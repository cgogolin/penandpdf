package com.cgogolin.penandpdf;

import android.graphics.RectF;

public class LinkInfoInternal extends LinkInfo {
    final public int pageNumber;
    final public int targetFlags; 
    final public RectF target;

        //Is to be keept in line with link.h
    static public int fz_link_flag_l_valid = 1; /* lt.x is valid */
    static public int fz_link_flag_t_valid = 2; /* lt.y is valid */
    static public int fz_link_flag_r_valid = 4; /* rb.x is valid */
    static public int fz_link_flag_b_valid = 8; /* rb.y is valid */
    static public int fz_link_flag_fit_h = 16; /* Fit horizontally */
    static public int fz_link_flag_fit_v = 32; /* Fit vertically */
    static public int fz_link_flag_r_is_zoom = 64; /* rb.x is actually a zoom figure */
    
    public LinkInfoInternal(float l, float t, float r, float b, int p, float ltx, float lty, float rbx, float rby, int flags) {
        super(l, t, r, b);
        pageNumber = p;
        targetFlags = flags;
        target = new RectF(ltx, lty, rbx, rby);
    }

    public void acceptVisitor(LinkInfoVisitor visitor) {
        visitor.visitInternal(this);
    }

    public LinkType type(){
        return LinkType.Internal;
    }
}
