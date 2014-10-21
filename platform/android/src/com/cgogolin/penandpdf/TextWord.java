package com.cgogolin.penandpdf;

import android.graphics.RectF;

public class TextWord extends RectF {
    public String w;

    public TextWord() {
        super();
        w = new String();
    }

    public void add(TextChar tc) {
        if(isEmpty())
            super.set(tc);
        else
            super.union(tc);
        w = w.concat(new String(new char[]{tc.c}));
    }

    public void add(TextWord tw) {
        if(isEmpty())
            super.set(tw);
        else
            super.union(tw);
        w = w.concat(tw.w);
    }
    

    public boolean intersects(TextWord wd) {
        return RectF.intersects(this,wd);
    }

    public boolean equals(TextWord wd) {
        return super.equals((RectF)wd) && w.equals(wd.w);
    }
}
