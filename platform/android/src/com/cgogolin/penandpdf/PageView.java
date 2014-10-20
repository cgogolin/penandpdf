package com.cgogolin.penandpdf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.ArrayDeque;
import java.util.LinkedList;

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
import android.graphics.Region;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.view.ViewGroup;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.preference.PreferenceManager;

import android.util.Log;

interface TextProcessor {
    void onStartLine();
    void onWord(TextWord word);
    void onEndLine();
    void onEndText();
}


class TextSelector
{
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
            {
                if (word.right > start && word.left < end)
                    tp.onWord(word);
            }
            
            tp.onEndLine();
        }
        tp.onEndText();
    }
}

public abstract class PageView extends ViewGroup implements MuPDFView {
    private static final int SELECTION_COLOR = 0x8033B5E5;
    private static final int GRAYEDOUT_COLOR = 0x30000000;
    private static final int SEARCHRESULTS_COLOR = 0x3033B5E5;
    private static final int HIGHLIGHTED_SEARCHRESULT_COLOR = 0xFF33B5E5;
    private static final int LINK_COLOR = 0xFF33B5E5;
    private static final int BOX_COLOR = 0xFF33B5E5;
    private static final int BACKGROUND_COLOR = 0xFFFFFFFF;
    private static final int ERASER_INNER_COLOR = 0xFFFFFFFF;
    private static final int ERASER_OUTER_COLOR = 0xFF000000;
    private static final int PROGRESS_DIALOG_DELAY = 200;
    
    protected final Context mContext;
    private ViewGroup mParent;
    
    protected     int       mPageNumber;
    protected     Point     mSize;   // Size of page at minimum zoom
    protected     float     mSourceScale;
    private       float     docRelXmax = Float.NEGATIVE_INFINITY;
    private       float     docRelXmin = Float.POSITIVE_INFINITY;
    private       boolean   mIsBlank;
    private       boolean   mHighlightLinks;


//    private       ImageView mEntireView; // Image rendered at minimum zoom
    private       PatchView mEntireView; // Page rendered at minimum zoom
    private       Bitmap    mEntireBm;
    private       Matrix    mEntireMat;    
//    private       AsyncTask<Void,Void,Void> mDrawEntire;

    private       PatchView mHqView;
//    private       AsyncTask<PatchInfo,Void,PatchInfo> mDrawPatch;
    
    private       TextWord  mText[][];
    private       AsyncTask<Void,Void,TextWord[][]> mGetText;
    protected     LinkInfo  mLinks[];
    private       AsyncTask<Void,Void,LinkInfo[]> mGetLinkInfo;

    private       View      mOverlayView;
    private       SearchResult mSearchResult = null;
    private       RectF     mSelectBox;
    private       RectF     mItemSelectBox;
    protected     ArrayDeque<ArrayList<ArrayList<PointF>>> mDrawingHistory = new ArrayDeque<ArrayList<ArrayList<PointF>>>();
    protected     ArrayList<ArrayList<PointF>> mDrawing;
    private       PointF eraser = null;    

    private       ProgressBar mBusyIndicator;
    private final Handler   mHandler = new Handler();
    
        //Set in onSharedPreferenceChanged()
    private static float inkThickness = 10;
    private static float eraserThickness = 20;
    private static int inkColor = 0x80AC7225;
    private static int highlightColor = 0x80AC7225;
    private static int underlineColor = 0x80AC7225;
    private static int strikeoutColor = 0x80AC7225;
    private static boolean useSmartTextSelection = false;    
    
        //To be overrwritten in MuPDFPageView
    protected abstract void drawPage(Bitmap bm, int sizeX, int sizeY, int patchX, int patchY, int patchWidth, int patchHeight);
    protected abstract void updatePage(Bitmap bm, int sizeX, int sizeY, int patchX, int patchY, int patchWidth, int patchHeight);
    protected abstract LinkInfo[] getLinkInfo();
    protected abstract TextWord[][] getText();
    protected abstract void addMarkup(PointF[] quadPoints, Annotation.Type type);

        //The ViewGroup PageView has three child views: mHqView, mEntire, and mOverlayView.
        //mOverlayView is from an anonymous subclass defined in setPage(), the other two are 
    
    class PatchInfo {
        public final Rect  viewArea;
        public final Rect  patchArea;
        public final boolean completeRedraw;
        public final Bitmap patchBm;
        public final boolean intersects;
        public final boolean areaChanged;
    
        public PatchInfo(Rect viewArea, Bitmap patchBm, PatchView patch, boolean update) {
            this.viewArea = viewArea;
            Rect rect = new Rect(0, 0, patchBm.getWidth(), patchBm.getHeight());
            intersects = rect.intersect(viewArea);
            rect.offset(-viewArea.left, -viewArea.top);
            patchArea = rect;
            areaChanged = patch == null ? true : !viewArea.equals(patch.getArea());
            completeRedraw = areaChanged || !update;
            this.patchBm = patchBm;
        }
    }
    

    class PatchView extends OpaqueImageView {
        private Rect area;
        private AsyncTask<PatchInfo,Void,PatchInfo> mDrawPatch;
    
        public PatchView(Context context) {
            super(context);
            setScaleType(ImageView.ScaleType.MATRIX);
        }

        public void setArea(Rect area) {
            this.area = area;
        }
    
        public Rect getArea() {
            return area;
        }
    
        public void reset() {
            cancelRenderInBackground();
            setArea(null);
            setImageBitmap(null);
            invalidate();
        }

        public void renderInBackground(PatchInfo patchInfo) {
                // Stop the drawing of previous patch if still going
            if (mDrawPatch != null) {
                mDrawPatch.cancel(true);
                mDrawPatch = null;
            }

            mDrawPatch = new AsyncTask<PatchInfo,Void,PatchInfo>() {
                protected PatchInfo doInBackground(PatchInfo... v) {                
                    if (v[0].completeRedraw) {
                        drawPage(v[0].patchBm, v[0].viewArea.width(), v[0].viewArea.height(),
                                 v[0].patchArea.left, v[0].patchArea.top,
                                 v[0].patchArea.width(), v[0].patchArea.height());
                    } else {
                        updatePage(v[0].patchBm, v[0].viewArea.width(), v[0].viewArea.height(),
                                   v[0].patchArea.left, v[0].patchArea.top,
                                   v[0].patchArea.width(), v[0].patchArea.height());
                    }
                    return v[0];
                }
                protected void onPostExecute(PatchInfo v) {                
                    setArea(v.viewArea);
                    setImageBitmap(v.patchBm);
                    invalidate();
                    layout(v.patchArea.left, v.patchArea.top, v.patchArea.right, v.patchArea.bottom);
                }
            };
            mDrawPatch.execute(patchInfo);
        }

        public void cancelRenderInBackground() {
            if (mDrawPatch != null) {
                mDrawPatch.cancel(true);
                mDrawPatch = null;
            }
        }
    }


    // class EntireView extends OpaqueImageView {
    //     private AsyncTask<PatchInfo,Void,PatchInfo> mDrawEntire;
    //     private Bitmap mEntireBm;
        
    //     public EntireView(Context context) {
    //         super(context);
    //         setScaleType(ImageView.ScaleType.MATRIX);
    //         reset();
    //     }
    
    //     public void reset() {
    //         cancelRenderInBackground();
    //         setImageBitmap(null);
    //         invalidate();
    //     }

    //     public void renderInBackground(PatchInfo patchInfo) {
    //             // Stop the drawing of previous patch if still going
    //         if (mDrawEntire != null) {
    //             mDrawEntire.cancel(true);
    //             mDrawEntire = null;
    //         }

    //             //Create a bitmap of the right size (needs to be adopted!!!)
    //         if(mEntireBm == null || mParent.getWidth() != mEntireBm.getWidth() || mParent.getHeight() != mEntireBm.getHeight())
    //         {
    //             mEntireBm = Bitmap.createBitmap(mParent.getWidth(), mParent.getHeight(), Config.ARGB_8888);
    //         }
            
    //         mDrawEntire = new AsyncTask<PatchInfo,Void,PatchInfo>() {
    //             protected PatchInfo doInBackground(PatchInfo... v) {                
    //                 if (v[0].completeRedraw) {
    //                     drawPage(v[0].patchBm, v[0].viewArea.width(), v[0].viewArea.height(),
    //                              v[0].patchArea.left, v[0].patchArea.top,
    //                              v[0].patchArea.width(), v[0].patchArea.height());
    //                 } else {
    //                     updatePage(v[0].patchBm, v[0].viewArea.width(), v[0].viewArea.height(),
    //                                v[0].patchArea.left, v[0].patchArea.top,
    //                                v[0].patchArea.width(), v[0].patchArea.height());
    //                 }
    //                 return v[0];
    //             }
    //             protected void onPostExecute(PatchInfo v) {                
    //                 setImageBitmap(v.patchBm);
    //                 invalidate();
    //                 layout(v.patchArea.left, v.patchArea.top, v.patchArea.right, v.patchArea.bottom);
    //             }
    //         };
    //         mDrawEntire.execute(patchInfo);
    //     }

    //     public void cancelRenderInBackground() {
    //         if (mDrawEntire != null) {
    //             mDrawEntire.cancel(true);
    //             mDrawEntire = null;
    //         }
    //     }
    // }
    
    public PageView(Context c, ViewGroup parent) {
        super(c);
        mContext = c;
        mParent = parent;
        mEntireMat = new Matrix();
    }

    @Override
    public boolean isOpaque() {
        return true;
    }
    
    private void reset() {
            // Cancel pending background tasks
        // if (mDrawEntire != null) {
        //     mDrawEntire.cancel(true);
        //     mDrawEntire = null;
        // }
            // if (mDrawPatch != null) {
            //     mDrawPatch.cancel(true);
            //     mDrawPatch = null;
            // }
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
        mSize = null;

        //     //Reset the child views 
        // if (mEntireView != null) {
        //     mEntireView.setImageBitmap(null);
        //     mEntireView.invalidate();
        // }
        if(mEntireView != null) mEntireView.reset();
        if(mHqView != null) mHqView.reset();

        if(mOverlayView != null)
        {
            removeView(mOverlayView);
            mOverlayView = null;
        }     
                    
        mSearchResult = null;
        mLinks = null;
        mSelectBox = null;
        mText = null;
        mItemSelectBox = null;
    }

    public void releaseResources() {
        reset();
        if (mBusyIndicator != null) {
            removeView(mBusyIndicator);
            mBusyIndicator = null;
        }
        mDrawing = null;
        mDrawingHistory.clear();
        
            //Cancel all other async tasks
        if (mGetText != null) {
            mGetText.cancel(true);
            mGetText = null;
        }
        if (mGetLinkInfo != null) {
            mGetLinkInfo.cancel(true);
            mGetLinkInfo = null;
        }
    }

    public void releaseBitmaps() {
        reset();
        mEntireBm = null;
    }

    public void setPage(int page, PointF size) {
        reset();
        mPageNumber = page;
        mIsBlank = false;
        
            // Calculate scaled size that fits within the parent
            // This is the size at minimum zoom
        mSourceScale = Math.min(mParent.getWidth()/size.x, mParent.getHeight()/size.y);
        mSize = new Point((int)(size.x*mSourceScale), (int)(size.y*mSourceScale));
        
            // Get the link info in the background
        mGetLinkInfo = new AsyncTask<Void,Void,LinkInfo[]>() {
            protected LinkInfo[] doInBackground(Void... v) {
                return getLinkInfo();
            }

            protected void onPostExecute(LinkInfo[] v) {
                mLinks = v;
                if (mOverlayView != null) mOverlayView.invalidate();
            }
        };
        mGetLinkInfo.execute();

            //Set the background to white for now and
            //prepare and show the busy indicator
        setBackgroundColor(BACKGROUND_COLOR);
        if (mBusyIndicator == null) {
            mBusyIndicator = new ProgressBar(mContext);
            mBusyIndicator.setIndeterminate(true);
            addView(mBusyIndicator);
            mBusyIndicator.setVisibility(INVISIBLE);
            mHandler.postDelayed(new Runnable() {
                    public void run() {
                        if (mBusyIndicator != null)
                            mBusyIndicator.setVisibility(VISIBLE);
                    }
                }, PROGRESS_DIALOG_DELAY);
        }



            //The following has been moved to addEntire()
        
        //     //Prepare the mEntire view
        // if (mEntireView == null) {
        //     mEntireView = new OpaqueImageView(mContext);
        //     mEntireView.setScaleType(ImageView.ScaleType.MATRIX);
        //     addView(mEntireView);
        // }
        // mEntireView.setImageBitmap(null);
        // mEntireView.invalidate();
        
        //     //Create a bitmap of the right size
        // if(mEntireBm == null || mParent.getWidth() != mEntireBm.getWidth() || mParent.getHeight() != mEntireBm.getHeight())
        // {
        //     mEntireBm = Bitmap.createBitmap(mParent.getWidth(), mParent.getHeight(), Config.ARGB_8888);
        // }
        
        //     // Render the page in the background
        // mDrawEntire = new AsyncTask<Void,Void,Void>() {
        //     protected Void doInBackground(Void... v) {
        //         drawPage(mEntireBm, mSize.x, mSize.y, 0, 0, mSize.x, mSize.y);
        //         return null;
        //     }
            
        //     protected void onPostExecute(Void v) {
        //         if(mBusyIndicator!=null)
        //         {
        //             mBusyIndicator.setVisibility(INVISIBLE);
        //             removeView(mBusyIndicator);
        //             mBusyIndicator = null;
        //         }
        //         mEntireView.setImageBitmap(mEntireBm);
        //         mEntireView.invalidate();
        //         setBackgroundColor(Color.TRANSPARENT);
        //     }

        //     protected void onCanceled() {
        //         if(mBusyIndicator!=null)
        //         {
        //             mBusyIndicator.setVisibility(INVISIBLE);
        //             removeView(mBusyIndicator);
        //             mBusyIndicator = null;
        //         }
        //     }
        // };
        // mDrawEntire.execute();

        addEntire(false);
        
            //Create the Overlay view if not present
        if (mOverlayView == null) {
            mOverlayView = new View(mContext) {
                    
                    Path mDrawingPath = new Path();
                    
                    class TextSelectionDrawer implements TextProcessor
                    {
                        RectF rect;
                        float docRelXmaxSelection = Float.NEGATIVE_INFINITY;
                        float docRelXminSelection = Float.POSITIVE_INFINITY;
                        float scale;
                        Canvas canvas;

                        public void reset(Canvas canvas, float scale) {
                            this.canvas = canvas;
                            this.scale = scale;
                            docRelXmaxSelection = Float.NEGATIVE_INFINITY;
                            docRelXminSelection = Float.POSITIVE_INFINITY;
                        }
                                    
                        public void onStartLine() {
                            rect = new RectF();
                        }
                                    
                        public void onWord(TextWord word) {
                            rect.union(word);
                        }
                                    
                        public void onEndLine() {
                            if (!rect.isEmpty())
                            {
                                canvas.drawRect(rect.left*scale, rect.top*scale, rect.right*scale, rect.bottom*scale, selectBoxPaint);
                                docRelXmaxSelection = Math.max(docRelXmaxSelection,Math.max(rect.right,docRelXmax));
                                docRelXminSelection = Math.min(docRelXminSelection,Math.min(rect.left,docRelXmin));
                            }
                        }
                                    
                        public void onEndText() {
                            if (useSmartTextSelection)
                            {
                                canvas.drawRect(0, 0, docRelXminSelection*scale, PageView.this.getHeight(), selectOverlayPaint);
                                canvas.drawRect(docRelXmaxSelection*scale, 0, PageView.this.getWidth(), PageView.this.getHeight(), selectOverlayPaint);
                            }
                        }
                    }
                    private final Paint searchResultPaint = new Paint();
                    private final Paint highlightedSearchResultPaint = new Paint();
                    private final Paint linksPaint = new Paint();
                    private final Paint selectBoxPaint = new Paint();
                    private final Paint selectOverlayPaint = new Paint();
                    private final Paint itemSelectBoxPaint = new Paint();
                    private final Paint drawingPaint = new Paint();
                    private final Paint eraserInnerPaint = new Paint();
                    private final Paint eraserOuterPaint = new Paint();
                    private final TextSelectionDrawer textSelectionDrawer = new TextSelectionDrawer();
                        {
                            searchResultPaint.setColor(SEARCHRESULTS_COLOR);
                            
                            highlightedSearchResultPaint.setColor(HIGHLIGHTED_SEARCHRESULT_COLOR);
                            highlightedSearchResultPaint.setStyle(Paint.Style.STROKE);
                            highlightedSearchResultPaint.setAntiAlias(true);
                            
                            linksPaint.setColor(LINK_COLOR);
                            linksPaint.setStyle(Paint.Style.STROKE);
                            linksPaint.setStrokeWidth(0);
                            
                            selectBoxPaint.setColor(SELECTION_COLOR);
                            selectBoxPaint.setStyle(Paint.Style.FILL);
                            selectBoxPaint.setStrokeWidth(0);
                            
                            selectOverlayPaint.setColor(GRAYEDOUT_COLOR);
                            selectOverlayPaint.setStyle(Paint.Style.FILL);
                            
                            itemSelectBoxPaint.setColor(BOX_COLOR);
                            itemSelectBoxPaint.setStyle(Paint.Style.STROKE);
                            itemSelectBoxPaint.setStrokeWidth(0);
                            
                            drawingPaint.setAntiAlias(true);
                            drawingPaint.setDither(true);
                            drawingPaint.setStrokeJoin(Paint.Join.ROUND);
                            drawingPaint.setStrokeCap(Paint.Cap.ROUND);
                            drawingPaint.setStyle(Paint.Style.STROKE);
                            
                            eraserInnerPaint.setAntiAlias(true);
                            eraserInnerPaint.setDither(true);
                            eraserInnerPaint.setStyle(Paint.Style.FILL);
                            eraserInnerPaint.setColor(ERASER_INNER_COLOR);
                            
                            eraserOuterPaint.setAntiAlias(true);
                            eraserOuterPaint.setDither(true);
                            eraserOuterPaint.setStyle(Paint.Style.STROKE);
                            eraserOuterPaint.setColor(ERASER_OUTER_COLOR);
                        }

                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        int x, y;
                        switch(View.MeasureSpec.getMode(widthMeasureSpec)) {
                            case View.MeasureSpec.UNSPECIFIED:
                                x = PageView.this.getWidth();
                                break;
                            case View.MeasureSpec.AT_MOST:
                                x = Math.min(PageView.this.getWidth() ,View.MeasureSpec.getSize(widthMeasureSpec));
                            default:
                                x = View.MeasureSpec.getSize(widthMeasureSpec);
                        }
                        switch(View.MeasureSpec.getMode(heightMeasureSpec)) {
                            case View.MeasureSpec.UNSPECIFIED:
                                y = PageView.this.getHeight();
                                break;
                            case View.MeasureSpec.AT_MOST:
                                y = Math.min(PageView.this.getHeight(), View.MeasureSpec.getSize(heightMeasureSpec));
                            default:
                                y = View.MeasureSpec.getSize(heightMeasureSpec);
                        }
                        setMeasuredDimension(x, y);
                    }

                    
                    @Override
                    protected void onDraw(final Canvas canvas) {
                        super.onDraw(canvas);

//                        Log.v("PageView", "onDraw() of page "+mPageNumber+" of OverlayView"+this);
                        
                            //Clip to the canvas size (not sure if this is necessary)
                        canvas.clipRect(0,0, canvas.getWidth(), canvas.getHeight(), Region.Op.INTERSECT);
                            //Move the canvas so that it covers the visible region (not sure why the -2 is necessary)
                        canvas.translate(PageView.this.getLeft(), PageView.this.getTop());
                        
                            // Work out current total scale factor from source to view
                        final float scale = mSourceScale*(float)PageView.this.getWidth()/(float)mSize.x;
                        
                            // Highlight the search results
                        if (!mIsBlank && mSearchResult != null) {
                            for (RectF rect : mSearchResult.getSearchBoxes())
                            {
                                canvas.drawRect(rect.left*scale, rect.top*scale,
                                                rect.right*scale, rect.bottom*scale,
                                                searchResultPaint);
                            }
                            RectF rect = mSearchResult.getFocusedSearchBox();
                            if(rect != null)
                            {
                                highlightedSearchResultPaint.setStrokeWidth(2 * scale);
                                canvas.drawRect(rect.left*scale, rect.top*scale,
                                                rect.right*scale, rect.bottom*scale,
                                                highlightedSearchResultPaint);
                            }
                        }

                            // Draw the link boxes
                        if (!mIsBlank && mLinks != null && mHighlightLinks) {
                            for (LinkInfo link : mLinks)
                            {
                                canvas.drawRect(link.rect.left*scale, link.rect.top*scale,
                                                link.rect.right*scale, link.rect.bottom*scale,
                                                linksPaint);
                            }
                        }

                            // Draw the text selection
                        if (!mIsBlank && mSelectBox != null && mText != null) {
                            textSelectionDrawer.reset(canvas, scale);                                                        
                            processSelectedText(textSelectionDrawer);
                        }

                            // Draw the box arround selected notations and thelike
                        if (!mIsBlank && mItemSelectBox != null) {
                            canvas.drawRect(mItemSelectBox.left*scale, mItemSelectBox.top*scale, mItemSelectBox.right*scale, mItemSelectBox.bottom*scale, itemSelectBoxPaint);
                        }

                            // Draw the current hand drawing
                        if (!mIsBlank && mDrawing != null) {
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
                                        mDrawingPath.moveTo(mX, mY);
                                        while (iit.hasNext()) {
                                            p = iit.next();
                                            float x = p.x * scale;
                                            float y = p.y * scale;
                                            mDrawingPath.lineTo(x, y);
                                        }
                                    }
                                    if(!canvas.quickReject(mDrawingPath, Canvas.EdgeType.AA))
                                    {
                                        drawingPaint.setStrokeWidth(inkThickness * scale);
                                        drawingPaint.setColor(inkColor);  //Should be done only on settings change
                                        canvas.drawPath(mDrawingPath, drawingPaint);
                                    }
                                    mDrawingPath.reset();
                                }
                            }
                        }

                            // Draw the eraser
                        if (eraser != null) {
                            canvas.drawCircle(eraser.x * scale, eraser.y * scale, eraserThickness * scale, eraserInnerPaint);
                            canvas.drawCircle(eraser.x * scale, eraser.y * scale, eraserThickness * scale, eraserOuterPaint);
                        }
                    }
                };

                //Fit the overlay view to the PageView
            mOverlayView.measure(MeasureSpec.makeMeasureSpec(mParent.getWidth(), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(mParent.getHeight(), MeasureSpec.AT_MOST));
            addView(mOverlayView);
        }
        mOverlayView.invalidate();
        
        requestLayout();
    }

    
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
        savemDrawingToHistory();
            
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
        PointF point = new PointF(docRelX, docRelY);
        
        if (mDrawing != null && mDrawing.size() > 0) {
            ArrayList<PointF> arc = mDrawing.get(mDrawing.size() - 1);
            int lastElementIndex = arc.size()-1;
            // if(lastElementIndex >= 2 && PointFMath.pointToLineDistance(arc.get(lastElementIndex-2),point,arc.get(lastElementIndex-1)) < inkThickness && PointFMath.pointToLineDistance(arc.get(lastElementIndex-2),arc.get(lastElementIndex),arc.get(lastElementIndex-1)) < inkThickness) {
            //     arc.remove(lastElementIndex-1);
            // }
            // if(lastElementIndex >= 2 && PointFMath.distance(arc.get(lastElementIndex-1), point) < inkThickness)
            //     arc.remove(lastElementIndex);
            arc.add(point);
            mOverlayView.invalidate();
        }
    }
    
    public void finishDraw() {
	if (mDrawing != null && mDrawing.size() > 0) {
            ArrayList<PointF> arc = mDrawing.get(mDrawing.size() - 1);
                //Make points look nice
            if(arc.size() == 1) {
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

    public void startErase(final float x, final float y) {
        savemDrawingToHistory();
        
        continueErase(x,y);
    }
    
    public void continueErase(final float x, final float y) {
        final float scale = mSourceScale*(float)getWidth()/(float)mSize.x;
        final float docRelX = (x - getLeft())/scale;
        final float docRelY = (y - getTop())/scale;
        eraser = new PointF(docRelX,docRelY);
        ArrayList<ArrayList<PointF>> newArcs = new ArrayList<ArrayList<PointF>>();
        if (mDrawing != null && mDrawing.size() > 0) {
            for (ArrayList<PointF> arc : mDrawing)
            {
                Iterator<PointF> iter = arc.iterator();
                PointF pointToAddToArc = null;
                PointF lastPoint = null;
                boolean newArcHasBeenCreated = false;
                if(iter.hasNext()) lastPoint = iter.next();
                    //Remove the first point if under eraser
                if(PointFMath.distance(lastPoint,eraser) <= eraserThickness) iter.remove();
                while (iter.hasNext())
                {
                    PointF point = iter.next();
                    LineSegmentCircleIntersectionResult result = PointFMath.LineSegmentCircleIntersection(lastPoint , point, eraser, eraserThickness);
                        //Act according to how the segment overlaps with the eraser
                    if(result.intersects)
                    {
                            //...remove the point from the arc...
                        iter.remove();
                        
                            //If the segment enters...
                        if(result.enter != null)
                        {
                                //...and either add the entry point to the current new arc or save it for later to add it to the end of the current arc
                            if(newArcHasBeenCreated)
                                newArcs.get(newArcs.size()-1).add(newArcs.get(newArcs.size()-1).size(),result.enter);
                            else
                                pointToAddToArc = result.enter;
                        }
                        
                            //If the segment exits start a new arc with the exit point
                        if(result.exit != null) {
                            newArcHasBeenCreated = true;
                            newArcs.add(new ArrayList<PointF>());
                            newArcs.get(newArcs.size()-1).add(result.exit);
                            newArcs.get(newArcs.size()-1).add(point);
                        }
                    }
                    else if(result.inside)
                    {
                            //Remove the point from the arc
                        iter.remove();
                    }
                    else if(newArcHasBeenCreated)
                    {
                            //If we have already a new arc transfer the points
                        iter.remove();
                        newArcs.get(newArcs.size()-1).add(point);
                    }
                    lastPoint = point;
                }
                    //If arc still contains points add the first entry point at the end
                if(arc.size() > 0 && pointToAddToArc != null)
                {
                    arc.add(arc.size(),pointToAddToArc);
                }
            }
                //Finally add all arcs in newArcs
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
        continueErase(x,y);
        eraser = null;
    }

    public void undoDraw() {
        if(mDrawingHistory.size()>0) 
        {
            mDrawing = mDrawingHistory.pop();
            mOverlayView.invalidate();
        }
    }
    
    public boolean canUndo() {
        return mDrawingHistory.size()>0;
    }
    
    
    public void cancelDraw() {
        mDrawing = null;
        mDrawingHistory.clear();
        mOverlayView.invalidate();
    }
    
    public int getDrawingSize() {
        return mDrawing == null ? 0 : mDrawing.size();
    }

    public void setDraw(PointF[][] arcs) {
        if(arcs != null)
        {
            mDrawing = new ArrayList<ArrayList<PointF>>();
            for(int i = 0; i < arcs.length; i++)
            {
                mDrawing.add(new ArrayList<PointF>(Arrays.asList(arcs[i])));
            }
        }
        if (mOverlayView != null) mOverlayView.invalidate();
    }
    
    protected PointF[][] getDraw() {
        if (mDrawing == null) return null;

        PointF[][] arcs = new PointF[mDrawing.size()][];
        for (int i = 0; i < mDrawing.size(); i++) {
            ArrayList<PointF> arc = mDrawing.get(i);
            arcs[i] = arc.toArray(new PointF[arc.size()]);
        }
        return arcs;
    }

    protected void processSelectedText(TextProcessor tp) {
        if (useSmartTextSelection)
            (new TextSelector(mText, mSelectBox,docRelXmin,docRelXmax)).select(tp);
        else
            (new TextSelector(mText, mSelectBox)).select(tp);
    }

    public void setItemSelectBox(RectF rect) {
        mItemSelectBox = rect;
        if (mOverlayView != null) mOverlayView.invalidate();
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
            int limit = Math.min(mParent.getWidth(), mParent.getHeight())/2;
            mBusyIndicator.measure(View.MeasureSpec.AT_MOST | limit, View.MeasureSpec.AT_MOST | limit);
        }

        if (mOverlayView != null) {
            mOverlayView.measure(MeasureSpec.makeMeasureSpec(mParent.getWidth(), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(mParent.getHeight(), MeasureSpec.AT_MOST));
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int w  = right-left;
        int h = bottom-top;

            //Layout the Hq patch 
        if (mHqView != null && mHqView.getArea() != null) {
            if(mHqView.getArea().width() != w || mHqView.getArea().height() != h) {
                    // Remove Hq if zoomed since patch was created
                mHqView.reset();
            } else
            {
                mHqView.layout(mHqView.getLeft(), mHqView.getTop(), mHqView.getRight(), mHqView.getBottom());
            }
        }

            //Layout the entire page view
        if (mEntireView != null)
        {
                //Draw mEntireView only if it is not completely covered by a Hq patch
            if(mHqView != null && mHqView.getDrawable() != null && mHqView.getLeft() == left &&  mHqView.getTop() == top && mHqView.getRight() == right && mHqView.getBottom() == bottom ) 
            {
                mEntireView.setVisibility(View.GONE);
            }
            else
            {
                mEntireView.setVisibility(View.VISIBLE);
                mEntireMat.setScale(w/(float)mSize.x, h/(float)mSize.y);
                mEntireView.setImageMatrix(mEntireMat);
                mEntireView.invalidate();
                mEntireView.layout(0, 0, w, h);
            }
        }

            //Layout the overlay view
        if (mOverlayView != null) {
            mOverlayView.layout(-left, -top, -left+mOverlayView.getMeasuredWidth(), -top+mOverlayView.getMeasuredHeight());
            mOverlayView.invalidate();
        }

            //Layout the busy indicator
        if (mBusyIndicator != null) {
            int bw = mBusyIndicator.getMeasuredWidth();
            int bh = mBusyIndicator.getMeasuredHeight();
            mBusyIndicator.layout((w-bw)/2, (h-bh)/2, (w+bw)/2, (h+bh)/2);
        }
    }


    public void addEntire(boolean update) {
        Rect viewArea = new Rect(0,0,mParent.getWidth(),mParent.getHeight());

            //Create a bitmap of the right size (is this really correct?)
        if(mEntireBm == null || mParent.getWidth() != mEntireBm.getWidth() || mParent.getHeight() != mEntireBm.getHeight())
        {
            mEntireBm = Bitmap.createBitmap(mParent.getWidth(), mParent.getHeight(), Config.ARGB_8888);
        }
        
        //Construct the PatchInfo
        PatchInfo patchInfo = new PatchInfo(viewArea, mEntireBm, mEntire, update);

            //If there is no itersection there is no need to draw anything
        if(!patchInfo.intersects) return;

            // If being asked for the same area as last time and not because of an update then nothing to do
        if (!patchInfo.areaChanged && !update) return;

            // Create and add the patch view if not already done
        if (mEntire == null) {
            mEntire = new PatchView(mContext);
            addView(mEntire);
            if(mOverlayView != null) mOverlayView.bringToFront();
        }
        
        mEntire.renderInBackground(patchInfo);
    }
    
    
    public void addHq(boolean update) {
        Rect viewArea = new Rect(getLeft(),getTop(),getRight(),getBottom());
        
            // If the viewArea's size matches the unzoomed size, there is no need for a hq patch
        if (viewArea.width() == mSize.x && viewArea.height() == mSize.y) return;

            //Construct the PatchInfo (important: the bitmap is shared between all page views that belong to a given readerview, so we ask the ReadderView to provide it)
        PatchInfo patchInfo = new PatchInfo(viewArea, ((ReaderView)mParent).getPatchBm(), mHqView, update);

            //If there is no itersection there is no need to draw anything
        if(!patchInfo.intersects) return;
        
            // If being asked for the same area as last time and not because of an update then nothing to do
        if (!patchInfo.areaChanged && !update) return;
        
            // Create and add the patch view if not already done
        if (mHqView == null) {
            mHqView = new PatchView(mContext);
            addView(mHqView);
            if(mOverlayView != null) mOverlayView.bringToFront();
        }
        
        mHqView.renderInBackground(patchInfo);
    }

    public void update() {
        update(false);
    }    
    
    public void update(final boolean cancelDrawInOnPostExecute) {
            // Cancel pending render task
        if (mDrawEntire != null) {
            mDrawEntire.cancel(true);
            mDrawEntire = null;
        }

        // if (mDrawPatch != null) {
        //     mDrawPatch.cancel(true);
        //     mDrawPatch = null;
        // }
        if(mHqView != null) mHqView.cancelRenderInBackground();

        //     // Render the page in the background
        // mDrawEntire = new AsyncTask<Void,Void,Void>() {
        //     protected Void doInBackground(Void... v) {
        //         updatePage(mEntireBm, mSize.x, mSize.y, 0, 0, mSize.x, mSize.y);
        //         return null;
        //     }

        //     protected void onPostExecute(Void v) {
        //         if(mBusyIndicator!=null)
        //         {
        //             mBusyIndicator.setVisibility(INVISIBLE);
        //             removeView(mBusyIndicator);
        //             mBusyIndicator = null;
        //         }
        //         mEntireView.setImageBitmap(mEntireBm);
        //         mEntireView.invalidate();
        //         if(cancelDrawInOnPostExecute) cancelDraw();
        //     }

        //     protected void onCanceled() {
        //         if(mBusyIndicator!=null)
        //         {
        //             mBusyIndicator.setVisibility(INVISIBLE);
        //             removeView(mBusyIndicator);
        //             mBusyIndicator = null;
        //         }
        //         if(cancelDrawInOnPostExecute) cancelDraw();
        //     }
        // };
        // mDrawEntire.execute();

        addEntire(true);
        addHq(true);
    }

    public void removeHq() {
        if (mHqView != null) mHqView.reset();
    }

    public float getScale() {
        return mSourceScale*(float)getWidth()/(float)mSize.x;
    }

    public void setSearchResult(SearchResult searchTaskResult) {
        mSearchResult = searchTaskResult;
    }
    
    public static void onSharedPreferenceChanged(SharedPreferences sharedPref, String key) {
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

    private void savemDrawingToHistory() {
        if(mDrawing != null) {
            ArrayList<ArrayList<PointF>> mDrawingCopy = new ArrayList<ArrayList<PointF>>(mDrawing.size());
            for(int i = 0; i < mDrawing.size(); i++)
            {
                mDrawingCopy.add(new ArrayList<PointF>(mDrawing.get(i)));
            }
            mDrawingHistory.push(mDrawingCopy);
        }
        else {
            mDrawingHistory.push(new ArrayList<ArrayList<PointF>>(0));
        }
    }


    @Override
    public Parcelable onSaveInstanceState() {
        Bundle bundle = new Bundle();
        bundle.putParcelable("superInstanceState", super.onSaveInstanceState());
            //Save
        bundle.putSerializable("mDrawing", mDrawing);
        bundle.putSerializable("mDrawingHistory", mDrawingHistory);

        return bundle;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            Bundle bundle = (Bundle) state;
                //Load 
            mDrawing = (ArrayList<ArrayList<PointF>>)bundle.getSerializable("mDrawing");
            mDrawingHistory = (ArrayDeque<ArrayList<ArrayList<PointF>>>)bundle.getSerializable("mDrawingHistory");

            state = bundle.getParcelable("superInstanceState");
        }
        super.onRestoreInstanceState(state);
    }
}
