package com.artifex.mupdfdemo;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.preference.PreferenceManager;

public class MuPDFReaderView extends ReaderView {
    enum Mode {Viewing, Selecting, Drawing}
    private final Context mContext;
    private boolean mLinksEnabled = false;
    private Mode mMode = Mode.Viewing;
    private boolean tapDisabled = false;
    private int tapPageMargin;

        //To be overwritten in MuPDFActivity:
    protected void onTapMainDocArea() {}
    protected void onDocMotion() {}
    protected void onHit(Hit item) {};
    protected void onSelectionStatusChanged() {};
    protected void onNumberOfStrokesChanged(int numberOfStrokes) {};

    public void setLinksEnabled(boolean b) {
        mLinksEnabled = b;
        resetupChildren();
    }

    public boolean linksEnabled() {
        return mLinksEnabled;
    }

    public void setMode(Mode m) {
        mMode = m;
    }

    public MuPDFReaderView(Activity act) {
        super(act);
        mContext = act;
            // Get the screen size etc to customise tap margins.
            // We calculate the size of 1 inch of the screen for tapping.
            // On some devices the dpi values returned are wrong, so we
            // sanity check it: we first restrict it so that we are never
            // less than 100 pixels (the smallest Android device screen
            // dimension I've seen is 480 pixels or so). Then we check
            // to ensure we are never more than 1/5 of the screen width.
        DisplayMetrics dm = new DisplayMetrics();
        act.getWindowManager().getDefaultDisplay().getMetrics(dm);
        tapPageMargin = (int)dm.xdpi;
        if (tapPageMargin < 100)
            tapPageMargin = 100;
        if (tapPageMargin > dm.widthPixels/5)
            tapPageMargin = dm.widthPixels/5;
        if (tapPageMargin > dm.heightPixels/5)
            tapPageMargin = dm.heightPixels/5;
    }

    public boolean onSingleTapUp(MotionEvent e)
        {
            if (mMode == Mode.Viewing && !tapDisabled) {
                MuPDFView pageView = (MuPDFView) getDisplayedView();
                if (pageView == null ) return super.onSingleTapUp(e);

                Hit item = pageView.passClickEvent(e.getX(), e.getY());
                onHit(item);
                
                LinkInfo link = null;
                if (mLinksEnabled && (item == Hit.LinkInternal || item == Hit.LinkExternal || item == Hit.LinkRemote) && (link = pageView.hitLink(e.getX(), e.getY())) != null)
                {
                    link.acceptVisitor(new LinkInfoVisitor() {
                            @Override
                            public void visitInternal(LinkInfoInternal li) {
                                    // Clicked on an internal (GoTo) link
                                setDisplayedViewIndex(li.pageNumber);
                            }                
                            @Override
                            public void visitExternal(LinkInfoExternal li) {
                                    //Clicked on an external link
                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri
                                                           .parse(li.url));
                                mContext.startActivity(intent);
                            }
                            @Override
                            public void visitRemote(LinkInfoRemote li) {
                                    // Clicked on a remote (GoToR) link
                            }
                        });
                }
                else if(item == Hit.Nothing)
                {
                    if (e.getX() > super.getWidth() - tapPageMargin || e.getY() > super.getHeight() - tapPageMargin) 
                        super.smartMoveForwards();
                    else if (e.getX() < tapPageMargin || e.getY() < tapPageMargin) 
                        super.smartMoveBackwards();
                    else
                        onTapMainDocArea();
                }
            }
            
            return super.onSingleTapUp(e);
        }
    

    @Override
    public boolean onDown(MotionEvent e) {

        return super.onDown(e);
    }

    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
                            float distanceY) {
        MuPDFView pageView = (MuPDFView)getDisplayedView();
        switch (mMode) {
            case Viewing:
                if (!tapDisabled) onDocMotion();
                return super.onScroll(e1, e2, distanceX, distanceY);
            case Selecting:
                if (pageView != null)
                {
                    boolean hadSelection = pageView.hasSelection();
                    pageView.selectText(e1.getX(), e1.getY(), e2.getX(), e2.getY());
                    if (hadSelection != pageView.hasSelection()) onSelectionStatusChanged();
                }
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                           float velocityY) {
        switch (mMode) {
            case Viewing:
                return super.onFling(e1, e2, velocityX, velocityY);
            default:
                return true;
        }
    }

    public boolean onScaleBegin(ScaleGestureDetector d) {
            // Disabled showing the buttons until next touch.
            // Not sure why this is needed, but without it
            // pinch zoom can make the buttons appear
        tapDisabled = true;
        return super.onScaleBegin(d);
    }

    public boolean onTouchEvent(MotionEvent event) {

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
        boolean useStylus = sharedPref.getBoolean(SettingsActivity.PREF_USE_STYLUS, false);
                
        int pointerIndexToUse = 0; // by default use the first pointer

            //if in stylus mode use stylus
        if (useStylus)
        {
            int pointerIndexOfStylus = -1; 
            for(int pointerIndex = 0; pointerIndex < event.getPointerCount(); pointerIndex++) {
                if (event.getToolType(pointerIndex) == android.view.MotionEvent.TOOL_TYPE_STYLUS) {
                    pointerIndexOfStylus = pointerIndex;
                    break; // we simply take the first stylus we find.
                }
            }
            pointerIndexToUse = pointerIndexOfStylus; // is pointer index of stylus or -1 if no stylus event occured
        }
            
        if ( mMode == Mode.Drawing )
        {
            if (event.getActionIndex() == pointerIndexToUse || !useStylus)
            {
                float x = event.getX(pointerIndexToUse);
                float y = event.getY(pointerIndexToUse);
                switch (event.getAction())
                {
                    case MotionEvent.ACTION_DOWN:
                        touch_start(x, y);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        touch_move(x, y);
                        break;
                    case MotionEvent.ACTION_UP:
                        touch_up();
                        break;
                }
            }
            if (useStylus) return true;
        }
                
        if ((event.getAction() & event.getActionMasked()) == MotionEvent.ACTION_DOWN)
        {
            tapDisabled = false;
        }

        return super.onTouchEvent(event);
    }

    private float mX, mY;

    private static final float TOUCH_TOLERANCE = 2;//Shoul be made customizable

    private void touch_start(float x, float y) {

        MuPDFView pageView = (MuPDFView)getDisplayedView();
        if (pageView != null)
        {
            pageView.startDraw(x, y);
        }
        mX = x;
        mY = y;
    }

    private void touch_move(float x, float y) {
        float dx = Math.abs(x - mX);
        float dy = Math.abs(y - mY);
        if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE)
        {
            MuPDFView pageView = (MuPDFView)getDisplayedView();
            if (pageView != null)
            {
                pageView.continueDraw(x, y);
            }
            mX = x;
            mY = y;
        }
    }

    private void touch_up() {
        MuPDFView pageView = (MuPDFView)getDisplayedView();
        if (pageView != null)
        {
            pageView.finishDraw();
            onNumberOfStrokesChanged(pageView.getDrawingSize());
        }
    }

    protected void onChildSetup(int i, View v) {
        if (SearchTaskResult.get() != null
            && SearchTaskResult.get().pageNumber == i)
            ((MuPDFView) v).setSearchBoxes(SearchTaskResult.get().searchBoxes);
        else
            ((MuPDFView) v).setSearchBoxes(null);

        ((MuPDFView) v).setLinkHighlighting(mLinksEnabled);

        ((MuPDFView) v).setChangeReporter(new Runnable() {
                public void run() {
                    applyToChildren(new ReaderView.ViewMapper() {
                            @Override
                            void applyToView(View view) {
                                ((MuPDFView) view).update();
                            }
                        });
                }
            });
    }

    protected void onMoveToChild(int i) {
        if (SearchTaskResult.get() != null && SearchTaskResult.get().pageNumber != i) {
            SearchTaskResult.set(null);
            resetupChildren();
        }
    }

    @Override
    protected void onMoveOffChild(int i) {
        View v = getView(i);
        if (v != null)
            ((MuPDFView)v).deselectAnnotation();
    }

    protected void onSettle(View v) {
            // When the layout has settled ask the page to render
            // in HQ
        ((MuPDFView) v).addHq(false);
    }

    protected void onUnsettle(View v) {
            // When something changes making the previous settled view
            // no longer appropriate, tell the page to remove HQ
        ((MuPDFView) v).removeHq();
    }

    @Override
    protected void onNotInUse(View v) {
        ((MuPDFView) v).releaseResources();
    }

    @Override
    protected void onScaleChild(View v, Float scale) {
        ((MuPDFView) v).setScale(scale);
    }
}
