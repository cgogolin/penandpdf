package com.cgogolin.penandpdf;

import java.lang.Math;

import java.util.LinkedList;
import java.util.NoSuchElementException;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.Scroller;
import android.widget.Toast;
import android.widget.ImageView;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import android.util.Log;

abstract public class ReaderView extends AdapterView<Adapter> implements GestureDetector.OnGestureListener, ScaleGestureDetector.OnScaleGestureListener, Runnable
{
    private static final int  MOVING_DIAGONALLY = 0;
    private static final int  MOVING_LEFT       = 1;
    private static final int  MOVING_RIGHT      = 2;
    private static final int  MOVING_UP         = 3;
    private static final int  MOVING_DOWN       = 4;

    private static final int  FLING_MARGIN      = 100;
    private static final int  GAP               = 20;

    private static final float MIN_SCALE        = 1.0f;
    private static final float MAX_SCALE        = 10.0f;
    private static final float REFLOW_SCALE_FACTOR = 0.5f;

        //Set in onSharedPreferenceChanged()
    protected static boolean mUseStylus = false;
    protected static boolean mFitWidth = false;

    private Bitmap mSharedHqBm1;
    private Bitmap mSharedHqBm2;

    private Adapter           mAdapter;
    private int               mCurrent = INVALID_POSITION;    // Adapter's index for the current view
    private int               mNewCurrent;
    private boolean           mHasNewCurrent = false;
    private boolean           mNextScrollWithCenter = false;
    private final SparseArray<View> mChildViews = new SparseArray<View>(3); // Shadows the children of the AdapterView but with more sensible indexing
    private final LinkedList<View> mViewCache = new LinkedList<View>();
    private boolean           mUserInteracting;  // Whether the user is interacting
    private boolean           mScaling;    // Whether the user is currently pinch zooming
    private float             mScale     = 1.0f; //mScale = 1.0 corresponds to "fit to screen"
    private float             mNewNormalizedScale = 0;//Set in setNormalizedScale() and accounted for in onLayout()
    private float             mNewNormalizedXScroll = 0;//Set in setNormalizedXScroll() and accounted for in onLayout()
    private float             mNewNormalizedYScroll = 0;//Set in setNormalizedYScroll() and accounted for in onLayout()
    private float             mNewDocRelXScroll = 0;//Set in setDocRelXScroll() and accounted for in onLayout()
    private float             mNewDocRelYScroll = 0;//Set in setDocRelYScroll() and accounted for in onLayout()

    private boolean           mHasNewNormalizedScale = false;//Set in setNormalizedScale() and accounted for in onLayout()
    private boolean           mHasNewNormalizedXScroll = false;//Set in setNormalizedXScroll() and accounted for in onLayout()
    private boolean           mHasNewNormalizedYScroll = false;//Set in setNormalizedYScroll() and accounted for in onLayout()
    private boolean           mHasNewDocRelXScroll = false;//Set in setDocRelXScroll() and accounted for in onLayout()
    private boolean           mHasNewDocRelYScroll = false;//Set in setDocRelYScroll() and accounted for in onLayout()

        // Scroll amounts recorded from events and then accounted for in onLayout.
    private int               mXScroll = 0;    
    private int               mYScroll = 0;
    private int               mScrollerLastX;
    private int               mScrollerLastY;
    private int               previousFocusX;
    private int               previousFocusY;
    
    private boolean           mReflow = false;
    private final GestureDetector mGestureDetector;
    private final ScaleGestureDetector mScaleGestureDetector;
    private final Scroller    mScroller;
    private boolean           mScrollDisabled;

    Parcelable displayedViewInstanceState = null; //Set by MuPDFReaderView in onRestoreInstanceState()
    
    static abstract class ViewMapper {
        abstract void applyToView(View view);
    }

    public ReaderView(Context context) {
        super(context);
        mGestureDetector = new GestureDetector(this);
        mScaleGestureDetector = new ScaleGestureDetector(context, this);
        mScroller        = new Scroller(context);
        mScroller.forceFinished(true); //Otherwise mScroller.isFinished() is not true which prevents the generation of the Hq area
    }

    @Override
        public int getSelectedItemPosition() {
        return mCurrent;
    }

    public void setDisplayedViewIndex(int i) {
        setDisplayedViewIndex(i, true);
    }
    
    public void setDisplayedViewIndex(int i, boolean countsAsNewCurrent) {
        if (0 <= i && i < mAdapter.getCount()) {
            mNewCurrent = i;
            mHasNewCurrent = countsAsNewCurrent;
            requestLayout();
        }
    }

    public void moveToNext() {
        View v = mChildViews.get(mCurrent+1);
        if (v != null)
            slideViewOntoScreen(v);
    }

    public void moveToPrevious() {
        View v = mChildViews.get(mCurrent-1);
        if (v != null)
            slideViewOntoScreen(v);
    }

	// When advancing down the page, we want to advance by about
	// 90% of a screenful. But we'd be happy to advance by between
	// 80% and 95% if it means we hit the bottom in a whole number
	// of steps.
    private int smartAdvanceAmount(int screenHeight, int max) {
        int advance = (int)(screenHeight * 0.9 + 0.5);
        int leftOver = max % advance;
        int steps = max / advance;
        if (leftOver == 0) {
                // We'll make it exactly. No adjustment
        } else if ((float)leftOver / steps <= screenHeight * 0.05) {
                // We can adjust up by less than 5% to make it exact.
            advance += (int)((float)leftOver/steps + 0.5);
        } else {
            int overshoot = advance - leftOver;
            if ((float)overshoot / steps <= screenHeight * 0.1) {
                    // We can adjust down by less than 10% to make it exact.
                advance -= (int)((float)overshoot/steps + 0.5);
            }
        }
        if (advance > max)
            advance = max;
        return advance;
    }

    
    public void smartMoveForwards() {
        View v = getSelectedView();
        if (v == null)
            return;

            // The following code works in terms of where the screen is on the views;
            // so for example, if the currentView is at (-100,-100), the visible
            // region would be at (100,100). If the previous page was (2000, 3000) in
            // size, the visible region of the previous page might be (2100 + GAP, 100)
            // (i.e. off the previous page). This is different to the way the rest of
            // the code in this file is written, but it's easier for me to think about.
            // At some point we may refactor this to fit better with the rest of the
            // code.

            // screenWidth/Height are the actual visible part of this view
        int screenWidth  = getWidth()-getPaddingLeft()-getPaddingRight();
        int screenHeight = getHeight()-getPaddingTop()-getPaddingBottom();
            // We might be mid scroll; we want to calculate where we scroll to based on
            // where this scroll would end, not where we are now (to allow for people
            // bashing 'forwards' very fast.
        int remainingX = mScroller.getFinalX() - mScroller.getCurrX();
        int remainingY = mScroller.getFinalY() - mScroller.getCurrY();
            // right/bottom is in terms of pixels within the scaled document; e.g. 1000
        int left = -(v.getLeft() + mXScroll + remainingX) + getPaddingLeft();
        int top  = -(v.getTop()  + mYScroll + remainingY) + getPaddingTop();
        int right  = screenWidth  + left;
        int bottom = screenHeight + top;
            // docWidth/Height are the width/height of the scaled document e.g. 2000x3000
        int docWidth  = v.getMeasuredWidth();
        int docHeight = v.getMeasuredHeight();

        int xOffset, yOffset;
        if (bottom >= docHeight || screenHeight >= 0.8*docHeight) // We are flush with the bottom or the user can see almost all of the page -> advance to next column.
        {
            if (right + 0.4*screenWidth > docWidth || screenWidth >= 0.7*docWidth ) // No room for another column or the user can see almost the wholepage -> go to next page
            {
                View nv = mChildViews.get(mCurrent+1);
                if (nv == null) // No page to advance to
                    return;
                int nextTop  = -(nv.getTop() + mYScroll + remainingY) + getPaddingTop();
                int nextLeft = -(nv.getLeft() + mXScroll + remainingX) + getPaddingLeft();
                int nextDocWidth = nv.getMeasuredWidth();
                int nextDocHeight = nv.getMeasuredHeight();

                    // Allow for the next page maybe being shorter than the screen is high
                if(nextDocHeight < screenHeight)
                {
                    yOffset = ((nextDocHeight - screenHeight)>>1);
                } else if(screenHeight >= 0.8*docHeight)
                {
                    yOffset = top;
                }
                else
                {
                    yOffset = 0;
                }
                
                if (nextDocWidth < screenWidth) // Next page is too narrow to fill the screen. Scroll to the top, centred.
                {
                    xOffset = (nextDocWidth - screenWidth)>>1;
                } else {
                        // Reset X back to the left hand column
                    if(screenWidth >= 0.7*docWidth)
                        xOffset = left;
                    else
                        xOffset = 0;
                        // Adjust in case the previous page is less wide
                    if (xOffset + screenWidth > nextDocWidth)
                        xOffset = nextDocWidth - screenWidth;
                }
                xOffset -= nextLeft;
                yOffset -= nextTop;
            } else {
                    // Move to top of next column
                xOffset = Math.min(screenWidth, docWidth - right);
                yOffset = screenHeight - bottom;
            }
        } else {
                // Advance by 90% of the screen height downwards (in case lines are partially cut off)
            xOffset = 0;
            yOffset = smartAdvanceAmount(screenHeight, docHeight - bottom);
        }
        mScrollerLastX = mScrollerLastY = 0;
        mScroller.startScroll(0, 0, remainingX - xOffset, remainingY - yOffset, 400);
        post(this);
    }

    public void smartMoveBackwards() {
        View v = getSelectedView();
        if (v == null)
            return;

            // The following code works in terms of where the screen is on the views;
            // so for example, if the currentView is at (-100,-100), the visible
            // region would be at (100,100). If the previous page was (2000, 3000) in
            // size, the visible region of the previous page might be (2100 + GAP, 100)
            // (i.e. off the previous page). This is different to the way the rest of
            // the code in this file is written, but it's easier for me to think about.
            // At some point we may refactor this to fit better with the rest of the
            // code.

            // screenWidth/Height are the actual fully visible part of this view
        int screenWidth  = getWidth()-getPaddingLeft()-getPaddingRight();
        int screenHeight = getHeight()-getPaddingTop()-getPaddingBottom();
            // We might be mid scroll; we want to calculate where we scroll to based on
            // where this scroll would end, not where we are now (to allow for people
            // bashing 'forwards' very fast.
        int remainingX = mScroller.getFinalX() - mScroller.getCurrX();
        int remainingY = mScroller.getFinalY() - mScroller.getCurrY();
            // left/top is in terms of pixels within the scaled document; e.g. 1000
        int left  = -(v.getLeft() + mXScroll + remainingX) + getPaddingLeft();
        int top   = -(v.getTop()  + mYScroll + remainingY) + getPaddingTop();
        // int right  = screenWidth  + left;
        // int bottom = screenHeight + top;
            // docWidth/Height are the width/height of the scaled document e.g. 2000x3000
        int docWidth  = v.getMeasuredWidth();
        int docHeight = v.getMeasuredHeight();

        int xOffset, yOffset;
        if (top <= 0 || screenHeight >= 0.8*docHeight ) // We are flush with the top or the user can see almost all of the page -> step back to previous column.
        {          
            if (left < 0.4 * screenWidth || screenWidth >= 0.7*docWidth) // No room for previous column or the user can see almost the wholepage -> go to previous page 
            {
                View pv = mChildViews.get(mCurrent-1);
                if (pv == null) /* No page to advance to */
                    return;
                int prevLeft  = -(pv.getLeft() + mXScroll) + getPaddingLeft();
                int prevTop  = -(pv.getTop() + mYScroll) + getPaddingTop();
                int prevDocWidth = pv.getMeasuredWidth();
                int prevDocHeight = pv.getMeasuredHeight();

                    // Allow for the next page maybe being shorter than the screen is high
                if(prevDocHeight < screenHeight)
                {
                    yOffset = ((prevDocHeight - screenHeight)>>1);
                } else if(screenHeight >= 0.8*docHeight)
                {
                    yOffset = top - prevDocHeight+screenHeight;
                }
                else
                {
                    yOffset = 0;
                }
                
                if (prevDocWidth < screenWidth) {
                        // Previous page is too narrow to fill the screen. Scroll to the bottom, centred.
                    xOffset = (prevDocWidth - screenWidth)>>1;
                } else {
                        // Reset X back to the right hand column
                    if(screenWidth >= 0.7*docWidth)
                        xOffset = left;
                    else
                        xOffset = docWidth-screenWidth;
                        // Adjust in case the next page is less wide
                    if (xOffset + screenWidth > prevDocWidth)
                        xOffset = prevDocWidth - screenWidth;
                    while (xOffset + screenWidth*2 < prevDocWidth)
                        xOffset += screenWidth;
                }
                xOffset -= prevLeft;
                yOffset -= prevTop + (-prevDocHeight+screenHeight >= 0 ? 0 : -prevDocHeight+screenHeight);
            } else {
                    // Move to bottom of previous column
                xOffset = - Math.min(screenWidth,left);
                yOffset = docHeight - screenHeight + top;
            }
        } else {
                // Retreat by 90% of the screen height downwards (in case lines are partially cut off)
            xOffset = 0;
            yOffset = -smartAdvanceAmount(screenHeight, top);
        }
        mScrollerLastX = mScrollerLastY = 0;
        mScroller.startScroll(0, 0, remainingX - xOffset, remainingY - yOffset, 400);
        post(this);
    }

    public void resetupChildren() {
        for (int i = 0; i < mChildViews.size(); i++)
            onChildSetup(mChildViews.keyAt(i), mChildViews.valueAt(i));
    }

    public void applyToChildren(ViewMapper mapper) {
        for (int i = 0; i < mChildViews.size(); i++)
            mapper.applyToView(mChildViews.valueAt(i));
    }

        //To be overwritten in MuPDFReaderView
    abstract protected void onChildSetup(int i, View v);
    abstract protected void onMoveToChild(int pageNumber);
    abstract protected void onMoveOffChild(int i);
    abstract protected void onSettle(View v);
    abstract protected void onUnsettle(View v);
    abstract protected void onScaleChild(View v, Float scale);
    abstract protected void onNumberOfStrokesChanged(int numberOfStrokes);
    
    public View getView(int i) {
        return mChildViews.get(i); //Can return null while waiting for onLayout()!
    }

    public void run() {
        if (!mScroller.isFinished()) {
            mScroller.computeScrollOffset();
            int x = mScroller.getCurrX();
            int y = mScroller.getCurrY();
            mXScroll += x - mScrollerLastX;
            mYScroll += y - mScrollerLastY;
            mScrollerLastX = x;
            mScrollerLastY = y;
            requestLayout();
            if(!mScrollDisabled) post(this);
        }
        else if (!mUserInteracting) {
                // End of an inertial scroll and the user is not interacting.
                // The layout is stable
            View v = getSelectedView();
            if (v != null) postSettle(v);
        }
    }
    
    @Override
        public boolean onDown(MotionEvent arg0) {
        mScroller.forceFinished(true);
        return true;
    }

    @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                               float velocityY) {
        if (mScrollDisabled)
            return true;

        View v = getSelectedView();
        if (v != null) {
            Rect bounds = getScrollBounds(v);
            switch(directionOfTravel(velocityX, velocityY)) {
                case MOVING_LEFT:
                    if (bounds.left >= 0) {
                            // Fling off to the left bring next view onto screen
                        View vl = mChildViews.get(mCurrent+1);

                        if (vl != null) {
                            slideViewOntoScreen(vl);
                            return true;
                        }
                    }
                    break;
                case MOVING_RIGHT:
                    if (bounds.right <= 0) {
                            // Fling off to the right bring previous view onto screen
                        View vr = mChildViews.get(mCurrent-1);

                        if (vr != null) {
                            slideViewOntoScreen(vr);
                            return true;
                        }
                    }
                    break;
            }
            mScrollerLastX = mScrollerLastY = 0;
                // If the page has been dragged out of bounds then we want to spring back
                // nicely. fling jumps back into bounds instantly, so we don't want to use
                // fling in that case. On the other hand, we don't want to forgo a fling
                // just because of a slightly off-angle drag taking us out of bounds other
                // than in the direction of the drag, so we test for out of bounds only
                // in the direction of travel.
                //
                // Also don't fling if out of bounds in any direction by more than fling
                // margin
            Rect expandedBounds = new Rect(bounds);
            expandedBounds.inset(-FLING_MARGIN, -FLING_MARGIN);

            if(withinBoundsInDirectionOfTravel(bounds, velocityX, velocityY)
               && expandedBounds.contains(0, 0)) {
                mScroller.fling(0, 0, (int)velocityX, (int)velocityY, bounds.left, bounds.right, bounds.top, bounds.bottom);
                post(this);
            }
        }

        return true;
    }
    
    @Override
        public void onLongPress(MotionEvent e) {
    }

    @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        if (!mScrollDisabled) {
            mXScroll -= distanceX;
            mYScroll -= distanceY;
            requestLayout();
        }
        return true;
    }

    @Override
        public void onShowPress(MotionEvent e) {
    }

    @Override
        public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }
    
    @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
        mScaling = true;
            // Ignore any scroll amounts yet to be accounted for: the
            // screen is not showing the effect of them, so they can
            // only confuse the user
        mXScroll = mYScroll = 0;
            // Avoid jump at end of scaling by disabling scrolling
            // until the next start of gesture
        mScrollDisabled = true;
        
        previousFocusX = (int)detector.getFocusX();
        previousFocusY = (int)detector.getFocusY();
        return true;
    }

    @Override 
        public boolean onScale(ScaleGestureDetector detector) {
        float previousScale = mScale;
        float scale_factor = mReflow ? REFLOW_SCALE_FACTOR : 1.0f;
        float min_scale = MIN_SCALE * scale_factor;
        float max_scale = MAX_SCALE * scale_factor;
        mScale = Math.min(Math.max(mScale * detector.getScaleFactor(), min_scale), max_scale);
                
        if (mReflow) {
            View v = getSelectedView();
            if (v != null)
                onScaleChild(v, mScale);
        } else {
            float factor = mScale/previousScale;

            View v = getSelectedView();
            if (v != null) {
                    // Work out the focus point relative to the view top left
                int viewFocusX = (int)detector.getFocusX() - (v.getLeft() + mXScroll);
                int viewFocusY = (int)detector.getFocusY() - (v.getTop() + mYScroll);
                    // Scroll to keep the focus point over the same place
                mXScroll += viewFocusX - viewFocusX * factor - previousFocusX + (int)detector.getFocusX();
                mYScroll += viewFocusY - viewFocusY * factor - previousFocusY + (int)detector.getFocusY();
                previousFocusX = (int)detector.getFocusX();
                previousFocusY = (int)detector.getFocusY();            
                requestLayout();
            }
        }
        return true;
    }

    @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
        if (mReflow) {
            applyToChildren(new ViewMapper() {
                    @Override
                    void applyToView(View view) {
                        onScaleChild(view, mScale);
                    }
                });
        }

            //Snap to page width
        if(mFitWidth)
        {
            View cv = getSelectedView();
            if(cv != null) 
            {
                float previousScale = mScale;
                float scale_factor = mReflow ? REFLOW_SCALE_FACTOR : 1.0f;
                float min_scale = MIN_SCALE * scale_factor;
                float max_scale = MAX_SCALE * scale_factor;
                float scale = getFillScreenScale(cv);
                float fitWidthScale = (float)getWidth()/(cv.getMeasuredWidth()*scale);
                if ( Math.abs(mScale - fitWidthScale) <= 0.15 && fitWidthScale >= 1.15) 
                {
                    mScale = Math.min(Math.max(fitWidthScale, min_scale), max_scale);
                    mScroller.forceFinished(true);
                    mXScroll = -cv.getLeft();
                    mYScroll = 0;
                    requestLayout();
                }
            }
        }
        
        mScaling = false;
    }

    @Override
	public boolean onTouchEvent(MotionEvent event) {
        mScaleGestureDetector.onTouchEvent(event);

        if (!mScaling)
            mGestureDetector.onTouchEvent(event);

        if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_DOWN) {
            mUserInteracting = true;
        }
        if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
            mScrollDisabled = false;
            mUserInteracting = false;

            View v = getSelectedView();
            if (v != null) {
                if (mScroller.isFinished()) {
                        // If, at the end of user interaction, there is no
                        // current inertial scroll in operation then animate
                        // the view onto screen if necessary
                    slideViewOntoScreen(v);
                }

                if (mScroller.isFinished()) {
                        // If still there is no inertial scroll in operation
                        // then the layout is stable
                    postSettle(v);
                }
            }
        }

        return true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int x, y;
        x = View.MeasureSpec.getSize(widthMeasureSpec);
        y = View.MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(x, y);
        
        int n = getChildCount();
        for (int i = 0; i < n; i++)
            measureView(getChildAt(i));
    }

    public Bitmap getPatchBm(boolean update) {
            //We must make sure that we return one of two
            //bitmaps in an alternating manner, so that the native code can draw to one
            //while the other is set to the Hq view
            //if update=true the situation changes, then the native code should
            //precisely draw to the bitmap currently shown
        Bitmap currentBitmap = ((PageView)getSelectedView()).getHqImageBitmap();

        if(currentBitmap == null || (currentBitmap == mSharedHqBm2 && !update) || (currentBitmap == mSharedHqBm1 && update))
        {
            if (mSharedHqBm1 == null || mSharedHqBm1.getWidth() != getWidth() || mSharedHqBm1.getHeight() != getHeight())
            {
                mSharedHqBm1 = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
            }
            return mSharedHqBm1;
        }
        else {
            if (mSharedHqBm2 == null || mSharedHqBm2.getWidth() != getWidth() || mSharedHqBm2.getHeight() != getHeight())
            {
                mSharedHqBm2 = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
            }
            return mSharedHqBm2;
        }
    }
    
    
    @Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        
        View cv = getSelectedView();

            //If we were asked to display a different view do so now...
        if(mHasNewCurrent)
        {
            if (cv != null) onUnsettle(cv);
                //Reset scroll amounts
            mXScroll = mYScroll = 0;
            onMoveOffChild(mCurrent);
            mCurrent = mNewCurrent;
            onMoveToChild(mCurrent);
            mHasNewCurrent = false;
            post(this);
        }
            //... else check if we should be switching to a new view and do it
        else if (cv != null && maySwitchView()) {
            Point cvOffset = subScreenSizeOffset(cv);
                // Move to next if current is sufficiently off center
                // cv.getRight() may be out of date with the current scale
                // so add left to the measured width for the correct position
            if (cv.getLeft() + cv.getMeasuredWidth() + cvOffset.x + GAP/2 + mXScroll < getWidth()/2 && mCurrent + 1 < mAdapter.getCount()) {
                postUnsettle(cv);
                onMoveOffChild(mCurrent);
                mCurrent++;
                onMoveToChild(mCurrent);
                    // post to invoke test for end of animation
                    // where we must set hq area for the new current view
                post(this);
            }
                    // Move to previous if current is sufficiently off center
            if (cv.getLeft() - cvOffset.x - GAP/2 + mXScroll >= getWidth()/2 && mCurrent > 0) {
                postUnsettle(cv);
                onMoveOffChild(mCurrent);
                mCurrent--;
                onMoveToChild(mCurrent);
                    // post to invoke test for end of animation
                    // where we must set hq area for the new current view
                post(this);
            }
        }//mCurrent now is the view we should actually be displaying
        
            // Remove not needed children and hold them for reuse
        removeSuperflousChildren();
        
            //Caculate placement of the current view
        int cvLeft, cvRight, cvTop, cvBottom;
        {
            cv = getOrCreateChild(mCurrent, right-left, bottom-top);
            
                //Set mXScroll, mYScroll and mScale from the values set in setScale() and setScroll()
            if(!mReflow)
            {
                float scale_factor = mReflow ? REFLOW_SCALE_FACTOR : 1.0f;
                float min_scale = MIN_SCALE * scale_factor;
                float max_scale = MAX_SCALE * scale_factor;
                float scale = getFillScreenScale(cv);
                float scaleCorrection = getScaleCorrection(cv);
                
                if(mHasNewNormalizedScale)
                {
                    mScale = Math.min(Math.max(mNewNormalizedScale*scaleCorrection, min_scale), max_scale); 
                    mHasNewNormalizedScale = false;
                }
                if (mHasNewDocRelXScroll)
                {
                    mHasNewNormalizedXScroll = true;
                    mHasNewDocRelXScroll = false;
                    mNewNormalizedXScroll = -mNewDocRelXScroll*((PageView)cv).getScale()/(cv.getMeasuredWidth()*mScale*scale);
                }
                if (mHasNewDocRelYScroll)
                {
                    mHasNewNormalizedYScroll = true;
                    mHasNewDocRelYScroll = false;
                    mNewNormalizedYScroll = -mNewDocRelYScroll*((PageView)cv).getScale()/(cv.getMeasuredHeight()*mScale*scale);
                }
                    
                if (mHasNewNormalizedXScroll || mHasNewNormalizedYScroll)
                {
                        //Preset to the current values
                    int XScroll = (int)(getNormalizedXScroll()*cv.getMeasuredWidth()*mScale*scale);
                    int YScroll = (int)(getNormalizedYScroll()*cv.getMeasuredHeight()*mScale*scale);
                    
                    if(mHasNewNormalizedXScroll){
                        XScroll = (int)(mNewNormalizedXScroll*cv.getMeasuredWidth()*mScale*scale)+getPaddingLeft();
                        mHasNewNormalizedXScroll = false;
                    }
                    if(mHasNewNormalizedYScroll){
                        YScroll = (int)(mNewNormalizedYScroll*cv.getMeasuredHeight()*mScale*scale)+getPaddingTop();
                        mHasNewNormalizedYScroll = false;
                    }

                    if(mNextScrollWithCenter)
                    {
                        mNextScrollWithCenter = false;
                        XScroll+=getWidth()/2;
                        YScroll+=getHeight()/2;
                    }

                    mScroller.forceFinished(true);
                    mScrollerLastX = mScrollerLastY = 0;
                    mXScroll = XScroll - cv.getLeft();
                    mYScroll = YScroll - cv.getTop();
                }
            }
            
                //Set the positon of the top left corner
            cvLeft = cv.getLeft() + mXScroll;
            cvTop  = cv.getTop()  + mYScroll;
            
                //Reset scroll amounts
            mXScroll = mYScroll = 0;
        }
            //Calculate right and bottom after scaling the child
        onScaleChild(cv, mScale);
        cvRight  = cvLeft + cv.getMeasuredWidth();
        cvBottom = cvTop  + cv.getMeasuredHeight();

            //If the user is not interacting and the scroller is finished move the view so that no gaps are left
        if (!mUserInteracting && mScroller.isFinished() && !changed) {            
            Point corr = getCorrection(getScrollBounds(cvLeft-getPaddingLeft(), cvTop-getPaddingTop(), cvRight-getPaddingRight(), cvBottom-getPaddingBottom()));
            cvRight  += corr.x;
            cvLeft   += corr.x;
            cvTop    += corr.y;
            cvBottom += corr.y;
        }        

            //Finally layout the child view with the calculated values
        cv.layout(cvLeft, cvTop, cvRight, cvBottom);

            //Start the generation of the HQ area if appropriate
        if (!mUserInteracting && mScroller.isFinished())
            postSettle(cv);
        
            //Creat and layout the preceding and following PageViews
        Point cvOffset = subScreenSizeOffset(cv);
        if (mCurrent > 0) {
            View lv = getOrCreateChild(mCurrent - 1, right-left, bottom-top);
            Point leftOffset = subScreenSizeOffset(lv);
            int gap = leftOffset.x + GAP + cvOffset.x;
            lv.layout(cvLeft - lv.getMeasuredWidth() - gap,
                      (cvBottom + cvTop - lv.getMeasuredHeight())/2,
                      cvLeft - gap,
                      (cvBottom + cvTop + lv.getMeasuredHeight())/2);
        }
        if (mCurrent + 1 < mAdapter.getCount()) {
            View rv = getOrCreateChild(mCurrent + 1, right-left, bottom-top);
            Point rightOffset = subScreenSizeOffset(rv);
            int gap = cvOffset.x + GAP + rightOffset.x;
            rv.layout(cvRight + gap,
                      (cvBottom + cvTop - rv.getMeasuredHeight())/2,
                      cvRight + rv.getMeasuredWidth() + gap,
                      (cvBottom + cvTop + rv.getMeasuredHeight())/2);
        }
    }

    
    private void removeAllChildren() {
        int numChildren = mChildViews.size();
        for (int i = 0; i < numChildren; i++) {
            View v = mChildViews.valueAt(i);
            ((MuPDFView) v).releaseResources();
            removeViewInLayout(v);
        }
        mChildViews.clear();
        mViewCache.clear();
    }
    
    
    private void removeSuperflousChildren() {
        int numChildren = mChildViews.size();
        int childIndices[] = new int[numChildren];
        for (int i = 0; i < numChildren; i++)
            childIndices[i] = mChildViews.keyAt(i);
        

        int maxCount = mAdapter.getCount();
        for (int i = 0; i < numChildren; i++) {
            int ai = childIndices[i];
            if (ai < mCurrent - 1 || ai > mCurrent + 1 || ai < 0 || ai >= maxCount) {
                View v = mChildViews.get(ai);
                ((MuPDFView) v).releaseResources();
                mViewCache.add(v);
                removeViewInLayout(v);
                mChildViews.remove(ai);
            }
        }
    }
    
    @Override
	public Adapter getAdapter() {
        return mAdapter;
    }

    @Override
	public View getSelectedView() {
        return mChildViews.get(mCurrent); //Can return null while waiting for onLayout()!
    }

    @Override
    public void setAdapter(Adapter adapter) {
        mAdapter = adapter;
        removeAllChildren();
        removeAllViewsInLayout();
        requestLayout();
    }

    @Override
	public void setSelection(int arg0) {
        throw new UnsupportedOperationException(getContext().getString(R.string.not_supported));
    }

    private View getCached() {
        if (mViewCache.size() == 0)
            return null;
        else
            return mViewCache.removeFirst();
    }


    private View getOrCreateChild(int i, int width, int height) {
        View v = mChildViews.get(i);
        if (v == null) {
            v = mAdapter.getView(i, getCached(), this);
            onChildSetup(i, v);
            onScaleChild(v, mScale);
            addAndMeasureChild(i, v);
                //If we are creating the current view and have a saved instance state restore it
            if(i == mCurrent && displayedViewInstanceState != null){
                ((PageView)v).onRestoreInstanceState(displayedViewInstanceState);
                displayedViewInstanceState = null;
                onNumberOfStrokesChanged(((PageView)v).getDrawingSize());
            }
        }
        return v;
    }

    private void addAndMeasureChild(int i, View v) {
        LayoutParams params = v.getLayoutParams();
        if (params == null) {
            params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        }
        measureView(v);
        addViewInLayout(v, 0, params, true);
        mChildViews.append(i, v); // Record the view against it's adapter index
    }

    private float getFillScreenScale(View v) {
        return Math.min((float)(getWidth()-getPaddingLeft()-getPaddingRight())/(float)v.getMeasuredWidth(),(float)(getHeight()-getPaddingTop()-getPaddingBottom())/(float)v.getMeasuredHeight());
    }


    private float getScaleCorrection(View v) {
        return (float)(getWidth()-getPaddingLeft()-getPaddingRight())/(v.getMeasuredWidth()*getFillScreenScale(v));
    }

    
    private void measureView(View v) {
            // See what size the view wants to be
        v.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);

        if (!mReflow) {
                // Work out a scale that will fit it to this view
            float scale = getFillScreenScale(v); //This makes scale=1.0 correspond to fit to screen
                // Use the fitting values scaled by our current scale factor
            v.measure(View.MeasureSpec.EXACTLY | (int)(v.getMeasuredWidth()*scale*mScale),
                      View.MeasureSpec.EXACTLY | (int)(v.getMeasuredHeight()*scale*mScale));
        } else {
            v.measure(View.MeasureSpec.EXACTLY | (int)(v.getMeasuredWidth()),
                      View.MeasureSpec.EXACTLY | (int)(v.getMeasuredHeight()));
        }
    }

    private Rect getScrollBounds(int left, int top, int right, int bottom) {
        int xmin = getWidth() - right;
        int xmax = -left;
        int ymin = getHeight() - bottom;
        int ymax = -top;

            // In either dimension, if view smaller than screen then
            // constrain it to be central
        if (xmin > xmax) xmin = xmax = (xmin + xmax)/2;
        if (ymin > ymax) ymin = ymax = (ymin + ymax)/2;

        return new Rect(xmin, ymin, xmax, ymax);
    }

    private Rect getScrollBounds(View v) {
            // There can be scroll amounts not yet accounted for in
            // onLayout, so add mXScroll and mYScroll to the current
            // positions when calculating the bounds.
        return getScrollBounds(v.getLeft() + mXScroll - getPaddingLeft(),
                               v.getTop() + mYScroll - getPaddingTop(),
                               v.getLeft() + v.getMeasuredWidth() + mXScroll + getPaddingRight(),
                               v.getTop() + v.getMeasuredHeight() + mYScroll + getPaddingBottom());
    }

    private Point getCorrection(Rect bounds) {
        return new Point(Math.min(Math.max(0,bounds.left),bounds.right),
                         Math.min(Math.max(0,bounds.top),bounds.bottom));
    }

    private void postSettle(final View v) {
            // onSettle and onUnsettle are posted so that the calls
            // wont be executed until after the system has performed
            // layout.
        post(new Runnable() {
                public void run () {
                    onSettle(v);
                }
            });
    }

    private void postUnsettle(final View v) {
        post (new Runnable() {
                public void run () {
                    onUnsettle(v);
                }
            });
    }

    private void slideViewOntoScreen(View v) {
        Point corr = getCorrection(getScrollBounds(v));
        if (corr.x != 0 || corr.y != 0) {
            mScrollerLastX = mScrollerLastY = 0;
            mScroller.startScroll(0, 0, corr.x, corr.y, 400);
            post(this);
        }
    }

    private Point subScreenSizeOffset(View v) {
        return new Point(Math.max((getWidth() - v.getMeasuredWidth())/2, 0),
                         Math.max((getHeight() - v.getMeasuredHeight())/2, 0));
    }

    private static int directionOfTravel(float vx, float vy) {
        if (Math.abs(vx) > 3 * Math.abs(vy))
            return (vx > 0) ? MOVING_RIGHT : MOVING_LEFT;
        else if (Math.abs(vy) > 3 * Math.abs(vx))
            return (vy > 0) ? MOVING_DOWN : MOVING_UP;
        else
            return MOVING_DIAGONALLY;
    }

    private static boolean withinBoundsInDirectionOfTravel(Rect bounds, float vx, float vy) {
        switch (directionOfTravel(vx, vy)) {
            case MOVING_DIAGONALLY: return bounds.contains(0, 0);
            case MOVING_LEFT:       return bounds.left <= 0;
            case MOVING_RIGHT:      return bounds.right >= 0;
            case MOVING_UP:         return bounds.top <= 0;
            case MOVING_DOWN:       return bounds.bottom >= 0;
            default: throw new NoSuchElementException();
        }
    }
        
    public float getNormalizedScale() 
    {
        View cv = getSelectedView();
        if (cv != null) {
            float scale = Math.min((float)(getWidth()-getPaddingLeft()-getPaddingRight())/(float)cv.getMeasuredWidth(),(float)(getHeight()-getPaddingTop()-getPaddingBottom())/(float)cv.getMeasuredHeight());
            float scaleCorrection = getScaleCorrection(cv);
            return mScale/scaleCorrection;
        }
        else
            return 1f;
    }
        
    public float getNormalizedXScroll()
    {
        View cv = getSelectedView();
        if (cv != null) {
            return (cv.getLeft()-getPaddingLeft())/(float)cv.getMeasuredWidth();
        }
        else return 0;
    }

    public float getNormalizedYScroll()
    {
        View cv = getSelectedView();
        if (cv != null) {
            return (cv.getTop()-getPaddingTop())/(float)cv.getMeasuredHeight();
        }
        else return 0;
    }

    public void setNormalizedScale(float normalizedScale)
    {
        mNewNormalizedScale = normalizedScale;
        mHasNewNormalizedScale = true;
        requestLayout();
    }

    public void setScale(float scale)
    {
        mScale = scale;
        requestLayout();
    }            
        
    public void setNormalizedScroll(float normalizedXScroll, float normalizedYScroll) 
    {
        setNormalizedXScroll(normalizedXScroll);
        setNormalizedYScroll(normalizedYScroll);
    }

    public void setNormalizedXScroll(float normalizedXScroll)
    {
        mHasNewNormalizedXScroll = true;
        mNewNormalizedXScroll = normalizedXScroll;
        requestLayout();
    }

    public void setNormalizedYScroll(float normalizedYScroll)
    {
        mHasNewNormalizedYScroll = true;
        mNewNormalizedYScroll = normalizedYScroll;
        requestLayout();
    }

    public void setDocRelXScroll(float docRelXScroll)
    {
        mHasNewDocRelXScroll = true;
        mNewDocRelXScroll = docRelXScroll;
        requestLayout();
    }

    public void setDocRelYScroll(float docRelYScroll)
    {
        mHasNewDocRelYScroll = true;
        mNewDocRelYScroll = docRelYScroll;
        requestLayout();
    }

    public void doNextScrollWithCenter()
    {
        mNextScrollWithCenter = true;
    }    

    public static void onSharedPreferenceChanged(SharedPreferences sharedPref, String key){
        mUseStylus = sharedPref.getBoolean(SettingsActivity.PREF_USE_STYLUS, false);
        mFitWidth = sharedPref.getBoolean(SettingsActivity.PREF_FIT_WIDTH, false);
    }

        //This method can be overwritten in super classes to prevent view switching while, for example, we are in drawing mode
    public boolean maySwitchView() {
        return true;
    }


    @Override
    public Parcelable onSaveInstanceState() {
        Bundle bundle = new Bundle();
        bundle.putParcelable("superInstanceState", super.onSaveInstanceState());
            //Save
        bundle.putInt("mCurrent", mCurrent);
        bundle.putInt("mXScroll", mXScroll);
        bundle.putInt("mYScroll", mYScroll);
        bundle.putInt("mScrollerLastX", mScrollerLastX);
        bundle.putInt("mScrollerLastY", mScrollerLastY);
        bundle.putInt("previousFocusX", previousFocusX);
        bundle.putInt("previousFocusY", previousFocusY);
        bundle.putBoolean("mReflow", mReflow);
        bundle.putBoolean("mScrollDisabled", mScrollDisabled);
        return bundle;
    }
    
    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            Bundle bundle = (Bundle) state;
                //Load
            mCurrent = bundle.getInt("mCurrent", mCurrent);
            mXScroll = bundle.getInt("mXScroll", mXScroll);
            mYScroll = bundle.getInt("mYScroll", mYScroll);
            mScrollerLastX = bundle.getInt("mScrollerLastX", mScrollerLastX);
            mScrollerLastY = bundle.getInt("mScrollerLastY", mScrollerLastY);
            previousFocusX = bundle.getInt("previousFocusX", previousFocusX);
            previousFocusY = bundle.getInt("previousFocusY", previousFocusY);
            mReflow = bundle.getBoolean("mReflow", mReflow);
            mScrollDisabled = bundle.getBoolean("mScrollDisabled", mScrollDisabled);
        
            state = bundle.getParcelable("superInstanceState");
        }
        super.onRestoreInstanceState(state);
    }
}
