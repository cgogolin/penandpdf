package com.cgogolin.penandpdf;

import android.content.SharedPreferences;
import android.graphics.PointF;
import android.graphics.RectF;

enum Hit {Nothing, Widget, Annotation, Link, LinkInternal, LinkExternal, LinkRemote, Debug};

public interface MuPDFView {
    public void setPage(int page, PointF size);
    public void setScale(float scale);
    public Hit passClickEvent(float x, float y);
    public LinkInfo hitLink(float x, float y);
    public void selectText(float x0, float y0, float x1, float y1);
    public void deselectText();
    public boolean hasSelection();
    public boolean copySelection();
    public boolean markupSelection(Annotation.Type type);
    public void deleteSelectedAnnotation();
    public void setSearchResult(SearchResult searchTaskResult);
    public void setLinkHighlighting(boolean f);
    public void deselectAnnotation();
    public void startDraw(float x, float y);
    public void continueDraw(float x, float y);
    public void finishDraw();
    public void startErase(float x, float y);
    public void continueErase(float x, float y);
    public void finishErase(float x, float y);
    public void undoDraw();
    public void cancelDraw();
    public int getDrawingSize();
    public boolean saveDraw();
    public void setChangeReporter(Runnable reporter);
    public void update();
    public void addHq(boolean update);
    public void removeHq();
    public void releaseResources();
    public void releaseBitmaps();
}
