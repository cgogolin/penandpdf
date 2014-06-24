package com.cgogolin.penandpdf;

import android.graphics.RectF;

public abstract class LinkInfo {
    enum LinkType {Internal, External, Remote};
    
    final public RectF rect;
    
    public LinkInfo(float l, float t, float r, float b) {
        rect = new RectF(l, t, r, b);
    }
    
    public void acceptVisitor(LinkInfoVisitor visitor) {
    }
    
    public abstract LinkType type();
}
