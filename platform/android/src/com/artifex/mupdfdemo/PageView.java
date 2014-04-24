package com.artifex.mupdfdemo;

import java.util.ArrayList;
import java.util.Iterator;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.preference.PreferenceManager;

class PatchInfo {
    public Point patchViewSize;
    public Rect  patchArea;
    public boolean completeRedraw;

    public PatchInfo(Point aPatchViewSize, Rect aPatchArea, boolean aCompleteRedraw) {
        patchViewSize = aPatchViewSize;
        patchArea = aPatchArea;
        completeRedraw = aCompleteRedraw;
    }
}

// Make our ImageViews opaque to optimize redraw
class OpaqueImageView extends ImageView {

    public OpaqueImageView(Context context) {
        super(context);
    }

    @Override
    public boolean isOpaque() {
        return true;
    }
}

interface TextProcessor {
    void onStartLine();
    void onWord(TextWord word);
    void onEndLine();
    void onEndText();
}

class TextSelector {
    final private TextWord[][] mText;
    final private RectF mSelectBox;

    private float mStartLimit = Float.NEGATIVE_INFINITY;
    private float mEndLimit = Float.POSITIVE_INFINITY;
    
    public TextSelector(TextWord[][] text, RectF selectBox) {
        mText = text;
        mSelectBox = selectBox;
    }


    public TextSelector(TextWord[][] text, RectF selectBox, float startLimit, float endLimit) {
        mStartLimit = startLimit;
        mEndLimit = endLimit;
        mText = text;
        mSelectBox = selectBox;
    }
    
    public void select(TextProcessor tp) {
        if (mText == null || mSelectBox == null)
            return;

        ArrayList<TextWord[]> lines = new ArrayList<TextWord[]>();
        for (TextWord[] line : mText)
            if (line[0].bottom > mSelectBox.top && line[0].top < mSelectBox.bottom)
                lines.add(line);

        Iterator<TextWord[]> it = lines.iterator();
        while (it.hasNext()) {
            TextWord[] line = it.next();
            boolean firstLine = line[0].top < mSelectBox.top;
            boolean lastLine = line[0].bottom > mSelectBox.bottom;
                        
            float start = mStartLimit;
            float end = mEndLimit;
                        
            if (firstLine && lastLine) {
                start = Math.min(mSelectBox.left, mSelectBox.right);
                end = Math.max(mSelectBox.left, mSelectBox.right);
            } else if (firstLine) {
                start = mSelectBox.left;
            } else if (lastLine) {
                end = mSelectBox.right;
            }

            tp.onStartLine();

            for (TextWord word : line)
                if (word.right > start && word.left < end)
                    tp.onWord(word);

            tp.onEndLine();
        }
        tp.onEndText();
    }
}

public abstract class PageView extends ViewGroup implements MuPDFView {
    public static final int HIGHLIGHT_COLOR = 0x8033B5E5;
    public static final int GRAYEDOUT_COLOR = 0x30000000;
    public static final int SEARCHRESULTS_COLOR = 0x3033B5E5;
    public static final int HIGHLIGHTED_SEARCHRESULT_COLOR = 0xFF33B5E5;
    public static final int LINK_COLOR = 0xFF33B5E5;
    public static final int BOX_COLOR = 0xFF33B5E5;
    private static final int BACKGROUND_COLOR = 0xFFFFFFFF;
    private static final int PROGRESS_DIALOG_DELAY = 200;
    protected final Context   mContext;
    protected     int       mPageNumber;
    private       Point     mParentSize;
    protected     Point     mSize;   // Size of page at minimum zoom
    protected     float     mSourceScale;

    private       ImageView mEntire; // Image rendered at minimum zoom
    private       Bitmap    mEntireBm;
    private       Matrix    mEntireMat;
    private       AsyncTask<Void,Void,TextWord[][]> mGetText;
    private       AsyncTask<Void,Void,LinkInfo[]> mGetLinkInfo;
    private       AsyncTask<Void,Void,Void> mDrawEntire;

    private       Point     mPatchViewSize; // View size on the basis of which the patch was created
    private       Rect      mPatchArea;
    private       ImageView mPatch;
    private       Bitmap    mPatchBm;
    private       AsyncTask<PatchInfo,Void,PatchInfo> mDrawPatch;
    private SearchTaskResult mSearchTaskResult = null;
    protected     LinkInfo  mLinks[];
    private       RectF     mSelectBox;
    private       TextWord  mText[][];
    private       RectF     mItemSelectBox;
    protected     ArrayList<ArrayList<PointF>> mDrawing;
    private       View      mOverlayView;
    private       boolean   mIsBlank;
    private       boolean   mHighlightLinks;

    private       ProgressBar mBusyIndicator;
    private final Handler   mHandler = new Handler();

    private PointF eraser = null;
        //Set in onSharedPreferenceChanged()
    private static float inkThickness = 10;
    private static float eraserThickness = 20;
    private static int inkColor = 0x80AC7225;
    private static int eraserInnerColor = 0xFFFFFFFF;
    private static int eraserOuterColor = 0xFF000000;
    private static int highlightColor = 0x80AC7225;
    private static int underlineColor = 0x80AC7225;
    private static int strikeoutColor = 0x80AC7225;
    private static boolean useSmartTextSelection = false;
    
    private float docRelXmax = Float.NEGATIVE_INFINITY;
    private float docRelXmin = Float.POSITIVE_INFINITY;
    
    public PageView(Context c, Point parentSize, Bitmap sharedHqBm) {
        super(c);
        mContext    = c;
                
        mParentSize = parentSize;
        setBackgroundColor(BACKGROUND_COLOR);
        mEntireBm = Bitmap.createBitmap(parentSize.x, parentSize.y, Config.ARGB_8888);
        mPatchBm = sharedHqBm;
        mEntireMat = new Matrix();
    }

    protected abstract void drawPage(Bitmap bm, int sizeX, int sizeY, int patchX, int patchY, int patchWidth, int patchHeight);
    protected abstract void updatePage(Bitmap bm, int sizeX, int sizeY, int patchX, int patchY, int patchWidth, int patchHeight);
    protected abstract LinkInfo[] getLinkInfo();
    protected abstract TextWord[][] getText();
    protected abstract void addMarkup(PointF[] quadPoints, Annotation.Type type);

    private void reinit() {
            // Cancel pending render task
        if (mDrawEntire != null) {
            mDrawEntire.cancel(true);
            mDrawEntire = null;
        }

        if (mDrawPatch != null) {
            mDrawPatch.cancel(true);
            mDrawPatch = null;
        }

        if (mGetLinkInfo != null) {
            mGetLinkInfo.cancel(true);
            mGetLinkInfo = null;
        }

        if (mGetText != null) {
            mGetText.cancel(true);
            mGetText = null;
        }

        mIsBlank = true;
        mPageNumber = 0;

        if (mSize == null)
            mSize = mParentSize;

        if (mEntire != null) {
            mEntire.setImageBitmap(null);
            mEntire.invalidate();
        }

        if (mPatch != null) {
            mPatch.setImageBitmap(null);
            mPatch.invalidate();
        }

        mPatchViewSize = null;
        mPatchArea = null;

        mSearchTaskResult = null;
        mLinks = null;
        mSelectBox = null;
        mText = null;
        mItemSelectBox = null;
    }

    public void releaseResources() {
        reinit();

        if (mBusyIndicator != null) {
            removeView(mBusyIndicator);
            mBusyIndicator = null;
        }
    }

    public void releaseBitmaps() {
        reinit();
        mEntireBm = null;
        mPatchBm = null;
    }

    public void blank(int page) {
        reinit();
        mPageNumber = page;

        if (mBusyIndicator == null) {
            mBusyIndicator = new ProgressBar(mContext);
            mBusyIndicator.setIndeterminate(true);
//			mBusyIndicator.setBackgroundResource(R.drawable.busy);
            addView(mBusyIndicator);
        }

        setBackgroundColor(BACKGROUND_COLOR);
    }

    public void setPage(int page, PointF size) {
            // Cancel pending render task
        if (mDrawEntire != null) {
            mDrawEntire.cancel(true);
            mDrawEntire = null;
        }
        mIsBlank = false;
            // Highlights may be missing because mIsBlank was true on last draw
        if (mOverlayView != null)
            mOverlayView.invalidate();

        mPageNumber = page;
        if (mEntire == null) {
            mEntire = new OpaqueImageView(mContext);
            mEntire.setScaleType(ImageView.ScaleType.MATRIX);
            addView(mEntire);
        }

            // Calculate scaled size that fits within the screen limits
            // This is the size at minimum zoom
        mSourceScale = Math.min(mParentSize.x/size.x, mParentSize.y/size.y);
                
        Point newSize = new Point((int)(size.x*mSourceScale), (int)(size.y*mSourceScale));
        mSize = newSize;

        mEntire.setImageBitmap(null);
        mEntire.invalidate();

            // Get the link info in the background
        mGetLinkInfo = new AsyncTask<Void,Void,LinkInfo[]>() {
            protected LinkInfo[] doInBackground(Void... v) {
                return getLinkInfo();
            }

            protected void onPostExecute(LinkInfo[] v) {
                mLinks = v;
                if (mOverlayView != null)
                    mOverlayView.invalidate();
            }
        };

        mGetLinkInfo.execute();

            // Render the page in the background
        mDrawEntire = new AsyncTask<Void,Void,Void>() {
            protected Void doInBackground(Void... v) {
                drawPage(mEntireBm, mSize.x, mSize.y, 0, 0, mSize.x, mSize.y);
                return null;
            }

            protected void onPreExecute() {
                setBackgroundColor(BACKGROUND_COLOR);
                mEntire.setImageBitmap(null);
                mEntire.invalidate();

                if (mBusyIndicator == null) {
                    mBusyIndicator = new ProgressBar(mContext);
                    mBusyIndicator.setIndeterminate(true);
//					mBusyIndicator.setBackgroundResource(R.drawable.busy);
                    addView(mBusyIndicator);
                    mBusyIndicator.setVisibility(INVISIBLE);
                    mHandler.postDelayed(new Runnable() {
                            public void run() {
                                if (mBusyIndicator != null)
                                    mBusyIndicator.setVisibility(VISIBLE);
                            }
                        }, PROGRESS_DIALOG_DELAY);
                }
            }

            protected void onPostExecute(Void v) {
                removeView(mBusyIndicator);
                mBusyIndicator = null;
                mEntire.setImageBitmap(mEntireBm);
                mEntire.invalidate();
                setBackgroundColor(Color.TRANSPARENT);
            }
        };

        mDrawEntire.execute();

        if (mOverlayView == null) {
            mOverlayView = new View(mContext) {
                    @Override
                    //Todo: Optimize this for speed!!!
                    protected void onDraw(final Canvas canvas) {
                        super.onDraw(canvas);
                            // Work out current total scale factor
                            // from source to view
                        final float scale = mSourceScale*(float)getWidth()/(float)mSize.x;
                        final Paint paint = new Paint();//Should not be done in onDraw()!!!

                        if (!mIsBlank && mSearchTaskResult != null) {
                            paint.setColor(SEARCHRESULTS_COLOR);
                            for (RectF rect : mSearchTaskResult.getSearchBoxes())
                                canvas.drawRect(rect.left*scale, rect.top*scale,
                                                rect.right*scale, rect.bottom*scale,
                                                paint);
                            RectF rect = mSearchTaskResult.getFocusedSearchBox();
                            if(rect != null)
                            {
                                paint.setColor(HIGHLIGHTED_SEARCHRESULT_COLOR);
                                paint.setStyle(Paint.Style.STROKE);
                                paint.setAntiAlias(true);
                                paint.setStrokeWidth(2 * scale);
                                canvas.drawRect(rect.left*scale, rect.top*scale,
                                                rect.right*scale, rect.bottom*scale,
                                                paint);
                                paint.setAntiAlias(false);
                            }
                        }

                        if (!mIsBlank && mLinks != null && mHighlightLinks) {
                            paint.setStyle(Paint.Style.STROKE);
                            paint.setColor(LINK_COLOR);
                            paint.setStrokeWidth(0);
                            for (LinkInfo link : mLinks)
                                canvas.drawRect(link.rect.left*scale, link.rect.top*scale,
                                                link.rect.right*scale, link.rect.bottom*scale,
                                                paint);
                        }

                        if (mSelectBox != null && mText != null) {
                            paint.setStyle(Paint.Style.FILL);
                            paint.setColor(HIGHLIGHT_COLOR);
                            paint.setStrokeWidth(0);
                            processSelectedText(new TextProcessor() {
                                    RectF rect;
                                    float docRelXmaxSelection = Float.NEGATIVE_INFINITY;
                                    float docRelXminSelection = Float.POSITIVE_INFINITY;
                                                    
                                    public void onStartLine() {
                                        rect = new RectF();
                                    }

                                    public void onWord(TextWord word) {
                                        rect.union(word);
                                    }

                                    public void onEndLine() {
                                        if (!rect.isEmpty())
                                        {
                                            canvas.drawRect(rect.left*scale, rect.top*scale, rect.right*scale, rect.bottom*scale, paint);
                                            docRelXmaxSelection = Math.max(docRelXmaxSelection,Math.max(rect.right,docRelXmax));
                                            docRelXminSelection = Math.min(docRelXminSelection,Math.min(rect.left,docRelXmin));
                                        }
                                    }

                                    public void onEndText() {
                                        if (useSmartTextSelection)
                                        {
                                            paint.setColor(GRAYEDOUT_COLOR);
                                            paint.setStyle(Paint.Style.FILL);
                                            canvas.drawRect(0, 0, docRelXminSelection*scale, getHeight(), paint);
                                            canvas.drawRect(docRelXmaxSelection*scale, 0, getWidth(), getHeight(), paint);
                                        }
                                    }
                                });
                        }

                        if (mItemSelectBox != null) {
                            paint.setStyle(Paint.Style.STROKE);
                            paint.setColor(BOX_COLOR);
                            paint.setStrokeWidth(0);
                            canvas.drawRect(mItemSelectBox.left*scale, mItemSelectBox.top*scale, mItemSelectBox.right*scale, mItemSelectBox.bottom*scale, paint);
                        }

                        if (mDrawing != null) {
                            Path path = new Path();
                            PointF p;
                            Iterator<ArrayList<PointF>> it = mDrawing.iterator();
                            while (it.hasNext()) {
                                ArrayList<PointF> arc = it.next();
                                if (arc.size() >= 2) {
                                    Iterator<PointF> iit = arc.iterator();
                                    if(iit.hasNext())
                                    {
                                        p = iit.next();
                                        float mX = p.x * scale;
                                        float mY = p.y * scale;
                                        path.moveTo(mX, mY);
                                        while (iit.hasNext()) {
                                            p = iit.next();
                                            float x = p.x * scale;
                                            float y = p.y * scale;
                                            path.quadTo(mX, mY, (x + mX) / 2, (y + mY) / 2);
                                            mX = x;
                                            mY = y;
                                        }
                                    path.lineTo(mX, mY);
                                    }
                                }
                            }

                            paint.setAntiAlias(true);
                            paint.setDither(true);
                            paint.setStrokeJoin(Paint.Join.ROUND);
                            paint.setStrokeCap(Paint.Cap.ROUND);

                            paint.setStyle(Paint.Style.STROKE);
                            paint.setStrokeWidth(inkThickness * scale);
                            paint.setColor(inkColor);
                            
                            canvas.drawPath(path, paint);
                        }

                        if (eraser != null) {
                            paint.setAntiAlias(true);
                            paint.setDither(true);
                            paint.setStyle(Paint.Style.FILL);
                            paint.setColor(eraserInnerColor);
                            canvas.drawCircle(eraser.x * scale, eraser.y * scale, eraserThickness * scale, paint);
                            paint.setStyle(Paint.Style.STROKE);
                            paint.setColor(eraserOuterColor);
                            canvas.drawCircle(eraser.x * scale, eraser.y * scale, eraserThickness * scale, paint);
                        }
                    }
                };

            addView(mOverlayView);
        }
        requestLayout();
    }

	// public void setSearchBoxes(RectF searchBoxes[]) {
	// 	mSearchBoxes = searchBoxes;
	// 	if (mOverlayView != null)
	// 		mOverlayView.invalidate();
	// }

    public void setLinkHighlighting(boolean f) {
        mHighlightLinks = f;
        if (mOverlayView != null)
            mOverlayView.invalidate();
    }

    
    
    public void deselectText() {
        docRelXmax = Float.NEGATIVE_INFINITY;
        docRelXmin = Float.POSITIVE_INFINITY;
            
        mSelectBox = null;
        mOverlayView.invalidate();
    }

    
    public boolean hasSelection() {
        if (mSelectBox == null)
            return false;
        else
            return true;
    }

    public void selectText(float x0, float y0, float x1, float y1) {
        float scale = mSourceScale*(float)getWidth()/(float)mSize.x;
        float docRelX0 = (x0 - getLeft())/scale;
        float docRelY0 = (y0 - getTop())/scale;
        float docRelX1 = (x1 - getLeft())/scale;
        float docRelY1 = (y1 - getTop())/scale;

            //Adjust the min/max x values between which text is selected
        if(Math.max(docRelX0,docRelX1)>docRelXmax) docRelXmax = Math.max(docRelX0,docRelX1);
        if(Math.min(docRelX0,docRelX1)<docRelXmin) docRelXmin = Math.min(docRelX0,docRelX1);
                
            // Order on Y but maintain the point grouping
        if (docRelY0 <= docRelY1)
            mSelectBox = new RectF(docRelX0, docRelY0, docRelX1, docRelY1);
        else
            mSelectBox = new RectF(docRelX1, docRelY1, docRelX0, docRelY0);

        mOverlayView.invalidate();

        if (mGetText == null) {
            mGetText = new AsyncTask<Void,Void,TextWord[][]>() {
                @Override
                protected TextWord[][] doInBackground(Void... params) {
                    return getText();
                }
                @Override
                protected void onPostExecute(TextWord[][] result) {
                    mText = result;
                    mOverlayView.invalidate();
                }
            };

            mGetText.execute();
        }
    }

    public void startDraw(final float x, final float y) {        
        final float scale = mSourceScale*(float)getWidth()/(float)mSize.x;
        final float docRelX = (x - getLeft())/scale;
        final float docRelY = (y - getTop())/scale;
        if (mDrawing == null) mDrawing = new ArrayList<ArrayList<PointF>>();
        ArrayList<PointF> arc = new ArrayList<PointF>();
        arc.add(new PointF(docRelX, docRelY));
        mDrawing.add(arc);
    }

    public void continueDraw(final float x, final float y) {
        final float scale = mSourceScale*(float)getWidth()/(float)mSize.x;
        final float docRelX = (x - getLeft())/scale;
        final float docRelY = (y - getTop())/scale;
        if (mDrawing != null && mDrawing.size() > 0) {
            ArrayList<PointF> arc = mDrawing.get(mDrawing.size() - 1);
            arc.add(new PointF(docRelX, docRelY));
            mOverlayView.invalidate();
        }
            //Postprocess the current arc
        // if(arc.size() >= 4)
        // {
        //     inkThickness * scale
        // }
    }
    
    public void finishDraw() {
	if (mDrawing != null && mDrawing.size() > 0) {
            ArrayList<PointF> arc = mDrawing.get(mDrawing.size() - 1);
                //Make points look nice
            if(arc.size() == 1) {
                final float scale = mSourceScale*(float)getWidth()/(float)mSize.x;
                final PointF lastArc = arc.get(0);
                arc.add(new PointF(lastArc.x+0.5f*inkThickness,lastArc.y));
                arc.add(new PointF(lastArc.x+0.5f*inkThickness,lastArc.y+0.5f*inkThickness));
                arc.add(new PointF(lastArc.x,lastArc.y+0.5f*inkThickness));
                arc.add(lastArc);
                arc.add(new PointF(lastArc.x+0.5f*inkThickness,lastArc.y));
                mOverlayView.invalidate();
            }
        }
    }
    
    public void eraseAt(final float x, final float y) {
        final float scale = mSourceScale*(float)getWidth()/(float)mSize.x;
        final float docRelX = (x - getLeft())/scale;
        final float docRelY = (y - getTop())/scale;
        eraser = new PointF(docRelX,docRelY);
        ArrayList<ArrayList<PointF>> newArcs = new ArrayList<ArrayList<PointF>>();
        if (mDrawing != null && mDrawing.size() > 0) {
            for (ArrayList<PointF> arc : mDrawing)
            {
                    //Remove points from the beggining
                PointF lastPointRemovedFromBeginning = null;
                while(arc.size() > 0 && PointFMath.distance(arc.get(0),eraser) <= eraserThickness)
                    lastPointRemoved = arc.remove(0);

                    //Remove points from the end
                PointF lastPointRemovedFromEnd = null;
                while(arc.size() > 0 && PointFMath.distance(arc.get(arc.size()-1),eraser) <= eraserThickness)
                    lastPointRemovedFromEnd = arc.remove(arc.size()-1);
                
                    //Remove points from the middle of the arc splitting the arc up and saving them in newArcs
                Iterator<PointF> iter = arc.iterator();
                boolean lastPointWasUnderEraser = false;
                boolean pointsWereUnderEraser = false;
                while (iter.hasNext())
                {
                    PointF point = iter.next();
                    if(PointFMath.distance(point,eraser) <= eraserThickness)
                    {
                        iter.remove();
                        lastPointWasUnderEraser = true;
                        pointsWereUnderEraser = true;
                    }
                    else
                    {
                        if(lastPointWasUnderEraser)
                        {
                            newArcs.add(new ArrayList<PointF>());
                        }
                        if(pointsWereUnderEraser)
                        {
                            newArcs.get(newArcs.size()-1).add(point);
                            iter.remove();
                        }
                        lastPointWasUnderEraser = false;
                    }
                }
                    //Reinsert points into arc where it intersects the eraser (should be done eralier but these points must be ignored in the above collision detection...)
                if(lastPointRemovedFromBeginning != null)
                {
                    arc.add(0,PointFMath.pointOnLineCircleIntersection(arc.get(0), lastPointRemovedFromBeginning, eraser, eraserThickness));
                }
                if(lastPointRemovedFromEnd != null)
                {
                    arc.add(arc.size(),PointFMath.pointOnLineCircleIntersection(arc.get(arc.size()-1), lastPointRemovedFromEnd, eraser, eraserThickness));
                }
            }
            
            
                //Add all arcs in newArcs
            mDrawing.addAll(newArcs);
                //...and remove all arcs with less then two points...
            Iterator<ArrayList<PointF>> iter = mDrawing.iterator();
            while (iter.hasNext()) {
                if (iter.next().size() < 2) {
                    iter.remove();
                }
            }
        }
        mOverlayView.invalidate();
    }

    public void finishErase(final float x, final float y) {
        eraseAt(x,y);
        eraser = null;
    }

    public void undoDraw() {
        if (mDrawing == null || mDrawing.size() == 0) return;
        mDrawing.remove(mDrawing.size()-1);
        mOverlayView.invalidate();
    }
    
    public void cancelDraw() {
        mDrawing = null;
        mOverlayView.invalidate();
    }
    
    public int getDrawingSize() {
        return mDrawing.size();
    }
    
    protected PointF[][] getDraw() {
        if (mDrawing == null)
            return null;

        PointF[][] path = new PointF[mDrawing.size()][];

        for (int i = 0; i < mDrawing.size(); i++) {
            ArrayList<PointF> arc = mDrawing.get(i);
            path[i] = arc.toArray(new PointF[arc.size()]);
        }

        return path;
    }

    protected void processSelectedText(TextProcessor tp) {
        if (useSmartTextSelection)
            (new TextSelector(mText, mSelectBox,docRelXmin,docRelXmax)).select(tp);
        else
            (new TextSelector(mText, mSelectBox)).select(tp);
    }

    public void setItemSelectBox(RectF rect) {
        mItemSelectBox = rect;
        if (mOverlayView != null)
            mOverlayView.invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int x, y;
        switch(View.MeasureSpec.getMode(widthMeasureSpec)) {
            case View.MeasureSpec.UNSPECIFIED:
                x = mSize.x;
                break;
            default:
                x = View.MeasureSpec.getSize(widthMeasureSpec);
        }
        switch(View.MeasureSpec.getMode(heightMeasureSpec)) {
            case View.MeasureSpec.UNSPECIFIED:
                y = mSize.y;
                break;
            default:
                y = View.MeasureSpec.getSize(heightMeasureSpec);
        }

        setMeasuredDimension(x, y);

        if (mBusyIndicator != null) {
            int limit = Math.min(mParentSize.x, mParentSize.y)/2;
            mBusyIndicator.measure(View.MeasureSpec.AT_MOST | limit, View.MeasureSpec.AT_MOST | limit);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int w  = right-left;
        int h = bottom-top;

        if (mEntire != null) {
            mEntireMat.setScale(w/(float)mSize.x, h/(float)mSize.y);
            mEntire.setImageMatrix(mEntireMat);
            mEntire.invalidate();
            mEntire.layout(0, 0, w, h);
        }

        if (mOverlayView != null) {
            mOverlayView.layout(0, 0, w, h);
        }

        if (mPatchViewSize != null) {
            if (mPatchViewSize.x != w || mPatchViewSize.y != h) {
                    // Zoomed since patch was created
                mPatchViewSize = null;
                mPatchArea     = null;
                if (mPatch != null) {
                    mPatch.setImageBitmap(null);
                    mPatch.invalidate();
                }
            } else {
                mPatch.layout(mPatchArea.left, mPatchArea.top, mPatchArea.right, mPatchArea.bottom);
            }
        }

        if (mBusyIndicator != null) {
            int bw = mBusyIndicator.getMeasuredWidth();
            int bh = mBusyIndicator.getMeasuredHeight();

            mBusyIndicator.layout((w-bw)/2, (h-bh)/2, (w+bw)/2, (h+bh)/2);
        }
    }

    public void addHq(boolean update) {
        Rect viewArea = new Rect(getLeft(),getTop(),getRight(),getBottom());
            // If the viewArea's size matches the unzoomed size, there is no need for an hq patch
        if (viewArea.width() != mSize.x || viewArea.height() != mSize.y) {
            Point patchViewSize = new Point(viewArea.width(), viewArea.height());
            Rect patchArea = new Rect(0, 0, mParentSize.x, mParentSize.y);

                // Intersect and test that there is an intersection
            if (!patchArea.intersect(viewArea))
                return;

                // Offset patch area to be relative to the view top left
            patchArea.offset(-viewArea.left, -viewArea.top);

            boolean area_unchanged = patchArea.equals(mPatchArea) && patchViewSize.equals(mPatchViewSize);

                // If being asked for the same area as last time and not because of an update then nothing to do
            if (area_unchanged && !update)
                return;

            boolean completeRedraw = !(area_unchanged && update);

                // Stop the drawing of previous patch if still going
            if (mDrawPatch != null) {
                mDrawPatch.cancel(true);
                mDrawPatch = null;
            }

                // Create and add the image view if not already done
            if (mPatch == null) {
                mPatch = new OpaqueImageView(mContext);
                mPatch.setScaleType(ImageView.ScaleType.MATRIX);
                addView(mPatch);
                mOverlayView.bringToFront();
            }

            mDrawPatch = new AsyncTask<PatchInfo,Void,PatchInfo>() {
                protected PatchInfo doInBackground(PatchInfo... v) {
                    if (v[0].completeRedraw) {
                        drawPage(mPatchBm, v[0].patchViewSize.x, v[0].patchViewSize.y,
                                 v[0].patchArea.left, v[0].patchArea.top,
                                 v[0].patchArea.width(), v[0].patchArea.height());
                    } else {
                        updatePage(mPatchBm, v[0].patchViewSize.x, v[0].patchViewSize.y,
                                   v[0].patchArea.left, v[0].patchArea.top,
                                   v[0].patchArea.width(), v[0].patchArea.height());
                    }

                    return v[0];
                }

                protected void onPostExecute(PatchInfo v) {
                    mPatchViewSize = v.patchViewSize;
                    mPatchArea     = v.patchArea;
                    mPatch.setImageBitmap(mPatchBm);
                    mPatch.invalidate();
                        //requestLayout();
                        // Calling requestLayout here doesn't lead to a later call to layout. No idea
                        // why, but apparently others have run into the problem.
                    mPatch.layout(mPatchArea.left, mPatchArea.top, mPatchArea.right, mPatchArea.bottom);
                    invalidate();
                }
            };

            mDrawPatch.execute(new PatchInfo(patchViewSize, patchArea, completeRedraw));
        }
    }

    public void update() {
            // Cancel pending render task
        if (mDrawEntire != null) {
            mDrawEntire.cancel(true);
            mDrawEntire = null;
        }

        if (mDrawPatch != null) {
            mDrawPatch.cancel(true);
            mDrawPatch = null;
        }

            // Render the page in the background
        mDrawEntire = new AsyncTask<Void,Void,Void>() {
            protected Void doInBackground(Void... v) {
                updatePage(mEntireBm, mSize.x, mSize.y, 0, 0, mSize.x, mSize.y);
                return null;
            }

            protected void onPostExecute(Void v) {
                mEntire.setImageBitmap(mEntireBm);
                mEntire.invalidate();
            }
        };

        mDrawEntire.execute();

        addHq(true);
    }

    public void removeHq() {
            // Stop the drawing of the patch if still going
        if (mDrawPatch != null) {
            mDrawPatch.cancel(true);
            mDrawPatch = null;
        }

            // And get rid of it
        mPatchViewSize = null;
        mPatchArea = null;
        if (mPatch != null) {
            mPatch.setImageBitmap(null);
            mPatch.invalidate();
        }
    }

    public int getPage() {
        return mPageNumber;
    }

    @Override
    public boolean isOpaque() {
        return true;
    }

    public float getScale()
        {
            return mSourceScale*(float)getWidth()/(float)mSize.x;
        }


    public void setSearchTaskResult(SearchTaskResult searchTaskResult)
        {
            mSearchTaskResult = searchTaskResult;
        }

    public static void onSharedPreferenceChanged(SharedPreferences sharedPref, String key){
            //Set ink thickness and colors for PageView
        inkThickness = Float.parseFloat(sharedPref.getString(SettingsActivity.PREF_INK_THICKNESS, Float.toString(inkThickness)));
        eraserThickness = Float.parseFloat(sharedPref.getString(SettingsActivity.PREF_ERASER_THICKNESS, Float.toString(eraserThickness)));
        inkColor = ColorPalette.getHex(Integer.parseInt(sharedPref.getString(SettingsActivity.PREF_INK_COLOR, Integer.toString(inkColor))));
        highlightColor = ColorPalette.getHex(Integer.parseInt(sharedPref.getString(SettingsActivity.PREF_HIGHLIGHT_COLOR, Integer.toString(highlightColor))));
        underlineColor = ColorPalette.getHex(Integer.parseInt(sharedPref.getString(SettingsActivity.PREF_UNDERLINE_COLOR, Integer.toString(underlineColor))));
        strikeoutColor = ColorPalette.getHex(Integer.parseInt(sharedPref.getString(SettingsActivity.PREF_STRIKEOUT_COLOR, Integer.toString(strikeoutColor))));
            //Find out whether or not to use smart text selection
        useSmartTextSelection = sharedPref.getBoolean(SettingsActivity.PREF_SMART_TEXT_SELECTION, true);
    }
}
