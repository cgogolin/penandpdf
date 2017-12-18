package com.cgogolin.penandpdf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.ArrayDeque;
import java.util.LinkedList;
import android.util.TypedValue;
import java.lang.Thread;

import java.io.Serializable;
import java.io.IOException;
import java.io.ObjectStreamException;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.view.MotionEvent;
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
    private static final int SELECTION_MARKER_COLOR = 0xFF33B5E5;
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
    protected ViewGroup mParent;
    
    protected     int       mPageNumber;
    protected     Point     mSize;   // Size of page at minimum zoom
    protected     float     mSourceScale;
    private       float     docRelXmax = Float.NEGATIVE_INFINITY;
    private       float     docRelXmin = Float.POSITIVE_INFINITY;
    private       boolean   mIsBlank;
    private       boolean   mHighlightLinks;

    private       Bitmap mTextAnnotationBitmap;
    
    private       PatchView mEntireView; // Page rendered at minimum zoom
    private       Bitmap    mEntireBm;
    private       Matrix    mEntireMat;    

    private       PatchView mHqView;
    
    private       TextWord  mText[][];
    private       AsyncTask<Void,Void,TextWord[][]> mLoadTextTask;
    protected     LinkInfo  mLinks[];
    private       AsyncTask<Void,Void,LinkInfo[]> mLoadLinkInfoTask;
    protected     Annotation mAnnotations[];
    private       AsyncTask<Void,Void,Annotation[]> mLoadAnnotationsTask;

    private       OverlayView mOverlayView;
    private       SearchResult mSearchResult = null;
    private       RectF     mSelectBox;
    private       RectF     mItemSelectBox;
    
    protected     ArrayDeque<ArrayList<ArrayList<PointF>>> mDrawingHistory = new ArrayDeque<ArrayList<ArrayList<PointF>>>();
    protected     ArrayList<ArrayList<PointF>> mDrawing;
    
    private       PointF eraser = null;    

    private       ProgressBar mBusyIndicator;
    private final Handler   mHandler = new Handler();
    
        //Just dummy values, reall values are set in onSharedPreferenceChanged()
    private static float inkThickness = 10f;
    private static float eraserThickness = 20f;
    private static int inkColor = 0x80AC7225;
    private static int highlightColor = 0x80AC7225;
    private static int underlineColor = 0x80AC7225;
    private static int strikeoutColor = 0x80AC7225;
    private static boolean useSmartTextSelection = false;    

    protected abstract CancellableTaskDefinition<PatchInfo,PatchInfo> getRenderTask(PatchInfo patchInfo);
    protected abstract LinkInfo[] getLinkInfo();
    protected abstract TextWord[][] getText();
    protected abstract Annotation[] getAnnotations();
    protected abstract void addMarkup(PointF[] quadPoints, Annotation.Type type);
    protected abstract void addTextAnnotation(Annotation annot);


        //The ViewGroup PageView has three main child views: mEntireView, mHqView, and mOverlayView, all of which are defined below
        //mEntireView is the view that holds a image of the whole document at a resolution corresponding to zoom=1.0
        //mHqView is the view that holds an pixel accurate image of the currently visible patch of the document
        //mOverlayView is used to draw things like the currently drawn line, selection boxes, frames around links, ...
    
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
    

    class PatchView extends ImageView {
        private Rect area;
        private Rect patchArea;
        private CancellableAsyncTask<PatchInfo,PatchInfo> mDrawPatch;
        private Bitmap bitmap;
        
        public PatchView(Context context) {
            super(context);
            setScaleType(ImageView.ScaleType.MATRIX);
        }

        @Override
        public boolean isOpaque() {
            return true;
        }
        
        public void setArea(Rect area) {
            this.area = area;
        }
    
        public Rect getArea() {
            return area;
        }
        
        public void setPatchArea(Rect patchArea) {
            this.patchArea = patchArea;
        }
    
        public Rect getPatchArea() {
            return patchArea;
        }
    
        public void reset() {
            cancelRenderInBackground();
            setArea(null);
            setPatchArea(null);
            setImageBitmap(null);
            setImageDrawable(null);
            invalidate();
        }
        
        @Override
        public void setImageBitmap(Bitmap bitmap) {
            this.bitmap = bitmap;
            super.setImageBitmap(bitmap);
        }

        public Bitmap getImageBitmap() {
            return bitmap;
        }
        
        public void renderInBackground(PatchInfo patchInfo) {
                //If we are already rendering / have rendered the area there is nothing to do
            if(getArea() == patchInfo.viewArea) return;
            
                // Stop the drawing of previous patch if still going
            cancelRenderInBackground();            
            
            setPatchArea(null);

            mDrawPatch = new CancellableAsyncTask<PatchInfo, PatchInfo>(getRenderTask(patchInfo)){
                    @Override
                    protected void onPostExecute(PatchInfo patchInfo) {
                        removeBusyIndicator();
                        setArea(patchInfo.viewArea);
                        setPatchArea(patchInfo.patchArea);
                        setImageBitmap(patchInfo.patchBm);
                        requestLayout();
                    }
                    @Override
                    protected void onCanceled() {
                        super.onCanceled();
                        removeBusyIndicator(); //Do we really want to do this here?
                    }
                };
            mDrawPatch.execute(patchInfo);
        }
        
        public void cancelRenderInBackground() {
            if (mDrawPatch != null) {
                mDrawPatch.cancel();
//              mDrawPatch.cancelAndWait();
                mDrawPatch = null;
            }
        }

        private void removeBusyIndicator() {
            if(mBusyIndicator!=null)
            {
                mBusyIndicator.setVisibility(INVISIBLE);
                removeView(mBusyIndicator);
                mBusyIndicator = null;
            }
        }
    }

        //Update in following TextSelectionDrawer (coordinates are relative to document)
    private RectF leftMarkerRect = new RectF();
    private RectF rightMarkerRect= new RectF();

    public boolean hitsLeftMarker(float x, float y)
        {
            float scale = mSourceScale*(float)getWidth()/(float)mSize.x;
            float docRelX = (x - getLeft())/scale;
            float docRelY = (y - getTop())/scale;            
            return leftMarkerRect != null && leftMarkerRect.contains(docRelX,docRelY); 
        }
    public boolean hitsRightMarker(float x, float y)
        {
            float scale = mSourceScale*(float)getWidth()/(float)mSize.x;
            float docRelX = (x - getLeft())/scale;
            float docRelY = (y - getTop())/scale;
            return rightMarkerRect != null && rightMarkerRect.contains(docRelX,docRelY); 
        }
    public void moveLeftMarker(MotionEvent e){
        float scale = mSourceScale*(float)getWidth()/(float)mSize.x;
        float docRelX = (e.getX() - getLeft())/scale;
        float docRelY = (e.getY() - getTop())/scale;

        mSelectBox.left=docRelX;
        if(docRelY < mSelectBox.bottom)
            mSelectBox.top=docRelY;
        else {
            mSelectBox.top=mSelectBox.bottom;
            mSelectBox.bottom=docRelY;
        }                
        if(docRelX>docRelXmax) docRelXmax = docRelX;
        if(docRelX<docRelXmin) docRelXmin = docRelX;
        mOverlayView.invalidate();
    }
    
    public void moveRightMarker(MotionEvent e){
        float scale = mSourceScale*(float)getWidth()/(float)mSize.x;
        float docRelX = (e.getX() - getLeft())/scale;
        float docRelY = (e.getY() - getTop())/scale;
        mSelectBox.right=docRelX;
        if(docRelY > mSelectBox.top)
            mSelectBox.bottom=docRelY;
        else {
            mSelectBox.bottom=mSelectBox.top;
            mSelectBox.top=docRelY;
        }
        if(docRelX>docRelXmax) docRelXmax = docRelX;
        if(docRelX<docRelXmin) docRelXmin = docRelX;
        mOverlayView.invalidate();
    }
    
    
    class OverlayView extends View {
        Path mDrawingPath = new Path();
        
        class TextSelectionDrawer implements TextProcessor
        {
            RectF rect;
            RectF firstLineRect = new RectF();
            RectF lastLineRect = new RectF();
            Path leftMarker = new Path();
            Path rightMarker = new Path();
            float height;
            float oldHeight = 0f;
            float docRelXmaxSelection = Float.NEGATIVE_INFINITY;
            float docRelXminSelection = Float.POSITIVE_INFINITY;
            float scale;
            Canvas canvas;

            public void reset(Canvas canvas, float scale) {
                this.canvas = canvas;
                this.scale = scale;
                firstLineRect = null;
                lastLineRect = null;
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
                    if(firstLineRect == null || firstLineRect.top > rect.top)
                    {
                        if(firstLineRect == null) firstLineRect = new RectF();
                        firstLineRect.set(rect);
                    }
                    if(lastLineRect == null || lastLineRect.bottom < rect.bottom)
                    {
                        if(lastLineRect == null) lastLineRect = new RectF();
                        lastLineRect.set(rect);
                    }
                    
                        
                    canvas.drawRect(rect.left*scale, rect.top*scale, rect.right*scale, rect.bottom*scale, selectBoxPaint);
                    
                    docRelXmaxSelection = Math.max(docRelXmaxSelection,Math.max(rect.right,docRelXmax));
                    docRelXminSelection = Math.min(docRelXminSelection,Math.min(rect.left,docRelXmin));
                }
            }
                                    
            public void onEndText() {
                if(firstLineRect != null && lastLineRect != null)
                {
                    height = Math.min(Math.max(Math.max(firstLineRect.bottom - firstLineRect.top, lastLineRect.bottom - lastLineRect.top), getResources().getDisplayMetrics().xdpi*0.07f/scale), 4*getResources().getDisplayMetrics().xdpi*0.07f/scale);
                    
                    leftMarkerRect.set(firstLineRect.left-0.9f*height,firstLineRect.top,firstLineRect.left,firstLineRect.top+1.9f*height);
                    rightMarkerRect.set(lastLineRect.right,lastLineRect.top,lastLineRect.right+0.9f*height,lastLineRect.top+1.9f*height);
                    
                    if(height != oldHeight || true)
                    {
                        leftMarker.rewind();
                        leftMarker.moveTo(0f,0f);
                        leftMarker.rLineTo(0f,1.9f*height*scale);
                        leftMarker.rLineTo(-0.9f*height*scale,0f);
                        leftMarker.rLineTo(0f,-0.9f*height*scale);
                        leftMarker.close();
                        
                        rightMarker.rewind();
                        rightMarker.moveTo(0f,0f);
                        rightMarker.rLineTo(0f,1.9f*height*scale);
                        rightMarker.rLineTo(0.9f*height*scale,0f);
                        rightMarker.rLineTo(0f,-0.9f*height*scale);
                        rightMarker.close();
                        oldHeight = height;
                    }
                    
                    leftMarker.offset(firstLineRect.left*scale, firstLineRect.top*scale);
                    rightMarker.offset(lastLineRect.right*scale, lastLineRect.top*scale);
                    canvas.drawPath(leftMarker, selectMarkerPaint);
                    canvas.drawPath(rightMarker, selectMarkerPaint);
                        //Undo the offset so that we can reuse the path
                    leftMarker.offset(-firstLineRect.left*scale, -firstLineRect.top*scale);
                    rightMarker.offset(-lastLineRect.right*scale, -lastLineRect.top*scale);                        
                }
                
                if(useSmartTextSelection)
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
        private final Paint selectMarkerPaint = new Paint();
        private final Paint selectOverlayPaint = new Paint();
        private final Paint itemSelectBoxPaint = new Paint();
        private final Paint drawingPaint = new Paint();
        private final Paint eraserInnerPaint = new Paint();
        private final Paint eraserOuterPaint = new Paint();
        private final TextSelectionDrawer textSelectionDrawer = new TextSelectionDrawer();
        
        public OverlayView(Context context) {
            super(context);
            
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

            selectMarkerPaint.setColor(SELECTION_MARKER_COLOR);
            selectMarkerPaint.setStyle(Paint.Style.FILL);
            selectMarkerPaint.setStrokeWidth(0);
            
            selectOverlayPaint.setColor(GRAYEDOUT_COLOR);
            selectOverlayPaint.setStyle(Paint.Style.FILL);
                            
            itemSelectBoxPaint.setColor(BOX_COLOR);
            itemSelectBoxPaint.setStyle(Paint.Style.STROKE);
            itemSelectBoxPaint.setStrokeWidth(3);
                            
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

            //Used in onDraw but defined here for perfomance
        private PointF mP;
        private Iterator<ArrayList<PointF>> it;
        private ArrayList<PointF> arc;
        private Iterator<PointF> iit;
        private float mX1, mY1, mX2, mY2;
        
        @Override
        protected void onDraw(final Canvas canvas) {
            super.onDraw(canvas);

                //As we use HW acceleration canvas.getClipBounds()) alwas returns the full view.
                //To further speed things up we could also use a Picture...
            
                //Move the canvas so that it covers the visible region
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
            if(!mIsBlank)
                drawDrawing(canvas, scale);
            
                // Draw the eraser
            if (!mIsBlank && eraser != null) {
                canvas.drawCircle(eraser.x * scale, eraser.y * scale, eraserThickness * scale, eraserInnerPaint);
                canvas.drawCircle(eraser.x * scale, eraser.y * scale, eraserThickness * scale, eraserOuterPaint);
            }
        }

        private void drawDrawing(Canvas canvas, float scale)
            {
                if (mDrawing != null) {
                        // PointF mP; //These are defined as member variables for performance 
                        // Iterator<ArrayList<PointF>> it;
                        // ArrayList<PointF> arc;
                        // Iterator<PointF> iit;
                        // float mX1, mY1, mX2, mY2;
                    drawingPaint.setStrokeWidth(inkThickness * scale);
                    drawingPaint.setColor(inkColor);  //Should be done only on settings change
                    it = mDrawing.iterator();
                    while (it.hasNext()) {
                        arc = it.next();
                        if (arc.size() >= 2) {
                            iit = arc.iterator();
                            if(iit.hasNext())
                            {
                                mP = iit.next();
                                mX1 = mP.x * scale;
                                mY1 = mP.y * scale;
                                mDrawingPath.moveTo(mX1, mY1);
                                while (iit.hasNext()) {
                                    mP = iit.next();
                                    mX2 = mP.x * scale;
                                    mY2 = mP.y * scale;
                                    mDrawingPath.lineTo(mX2, mY2);
                                }
                            }
                            if (android.os.Build.VERSION.SDK_INT >= 11 && canvas.isHardwareAccelerated()
                                && android.os.Build.VERSION.SDK_INT < 16) {
                                canvas.drawPath(mDrawingPath, drawingPaint);
                            } else if (!canvas.quickReject(mDrawingPath, Canvas.EdgeType.AA)) {
                                canvas.drawPath(mDrawingPath, drawingPaint);
                            }
                            mDrawingPath.reset();
                        }
                    }
                }
            }
    }

    
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
        if (mLoadAnnotationsTask != null) {
            mLoadAnnotationsTask.cancel(true);
            mLoadAnnotationsTask = null;
        }
        if (mLoadLinkInfoTask != null) {
            mLoadLinkInfoTask.cancel(true);
            mLoadLinkInfoTask = null;
        }
        if (mLoadTextTask != null) {
            mLoadTextTask.cancel(true);
            mLoadTextTask = null;
        }

            //Reset the child views
        if(mEntireView != null) mEntireView.reset();
        if(mHqView != null) mHqView.reset();
        if(mOverlayView != null)
        {
            removeView(mOverlayView);
            mOverlayView = null;
        }
        
        mIsBlank = true;
        mPageNumber = 0;        
        mSize = null;
                    
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
    }

    public void releaseBitmaps() {
        reset();
        mTextAnnotationBitmap = null;
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

            //Create the mEntireView
        addEntire(false);

            // Get the link info and text in the background
        loadLinkInfo();
        loadText();
        
            //Create the mOverlayView if not present
        if (mOverlayView == null) {
            mOverlayView = new OverlayView(mContext);

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

        //This is incredibly wastefull. Should stop going through the rest of the text as soon as it has found some word
    public boolean hasTextSelected() {

        class Boolean {
            public boolean value;
        }
        
        final Boolean b = new Boolean();
        b.value = false;
        
        processSelectedText(new TextProcessor() {
                public void onStartLine() {}
                public void onWord(TextWord word) {
                    b.value = true;
                }
                public void onEndLine() {}
                public void onEndText() {}
            });
        return b.value;
    }
    
    public void selectText(float x0, float y0, float x1, float y1) {
        float scale = mSourceScale*(float)getWidth()/(float)mSize.x;
        float docRelX0 = (x0 - getLeft())/scale;
        float docRelY0 = (y0 - getTop())/scale;
        float docRelX1 = (x1 - getLeft())/scale;
        float docRelY1 = (y1 - getTop())/scale;
        
            // Order on Y but maintain the point grouping
        if (docRelY0 <= docRelY1)
            mSelectBox = new RectF(docRelX0, docRelY0, docRelX1, docRelY1);
        else
            mSelectBox = new RectF(docRelX1, docRelY1, docRelX0, docRelY0);

            //Adjust the min/max x values between which text is selected
        if(Math.max(docRelX0,docRelX1)>docRelXmax) docRelXmax = Math.max(docRelX0,docRelX1);
        if(Math.min(docRelX0,docRelX1)<docRelXmin) docRelXmin = Math.min(docRelX0,docRelX1);
        
        mOverlayView.invalidate();

        loadText(); //We should do this earlier in the background ...
    }


        //The following three helper methods use the methods getText(), getLinkInfo(), and getAnnotations()
        //that are to be provided by any super class to asynchronously load the Text, LinkInfo, and Annotations
        //respectively
        //TODO: fix how tasks are cleared up once done and make sure they are not unnecessarially recreated
    private void loadText() { 
        if (mLoadTextTask == null) {
            mLoadTextTask = new AsyncTask<Void,Void,TextWord[][]>() {
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
            mLoadTextTask.execute();
        }
    }

    private void loadLinkInfo() {
        mLoadLinkInfoTask = new AsyncTask<Void,Void,LinkInfo[]>() {
            protected LinkInfo[] doInBackground(Void... v) {
                return getLinkInfo();
            }

            protected void onPostExecute(LinkInfo[] v) {
                mLinks = v;
                if (mOverlayView != null) mOverlayView.invalidate();
            }
        };
        mLoadLinkInfoTask.execute();
    }

    protected void loadAnnotations() {
        mAnnotations = null;
        if (mLoadAnnotationsTask != null) mLoadAnnotationsTask.cancel(true);
        mLoadAnnotationsTask = new AsyncTask<Void,Void,Annotation[]> () {
            @Override
            protected Annotation[] doInBackground(Void... params) {
                return getAnnotations();
            }
            
            @Override
            protected void onPostExecute(Annotation[] result) {
                mAnnotations = result;
                redraw(true);
            }
        };
        mLoadAnnotationsTask.execute();
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
            arc.add(point);
            
            PointF point0 = arc.get(arc.size()-2);
            Rect invalidRect = new Rect();
            invalidRect.union((int)(point.x*scale+getLeft()), (int)(point.y*scale+getTop()));
            invalidRect.union((int)(arc.get(arc.size()-2).x*scale+getLeft()), (int)(arc.get(arc.size()-2).y*scale+getTop()));
            int inkWidth = (int)(inkThickness*scale)+1;
            mOverlayView.invalidate(invalidRect.left-inkWidth, invalidRect.top-inkWidth, invalidRect.right+inkWidth, invalidRect.bottom+inkWidth);
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
            }
            mOverlayView.invalidate();
        }
    }

    public void startErase(final float x, final float y) {
        savemDrawingToHistory();
        final float scale = mSourceScale*(float)getWidth()/(float)mSize.x;
        final float docRelX = (x - getLeft())/scale;
        final float docRelY = (y - getTop())/scale;
        eraser = new PointF(docRelX,docRelY);
        continueErase(x,y);
    }
    
    public void continueErase(final float x, final float y) {
        if(eraser==null)
            return;//I don't understand under which conditions this is possible but very rarely this function gets called while eraser==null
        
        final float scale = mSourceScale*(float)getWidth()/(float)mSize.x;
        final float docRelX = (x - getLeft())/scale;
        final float docRelY = (y - getTop())/scale;

        eraser.set(docRelX,docRelY);
        
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
                if(lastPoint!=null && PointFMath.distance(lastPoint,eraser) <= eraserThickness)
                    iter.remove();
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
        if(mHqView != null)
        {
                // Remove Hq if zoomed since patch was created
            if(mHqView.getArea() == null || mHqView.getPatchArea() == null || mHqView.getArea().width() != w || mHqView.getArea().height() != h) {
                mHqView.setVisibility(View.GONE);
                mHqView.reset();
            }
            else if(mHqView.getPatchArea() != null && mHqView.getArea() != null && mHqView.getArea().width() == w && mHqView.getArea().height() == h) {
                mHqView.layout(mHqView.getPatchArea().left, mHqView.getPatchArea().top, mHqView.getPatchArea().right, mHqView.getPatchArea().bottom);
                mHqView.setVisibility(View.VISIBLE);
            }
        }

            //Layout the entire page view
        if (mEntireView != null) {
                //Draw mEntireView only if it is not completely covered by a Hq patch
            if(mHqView != null && mHqView.getDrawable() != null && mHqView.getLeft() == left &&  mHqView.getTop() == top && mHqView.getRight() == right && mHqView.getBottom() == bottom )
            {
                mEntireView.setVisibility(View.GONE);
            }
            else
            {
                mEntireMat.setScale(w/(float)mSize.x, h/(float)mSize.y);
                mEntireView.setImageMatrix(mEntireMat);
                mEntireView.layout(0, 0, w, h);
                mEntireView.setVisibility(View.VISIBLE);
            }
        }

            //Layout the overlay view
        if (mOverlayView != null) {
            mOverlayView.layout(-left, -top, -left+mOverlayView.getMeasuredWidth(), -top+mOverlayView.getMeasuredHeight());
            if(changed) mOverlayView.invalidate();
        }

            //Layout the busy indicator
        if (mBusyIndicator != null) {
            int bw = mBusyIndicator.getMeasuredWidth();
            int bh = mBusyIndicator.getMeasuredHeight();
            mBusyIndicator.layout((w-bw)/2, (h-bh)/2, (w+bw)/2, (h+bh)/2);
        }
    }


    public void addEntire(boolean update) {
        Rect viewArea = new Rect(0,0,mSize.x,mSize.y);

            //Create a bitmap of the right size (is this really correct?)
        if(mEntireBm == null || mSize.x != mEntireBm.getWidth() || mSize.y != mEntireBm.getHeight())
        {
            mEntireBm = Bitmap.createBitmap(mSize.x, mSize.y, Config.ARGB_8888);
        }
        
            //Construct the PatchInfo
        PatchInfo patchInfo = new PatchInfo(viewArea, mEntireBm, mEntireView, update);

            //If there is no itersection there is no need to draw anything
        if(!patchInfo.intersects) return;

            // If being asked for the same area as last time and not because of an update then nothing to do
        if (!patchInfo.areaChanged && !update) return;

            // Create and add the mEntireView view if not already done
        if (mEntireView == null) {
            mEntireView = new PatchView(mContext);
            addView(mEntireView);
            if(mOverlayView != null) mOverlayView.bringToFront();
        }
        
        mEntireView.renderInBackground(patchInfo);
    }
    
    
    public void addHq(boolean update) {//If update is true, a more efficient method is used to redraw the patch but it is redrawn even if the area hasn't changed
        Rect viewArea = new Rect(getLeft(),getTop(),getRight(),getBottom());
        
        if(viewArea == null || mSize == null)
            return;
            
            // If the viewArea's size matches the unzoomed size, there is no need for a hq patch
        if (viewArea.width() == mSize.x && viewArea.height() == mSize.y) return;

            //Construct the PatchInfo (important: the bitmap is shared between all page views that belong to a given readerview, so we ask the ReadderView to provide it)
        PatchInfo patchInfo = new PatchInfo(viewArea, ((ReaderView)mParent).getPatchBm(update), mHqView, update);

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

    public void removeHq() {
        if (mHqView != null) mHqView.reset();
    }

    public void redraw(boolean update) {
        addEntire(update);
        addHq(update);
        mOverlayView.invalidate();
    }

    @Override
    public float getScale() {
        return mSourceScale*(float)getWidth()/(float)mSize.x;
    }

    public void setSearchResult(SearchResult searchTaskResult) {
        mSearchResult = searchTaskResult;
    }
    
    public static void onSharedPreferenceChanged(SharedPreferences sharedPref, String key, Context context) {
            //Set ink thickness and colors for PageView
        try{
            inkThickness = Float.parseFloat(sharedPref.getString(SettingsActivity.PREF_INK_THICKNESS, Float.toString(inkThickness)));
        }
        catch(NumberFormatException ex) {
            TypedValue typedValue = new TypedValue();
            context.getResources().getValue(R.dimen.ink_thickness_default, typedValue, true);
            inkThickness = typedValue.getFloat();
        }

        try{
            eraserThickness = Float.parseFloat(sharedPref.getString(SettingsActivity.PREF_ERASER_THICKNESS, Float.toString(eraserThickness)).replaceAll("[^0-9.]",""));
        }
        catch(NumberFormatException ex) {
            TypedValue typedValue = new TypedValue();
            context.getResources().getValue(R.dimen.eraser_thickness_default, typedValue, true);
            eraserThickness = typedValue.getFloat();
        }
            
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
            //Before we can save we first need to copy drawing data into an
            //object of a serializable class because unfortunately PointF
            //does not implement Serializable.
        ArrayList<ArrayList<PointFSerializable>> drawingSerializable = new ArrayList<ArrayList<PointFSerializable>>();
        if(mDrawing != null)
            for(ArrayList<PointF> stroke : mDrawing) {
                ArrayList<PointFSerializable> strokeSerializable = new ArrayList<PointFSerializable>();
                if(stroke!=null)
                    for(PointF pointF : stroke) {
                        strokeSerializable.add(new PointFSerializable(pointF));
                    }
                drawingSerializable.add(strokeSerializable);
            }
        ArrayDeque<ArrayList<ArrayList<PointFSerializable>>> drawingHistorySerializable = new ArrayDeque<ArrayList<ArrayList<PointFSerializable>>>();
        if(mDrawingHistory != null)
            for(ArrayList<ArrayList<PointF>> list : mDrawingHistory) {
                ArrayList<ArrayList<PointFSerializable>> listSerializable = new ArrayList<ArrayList<PointFSerializable>>();
                if(list!=null)
                    for(ArrayList<PointF> stroke : list) {
                        ArrayList<PointFSerializable> strokeSerializable = new ArrayList<PointFSerializable>();
                        if(stroke!=null)
                            for(PointF pointF : stroke) {
                                strokeSerializable.add(new PointFSerializable(pointF));
                            }
                        listSerializable.add(strokeSerializable);
                    }
                drawingHistorySerializable.add(listSerializable);
            }
        
        bundle.putSerializable("mDrawing", drawingSerializable);
        bundle.putSerializable("mDrawingHistory", drawingHistorySerializable);
        bundle.putFloat("inkThickness",inkThickness);
        bundle.putFloat("eraserThickness",eraserThickness);
        bundle.putInt("inkColor",inkColor);
        bundle.putInt("highlightColor",highlightColor);
        bundle.putInt("underlineColor",underlineColor);
        bundle.putInt("strikeoutColor",strikeoutColor);
        bundle.putBoolean("useSmartTextSelection",useSmartTextSelection);
        return bundle;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            Bundle bundle = (Bundle) state;
                //Load 
            ArrayList<ArrayList<PointFSerializable>> drawingSerializable = (ArrayList<ArrayList<PointFSerializable>>)bundle.getSerializable("mDrawing");
            ArrayDeque<ArrayList<ArrayList<PointFSerializable>>> drawingHistorySerializable = (ArrayDeque<ArrayList<ArrayList<PointFSerializable>>>)bundle.getSerializable("mDrawingHistory");
            
            mDrawing = new ArrayList<ArrayList<PointF>>();
            if(drawingSerializable!=null && !drawingSerializable.isEmpty())
                for(ArrayList<PointFSerializable> strokeSerializable : drawingSerializable) {
                    ArrayList<PointF> stroke = new ArrayList<PointF>();
                    if(strokeSerializable!=null && !strokeSerializable.isEmpty())
                            //Somehow, in the following line instead of PointF I can not use PointFSerializable and I don't understand why. See also https://stackoverflow.com/questions/47741898/store-and-retrieve-arraylist-of-custom-serializable-class-from-bundle
                        for(PointF pointFSerializable : strokeSerializable) {
                            stroke.add((PointF)pointFSerializable);
                        }
                    mDrawing.add(stroke);
                }
            mDrawingHistory = new ArrayDeque<ArrayList<ArrayList<PointF>>>();
            if(drawingHistorySerializable!=null && !drawingHistorySerializable.isEmpty())
                for(ArrayList<ArrayList<PointFSerializable>> listSerializable : drawingHistorySerializable) {
                    ArrayList<ArrayList<PointF>> list = new ArrayList<ArrayList<PointF>>();
                    if(listSerializable!=null && !listSerializable.isEmpty())
                        for(ArrayList<PointFSerializable> strokeSerializable : listSerializable) {
                            ArrayList<PointF> stroke = new ArrayList<PointF>();
                            if(listSerializable!=null && !listSerializable.isEmpty())
                                for(PointF pointFSerializable : strokeSerializable) {
                                    stroke.add((PointF)pointFSerializable);
                                }
                            list.add(stroke);
                        }
                    mDrawingHistory.add(list);       
                }
            
            inkThickness = bundle.getFloat("inkThickness");
            eraserThickness = bundle.getFloat("eraserThickness");
            inkColor = bundle.getInt("inkColor");
            highlightColor = bundle.getInt("highlightColor");
            underlineColor = bundle.getInt("underlineColor");
            strikeoutColor = bundle.getInt("strikeoutColor");
            useSmartTextSelection = bundle.getBoolean("useSmartTextSelection");
            state = bundle.getParcelable("superInstanceState");
        }
        super.onRestoreInstanceState(state);
    }

    public Bitmap getHqImageBitmap() {
        if(mHqView == null) return null;
        return mHqView.getImageBitmap();
    }

    public boolean saveDraw() {
        if(mOverlayView != null && mHqView != null){
            Bitmap bitmap = mHqView.getImageBitmap();
            if(bitmap!=null)
            {
                Canvas canvas = new Canvas(bitmap);
                float scale = mSourceScale*(float)getWidth()/(float)mSize.x;
                canvas.translate(Math.min(getLeft(),0), Math.min(getTop(),0));
                mOverlayView.drawDrawing(canvas, scale);
                mHqView.setImageBitmap(bitmap);
            }
        }
        return true;
    }
}
