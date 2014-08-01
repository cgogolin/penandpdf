package com.cgogolin.penandpdf;

import android.graphics.RectF;

public class SearchResult {
    private String text;
    private int   pageNumber;
    private RectF searchBoxes[];
    private int focus = -1;
    private int direction;
    
    public SearchResult(String text, int pageNumber, RectF[] searchBoxes, int direction) {
        this.text = text;
        this.pageNumber = pageNumber;
	this.searchBoxes = searchBoxes;
        this.direction = direction;
    }

    public String getText() {
        return text;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public RectF[] getSearchBoxes() {
        return searchBoxes;
    }

    public RectF getFocusedSearchBox() {
        if(focus >= 0 && focus < searchBoxes.length)
            return searchBoxes[focus];
        else
            return null;
    }
    
    public int getFocus() {
        return focus;
    }

    public void setFocus(int focus) {
        if(focus >= -1 && focus < searchBoxes.length)
            this.focus = focus;
    }

    public void focusFirst() {
        if(searchBoxes.length > 0)
            this.focus = 0;
    }

    public void focusLast() {
            this.focus = searchBoxes.length -1;
    }

    public boolean incrementFocus(int direction) {
        if(focus+direction >= 0 && focus+direction < searchBoxes.length)
        {
            focus+=direction;
            return true;
        }
        else
            return false;
    }
}
