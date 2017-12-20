package com.cgogolin.penandpdf;
import java.util.ArrayList;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap.Config;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.RectF;
import android.preference.PreferenceManager;
import android.util.Log;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.File;
import android.os.ParcelFileDescriptor;


public class MuPDFCore
{
    private static final float INK_THICKNESS=10f;
    private boolean mHasAdditionalChanges = false;

    private static boolean LIBRARY_LOADED=false;
		/* load our native library */
    static {
        try
        {
            System.loadLibrary("mupdf");
            LIBRARY_LOADED=true;
        }
        catch (UnsatisfiedLinkError e)
        {
            LIBRARY_LOADED=false;
        }
    }

	public static String cachDir;
	
	public static String getCacheDir() {
		return cachDir;
	}
	
		/* Readable members */
    private int numPages = -1;
    private boolean numPagesIsUpToDate = false;
    private float pageWidth;
    private float pageHeight;
    private long globals;
    private byte fileBuffer[];
    private String file_format;
    private String mPath = null;
    private String mFileName = null;
    
		/* The native functions */
    private static native boolean gprfSupportedInternal();
    private native long openFile(String filename);
    private native long openBuffer(String magic);
    private native String fileFormatInternal();
    private native boolean isUnencryptedPDFInternal();
    private native int countPagesInternal();
    private native void gotoPageInternal(int localActionPageNum);
    private native float getPageWidth();
    private native float getPageHeight();
    private native void drawPage(Bitmap bitmap,
								 int pageW, int pageH,
								 int patchX, int patchY,
								 int patchW, int patchH,
								 long cookiePtr);
	private native void updatePageInternal(Bitmap bitmap,
										   int page,
										   int pageW, int pageH,
										   int patchX, int patchY,
										   int patchW, int patchH,
										   long cookiePtr);
    private native RectF[] searchPage(String text);
    private native TextChar[][][][] text();
    private native byte[] textAsHtml();
    private native void addMarkupAnnotationInternal(PointF[] quadPoints, int type, String text);
    private native void addInkAnnotationInternal(PointF[][] arcs);
    private native void deleteAnnotationInternal(int annot_index);
    private native int passClickEventInternal(int page, float x, float y);
    private native void setFocusedWidgetChoiceSelectedInternal(String [] selected);
    private native String [] getFocusedWidgetChoiceSelected();
    private native String [] getFocusedWidgetChoiceOptions();
    private native int getFocusedWidgetSignatureState();
    private native String checkFocusedSignatureInternal();
    private native boolean signFocusedSignatureInternal(String keyFile, String password);
    private native int setFocusedWidgetTextInternal(String text);
    private native String getFocusedWidgetTextInternal();
    private native int getFocusedWidgetTypeInternal();
    private native LinkInfo [] getPageLinksInternal(int page);
    private native RectF[] getWidgetAreasInternal(int page);
    private native Annotation[] getAnnotationsInternal(int page);
    private native OutlineItem [] getOutlineInternal();
    private native boolean hasOutlineInternal();
    private native boolean needsPasswordInternal();
    private native boolean authenticatePasswordInternal(String password);
    private native MuPDFAlertInternal waitForAlertInternal();
    private native void replyToAlertInternal(MuPDFAlertInternal alert);
    private native void startAlertsInternal();
    private native void stopAlertsInternal();
    private native void destroying();
    private native boolean hasChangesInternal();
    private native int saveAsInternal(String path);
    private native int insertPageBeforeInternal(int position);
    private native long createCookie();
    private native void destroyCookie(long cookie);
    private native void abortCookie(long cookie);
    private native boolean cookieAborted(long cookie);

        /* making these non synchronized probably lead to a hard to debug crash in native code */
    public synchronized native void setInkThickness(float inkThickness);
    public synchronized native void setInkColor(float r, float g, float b);
    public synchronized native void setHighlightColor(float r, float g, float b);
    public synchronized native void setUnderlineColor(float r, float g, float b);
    public synchronized native void setStrikeoutColor(float r, float g, float b);
    public synchronized native void setTextAnnotIconColor(float r, float g, float b);
    public synchronized native int insertBlankPageBeforeInternal(int position);
	
	public synchronized native boolean javascriptSupported();

    public class Cookie
    {
        private final long cookiePtr;
        
        public Cookie()
            {
                cookiePtr = createCookie();
                if (cookiePtr == 0)
                    throw new OutOfMemoryError();
            }
        
        public void abort()
            {
                abortCookie(cookiePtr);
            }

        public boolean aborted()
            {
                return cookieAborted(cookiePtr);
            }
        
        public void destroy()
            {
                    // We could do this in finalize, but there's no guarantee that
                    // a finalize will occur before the muPDF context occurs.
                destroyCookie(cookiePtr);
			}
    }
    
    public MuPDFCore() //Hack to work around the fact that Java doesn't allow to call base class constructors later in the constructors of derived classes.
        {}
    
    public MuPDFCore(Context context, String path) throws Exception
        {
            if(!LIBRARY_LOADED)
                throw new Exception("Unable to load native library");
            init(context, path);
        }
    
    protected synchronized void init(Context context, String path) throws Exception
		{
			cachDir = context.getCacheDir().getAbsolutePath();
					
			if(path == null) throw new Exception(String.format(context.getString(R.string.cannot_open_file_Path), path));
                
            mPath = path;
            fileBuffer = null;
            int lastSlashPos = path.lastIndexOf('/');
            mFileName = new String(lastSlashPos == -1 ? path : path.substring(lastSlashPos+1));
            
            globals = openFile(path);
            if (globals == 0)
            {
                throw new Exception(String.format(context.getString(R.string.cannot_open_file_Path), path));
            }
            file_format = fileFormatInternal();
            if(file_format == null) throw new Exception(String.format(context.getString(R.string.cannot_interpret_file), path));
		}

    public MuPDFCore(Context context, byte buffer[], String fileName) throws Exception
        {
            init(context, buffer, fileName);
        }
    
    protected synchronized void init(Context context, byte buffer[], String fileName) throws Exception
		{
			cachDir = context.getCacheDir().getAbsolutePath();

            mPath = null;
            fileBuffer = buffer;
            mFileName = fileName;
            
            globals = openBuffer(fileName);
            if (globals == 0)
            {
                throw new Exception(context.getString(R.string.cannot_open_buffer));
            }
            file_format = fileFormatInternal();
            if(file_format == null) throw new Exception(String.format(context.getString(R.string.cannot_interpret_file), fileName));
		}

    public int countPages()
		{
            if (numPages < 0 || !numPagesIsUpToDate )
            {
                numPages = countPagesSynchronized();
                numPagesIsUpToDate = true;
            }
            return numPages;
		}

    public String fileFormat()
		{
            return file_format;
		}

    private synchronized int countPagesSynchronized() {
        return countPagesInternal();
    }

		/* Shim function */
    private synchronized void gotoPage(int page)
		{
            if (page > countPages()-1)
                page = countPages()-1;
            else if (page < 0)
                page = 0;
            gotoPageInternal(page);
            this.pageWidth = getPageWidth();
            this.pageHeight = getPageHeight();
		}

    public synchronized PointF getPageSize(int page) {
        gotoPage(page);
        return new PointF(pageWidth, pageHeight);
    }

    public MuPDFAlert waitForAlert() {
        MuPDFAlertInternal alert = waitForAlertInternal();
        return alert != null ? alert.toAlert() : null;
    }

    public void replyToAlert(MuPDFAlert alert) {
        replyToAlertInternal(new MuPDFAlertInternal(alert));
    }

    public synchronized void stopAlerts() {
        stopAlertsInternal();
    }

    public synchronized void startAlerts() {
        startAlertsInternal();
    }

    public synchronized void onDestroy() {
        stopAlerts();
        destroying();
        globals = 0;
    }

	public synchronized void drawPage(Bitmap bm, int page,
									  int pageW, int pageH,
									  int patchX, int patchY,
									  int patchW, int patchH,
									  MuPDFCore.Cookie cookie) {
        if(globals==0 || bm==null || cookie==null)
            return;
        
		gotoPage(page);
		drawPage(bm, pageW, pageH, patchX, patchY, patchW, patchH, cookie.cookiePtr);
	}

	public synchronized void updatePage(Bitmap bm, int page,
										int pageW, int pageH,
										int patchX, int patchY,
										int patchW, int patchH,
										MuPDFCore.Cookie cookie) {
        if(globals==0 || bm==null || cookie==null)
            return;
        
		updatePageInternal(bm, page, pageW, pageH, patchX, patchY, patchW, patchH, cookie.cookiePtr);
	}

    public synchronized PassClickResult passClickEvent(int page, float x, float y) {
        boolean changed = passClickEventInternal(page, x, y) != 0;

        switch (WidgetType.values()[getFocusedWidgetTypeInternal()])
        {
            case TEXT:
                return new PassClickResultText(changed, getFocusedWidgetTextInternal());
            case LISTBOX:
            case COMBOBOX:
                return new PassClickResultChoice(changed, getFocusedWidgetChoiceOptions(), getFocusedWidgetChoiceSelected());
            case SIGNATURE:
                return new PassClickResultSignature(changed, getFocusedWidgetSignatureState());
            default:
                return new PassClickResult(changed);
        }

    }

    public synchronized boolean setFocusedWidgetText(int page, String text) {
        boolean success;
        gotoPage(page);
        success = setFocusedWidgetTextInternal(text) != 0 ? true : false;

        return success;
    }

    public synchronized void setFocusedWidgetChoiceSelected(String [] selected) {
        setFocusedWidgetChoiceSelectedInternal(selected);
    }

    public synchronized String checkFocusedSignature() {
        return checkFocusedSignatureInternal();
    }

    public synchronized boolean signFocusedSignature(String keyFile, String password) {
        return signFocusedSignatureInternal(keyFile, password);
    }

    public synchronized LinkInfo [] getPageLinks(int page) {
        LinkInfo[] pageLinks = getPageLinksInternal(page);
        if(pageLinks == null) return null;
            // To flip the y cooridnate of all link targets to make coordiante system consistent with the link rect and coordinates of search results
        for (LinkInfo link: pageLinks)
            if(link.type() == LinkInfo.LinkType.Internal)
            {
                    //The 2*s don't make any sense, but for some reason they are necessary.
                ((LinkInfoInternal)link).target.left = 2*((LinkInfoInternal)link).target.left; 
                ((LinkInfoInternal)link).target.top = getPageSize(((LinkInfoInternal)link).pageNumber).y - 2*((LinkInfoInternal)link).target.top ;
                ((LinkInfoInternal)link).target.right = 2*((LinkInfoInternal)link).target.right;
                ((LinkInfoInternal)link).target.bottom = getPageSize(((LinkInfoInternal)link).pageNumber).y - 2*((LinkInfoInternal)link).target.bottom; 
            }
        return pageLinks;
    }

    public synchronized RectF [] getWidgetAreas(int page) {
        return getWidgetAreasInternal(page);
    }

    public synchronized Annotation [] getAnnoations(int page) {
        return getAnnotationsInternal(page);
    }

    public synchronized RectF [] searchPage(int page, String text) {
        gotoPage(page);
        return searchPage(text);
    }

    public synchronized byte[] html(int page) {
        gotoPage(page);
        return textAsHtml();
    }

    public synchronized TextWord [][] textLines(int page) {
        gotoPage(page);
        TextChar[][][][] chars = text();

            // The text of the page held in a hierarchy (blocks, lines, spans).
            // Currently we don't need to distinguish the blocks level or
            // the spans, and we need to collect the text into words.
        ArrayList<TextWord[]> lns = new ArrayList<TextWord[]>();

        for (TextChar[][][] bl: chars) {
            if (bl == null)
                continue;
            for (TextChar[][] ln: bl) {
                ArrayList<TextWord> wds = new ArrayList<TextWord>();
                TextWord wd = new TextWord();

                for (TextChar[] sp: ln) {
                    for (TextChar tc: sp) {
                        if(!Character.isLetter(tc.c))
                        {
                                //Non-letter characters start a new word, so add what we already have to wds
                            if (wd.w.length() > 0) {
                                wds.add(wd);
                                wd = new TextWord();
                            }
                        }
                        
                        wd.add(tc);
                        if(!Character.isLetter(tc.c))
                        {
                                //Non-letter characters go into a word on their own
                            wds.add(wd);
                            wd = new TextWord();
                        }
                    }
                }
                
                if (wd.w.length() > 0) {
                    wd.sort();
                    wds.add(wd);
                }
                
                if (wds.size() > 0)
                    lns.add(wds.toArray(new TextWord[wds.size()]));

                    //Some pdfs have strangely large character boxes, so we correct the bottom of the
                    //words in the previous line if they overlap with the current line and, the change is not too drastical (this is a heuristic!), and anything from the previous line actually intersects anything from the current line.
                if (lns.size() >= 2) {
                    TextWord line = new TextWord();
                    for(TextWord wd2 : lns.get(lns.size()-1)) {
                        line.add(wd2);
                    }
                        //Some pdfs have a strange line structure with makes it necessary to look further back than just one line, so we look at the few last lines (this is an ugly heuristic!)
                    for(int n = 2; lns.size()-n>=0 && n<=5; n++)
                    {
                        boolean anyIntersection = false;
                        for(TextWord wd3 : lns.get(lns.size()-n)) {
                            for(TextWord wd2 : lns.get(lns.size()-1)) {
                                if(wd3.intersects(wd2))
                                {
                                    anyIntersection = true;
                                    break;
                                }
                            }
                            if(anyIntersection) break;
                        }
                        if(anyIntersection)
                            for(TextWord wd3 : lns.get(lns.size()-n)) {
                                if(line.top > wd3.top && line.top < wd3.bottom && (wd3.bottom - line.top) / (wd3.bottom-wd3.top) < 0.45) {
                                    wd3.bottom = line.top;
                                }
                            }
                    }
                }
            }
        }
        
        return lns.toArray(new TextWord[lns.size()][]);
    }

    public synchronized void addTextAnnotation(int page, PointF[] rect, String text) {
        gotoPage(page);
        addMarkupAnnotationInternal(rect, Annotation.Type.TEXT.ordinal(), text);
    }
    
    public synchronized void addMarkupAnnotation(int page, PointF[] quadPoints, Annotation.Type type) {
        gotoPage(page);
        addMarkupAnnotationInternal(quadPoints, type.ordinal(),"");
    }

    public synchronized void addInkAnnotation(int page, PointF[][] arcs) {
        gotoPage(page);
        addInkAnnotationInternal(arcs);
    }

    public synchronized void deleteAnnotation(int page, int annot_index) {
        gotoPage(page);
        deleteAnnotationInternal(annot_index);
    }

    public synchronized boolean hasOutline() {
        return hasOutlineInternal();
    }

    public synchronized OutlineItem [] getOutline() {
        return getOutlineInternal();
    }

    public synchronized boolean needsPassword() {
        return needsPasswordInternal();
    }

    public synchronized boolean authenticatePassword(String password) {
        return authenticatePasswordInternal(password);
    }

    public synchronized boolean hasChanges() {
        return mHasAdditionalChanges || hasChangesInternal();
    }
    
    public String getPath() {
        return mPath;
    }

    public String getFileName() {
        return mFileName;
    }    

    
    public void onSharedPreferenceChanged(SharedPreferences sharedPref, String key){
            //Set ink thickness
        float inkThickness = Float.parseFloat(sharedPref.getString(SettingsActivity.PREF_INK_THICKNESS, Float.toString(INK_THICKNESS)));
        setInkThickness(inkThickness*0.5f);
            //Set colors
        int colorNumber;
        try {
            colorNumber = Integer.parseInt(sharedPref.getString(SettingsActivity.PREF_INK_COLOR, "0" ));
        } catch(NumberFormatException ex) {
            colorNumber = 0;
        }
        setInkColor(ColorPalette.getR(colorNumber), ColorPalette.getG(colorNumber), ColorPalette.getB(colorNumber));
        try {
            colorNumber = Integer.parseInt(sharedPref.getString(SettingsActivity.PREF_HIGHLIGHT_COLOR, "0" ));
        } catch(NumberFormatException ex) {
            colorNumber = 0;
        }
        setHighlightColor(ColorPalette.getR(colorNumber), ColorPalette.getG(colorNumber), ColorPalette.getB(colorNumber));
        try {
        colorNumber = Integer.parseInt(sharedPref.getString(SettingsActivity.PREF_UNDERLINE_COLOR, "0" ));
        } catch(NumberFormatException ex) {
            colorNumber = 0;
        }
        setUnderlineColor(ColorPalette.getR(colorNumber), ColorPalette.getG(colorNumber), ColorPalette.getB(colorNumber));
        try {
            colorNumber = Integer.parseInt(sharedPref.getString(SettingsActivity.PREF_STRIKEOUT_COLOR, "0" ));
        } catch(NumberFormatException ex) {
            colorNumber = 0;
        }
        setStrikeoutColor(ColorPalette.getR(colorNumber), ColorPalette.getG(colorNumber), ColorPalette.getB(colorNumber));
        try {
            colorNumber = Integer.parseInt(sharedPref.getString(SettingsActivity.PREF_TEXTANNOTICON_COLOR, "0" ));
        } catch(NumberFormatException ex) {
            colorNumber = 0;
        }
        setTextAnnotIconColor(ColorPalette.getR(colorNumber), ColorPalette.getG(colorNumber), ColorPalette.getB(colorNumber));
    }

    public synchronized boolean insertBlankPageAtEnd() {
        return insertBlankPageBefore(countPages());
    }
    
    public synchronized boolean insertBlankPageBefore(int position) {
        numPagesIsUpToDate = false;
        return insertBlankPageBeforeInternal(position) == 0 ? true : false;
    }

    public synchronized void relocate(String path, String fileName) {
        mPath = path;
        mFileName = fileName;
    }

    public synchronized void setHasAdditionalChanges(boolean hasAdditionalChanges) {
        mHasAdditionalChanges = hasAdditionalChanges;
    }
    
    public synchronized int saveAs(String path) {
        mHasAdditionalChanges = false;
        return saveAsInternal(path);
    }
}
