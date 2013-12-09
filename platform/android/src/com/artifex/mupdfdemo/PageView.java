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
	}
}

public abstract class PageView extends ViewGroup {
//        public static final int HIGHLIGHT_COLOR = 0x802572AC;
//        public static final int HIGHLIGHT_COLOR = 0x4033B5E5;
        public static final int HIGHLIGHT_COLOR = 0x8033B5E5;
        public static final int SEARCHRESULTS_COLOR = 0x4033B5E5;
//        public static final int LINK_COLOR = 0x60C7C7C7;
        public static final int LINK_COLOR = 0xFF33B5E5;
//        public static final int BOX_COLOR = 0xFF4444FF;
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
	private       RectF     mSearchBoxes[];
	protected     LinkInfo  mLinks[];
	private       RectF     mSelectBox;
	private       TextWord  mText[][];
	private       RectF     mItemSelectBox;
	protected     ArrayList<ArrayList<PointF>> mDrawing;
	private       View      mSearchView;
	private       boolean   mIsBlank;
	private       boolean   mHighlightLinks;

	private       ProgressBar mBusyIndicator;
	private final Handler   mHandler = new Handler();

    private float inkThickness = 10;
    private int inkColor = 0x80AC7225;
    private int highlightColor = 0x80AC7225;
    private int underlineColor = 0x80AC7225;
    private int strikeoutColor = 0x80AC7225;

        //New
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

		mSearchBoxes = null;
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

                    //Set ink thickness and colors for PageView
                SharedPreferences sharedPref = getContext().getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
                inkThickness = Float.parseFloat(sharedPref.getString(SettingsActivity.PREF_INK_THICKNESS, Float.toString(inkThickness)));
                inkColor = ColorPalette.getHex(Integer.parseInt(sharedPref.getString(SettingsActivity.PREF_INK_COLOR, Integer.toString(inkColor))));
                highlightColor = ColorPalette.getHex(Integer.parseInt(sharedPref.getString(SettingsActivity.PREF_HIGHLIGHT_COLOR, Integer.toString(highlightColor))));
                underlineColor = ColorPalette.getHex(Integer.parseInt(sharedPref.getString(SettingsActivity.PREF_UNDERLINE_COLOR, Integer.toString(underlineColor))));
                strikeoutColor = ColorPalette.getHex(Integer.parseInt(sharedPref.getString(SettingsActivity.PREF_STRIKEOUT_COLOR, Integer.toString(strikeoutColor))));
                
		mIsBlank = false;
		// Highlights may be missing because mIsBlank was true on last draw
		if (mSearchView != null)
			mSearchView.invalidate();

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
				if (mSearchView != null)
					mSearchView.invalidate();
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

		if (mSearchView == null) {
			mSearchView = new View(mContext) {
				@Override
				protected void onDraw(final Canvas canvas) {
					super.onDraw(canvas);
					// Work out current total scale factor
					// from source to view
					final float scale = mSourceScale*(float)getWidth()/(float)mSize.x;
					final Paint paint = new Paint();

					if (!mIsBlank && mSearchBoxes != null) {
						paint.setColor(SEARCHRESULTS_COLOR);
						for (RectF rect : mSearchBoxes)
							canvas.drawRect(rect.left*scale, rect.top*scale,
									        rect.right*scale, rect.bottom*scale,
									        paint);
					}

					if (!mIsBlank && mLinks != null && mHighlightLinks) {
                                            paint.setStyle(Paint.Style.STROKE);
						paint.setColor(LINK_COLOR);
						for (LinkInfo link : mLinks)
							canvas.drawRect(link.rect.left*scale, link.rect.top*scale,
									        link.rect.right*scale, link.rect.bottom*scale,
									        paint);
					}

					if (mSelectBox != null && mText != null) {
                                            paint.setStyle(Paint.Style.FILL);
                                            paint.setColor(HIGHLIGHT_COLOR);
                                            processSelectedText(new TextProcessor() {
							RectF rect;

							public void onStartLine() {
								rect = new RectF();
							}

							public void onWord(TextWord word) {
								rect.union(word);
							}

							public void onEndLine() {
								if (!rect.isEmpty())
									canvas.drawRect(rect.left*scale, rect.top*scale, rect.right*scale, rect.bottom*scale, paint);
							}
						});
					}

					if (mItemSelectBox != null) {
						paint.setStyle(Paint.Style.STROKE);
						paint.setColor(BOX_COLOR);
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

						paint.setAntiAlias(true);
						paint.setDither(true);
						paint.setStrokeJoin(Paint.Join.ROUND);
						paint.setStrokeCap(Paint.Cap.ROUND);

						paint.setStyle(Paint.Style.STROKE);
                                                paint.setStrokeWidth(inkThickness * scale);
                                                paint.setColor(inkColor);

						canvas.drawPath(path, paint);
					}
				}
			};

			addView(mSearchView);
		}
		requestLayout();
	}

	public void setSearchBoxes(RectF searchBoxes[]) {
		mSearchBoxes = searchBoxes;
		if (mSearchView != null)
			mSearchView.invalidate();
	}

	public void setLinkHighlighting(boolean f) {
		mHighlightLinks = f;
		if (mSearchView != null)
			mSearchView.invalidate();
	}

	public void deselectText() {
                //New
            docRelXmax = Float.NEGATIVE_INFINITY;
            docRelXmin = Float.POSITIVE_INFINITY;
            
		mSelectBox = null;
		mSearchView.invalidate();
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

                    //New
                if(Math.max(docRelX0,docRelX1)>docRelXmax) docRelXmax = Math.max(docRelX0,docRelX1);
                if(Math.min(docRelX0,docRelX1)<docRelXmin) docRelXmin = Math.min(docRelX0,docRelX1);
                
		// Order on Y but maintain the point grouping
		if (docRelY0 <= docRelY1)
			mSelectBox = new RectF(docRelX0, docRelY0, docRelX1, docRelY1);
		else
			mSelectBox = new RectF(docRelX1, docRelY1, docRelX0, docRelY0);

		mSearchView.invalidate();

		if (mGetText == null) {
			mGetText = new AsyncTask<Void,Void,TextWord[][]>() {
				@Override
				protected TextWord[][] doInBackground(Void... params) {
					return getText();
				}
				@Override
				protected void onPostExecute(TextWord[][] result) {
					mText = result;
					mSearchView.invalidate();
				}
			};

			mGetText.execute();
		}
	}

	public void startDraw(float x, float y) {
                    //Set ink thickness and color
                SharedPreferences sharedPref = getContext().getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
                inkThickness = Float.parseFloat(sharedPref.getString(SettingsActivity.PREF_INK_THICKNESS, Float.toString(inkThickness)));
                inkColor = ColorPalette.getHex(Integer.parseInt(sharedPref.getString(SettingsActivity.PREF_INK_COLOR, Integer.toString(inkColor))));            
		float scale = mSourceScale*(float)getWidth()/(float)mSize.x;
		float docRelX = (x - getLeft())/scale;
		float docRelY = (y - getTop())/scale;
		if (mDrawing == null)
                    mDrawing = new ArrayList<ArrayList<PointF>>();
                

		ArrayList<PointF> arc = new ArrayList<PointF>();
		arc.add(new PointF(docRelX, docRelY));
		mDrawing.add(arc);
	}

	public void continueDraw(float x, float y) {
		float scale = mSourceScale*(float)getWidth()/(float)mSize.x;
		float docRelX = (x - getLeft())/scale;
		float docRelY = (y - getTop())/scale;

		if (mDrawing != null && mDrawing.size() > 0) {
                    ArrayList<PointF> arc = mDrawing.get(mDrawing.size() - 1);
                    arc.add(new PointF(docRelX, docRelY));
                    mSearchView.invalidate();
		}
	}

    public void finishDraw() {
	if (mDrawing != null && mDrawing.size() > 0) {
            ArrayList<PointF> arc = mDrawing.get(mDrawing.size() - 1);
            PointF lastArc = arc.get(0);
            float scale = mSourceScale*(float)getWidth()/(float)mSize.x;
            if(arc.size() == 1) {
                arc.add(new PointF(lastArc.x+0.5f*inkThickness,lastArc.y));
                arc.add(new PointF(lastArc.x+0.5f*inkThickness,lastArc.y+0.5f*inkThickness));
                arc.add(new PointF(lastArc.x,lastArc.y+0.5f*inkThickness));
                arc.add(lastArc);
                arc.add(new PointF(lastArc.x+0.5f*inkThickness,lastArc.y));
            }
            mSearchView.invalidate();
        }
    }


    public void undoDraw() {
        if (mDrawing == null || mDrawing.size() == 0) return;
        mDrawing.remove(mDrawing.size()-1);
        mSearchView.invalidate();
    }
    

    public void cancelDraw() {
        mDrawing = null;
        mSearchView.invalidate();
    }

    public int getDrawingSize() {
        return mDrawing.size();
    }
    
    protected PointF[][] getDraw() { //This is where ink drawn stuff processed 
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
            SharedPreferences sharedPref = getContext().getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
            boolean useSmartTextSelection = sharedPref.getBoolean(SettingsActivity.PREF_SMART_TEXT_SELECTION, true);
            if (useSmartTextSelection)
                (new TextSelector(mText, mSelectBox,docRelXmin,docRelXmax)).select(tp);
            else
                (new TextSelector(mText, mSelectBox)).select(tp);
	}

	public void setItemSelectBox(RectF rect) {
		mItemSelectBox = rect;
		if (mSearchView != null)
			mSearchView.invalidate();
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

		if (mSearchView != null) {
			mSearchView.layout(0, 0, w, h);
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
				mSearchView.bringToFront();
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

    // public void setInkThickness(float inkThickness){
    //     this.inkThickness = inkThickness;
    // }
    // public void setinkColor(int inkColor){
    //     this.inkColor = inkColor;    
    // }
    // public void setHighlightColor(int highlightColor){
    //     this.highlightColor = highlightColor;
    // }
    // public void setUnderlineColor(int underlineColor){
    //     this.underlineColor = underlineColor;
    // }
    // public void setStrikeoutColor(int strikeoutColor){
    //     this.strikeoutColor = strikeoutColor;
    // }
}
