package com.cgogolin.penandpdf;

import android.graphics.PointF;
import android.graphics.RectF;

public class Annotation extends RectF {
    enum Type {
        TEXT, LINK, FREETEXT, LINE, SQUARE, CIRCLE, POLYGON, POLYLINE, HIGHLIGHT,
            UNDERLINE, SQUIGGLY, STRIKEOUT, STAMP, CARET, INK, POPUP, FILEATTACHMENT,
            SOUND, MOVIE, WIDGET, SCREEN, PRINTERMARK, TRAPNET, WATERMARK, A3D, UNKNOWN
            }

    public final Type type;
    public final PointF[][] arcs;
    public String text;

    public Annotation(float x0, float y0, float x1, float y1, Type type, PointF[][] arcs, String text) {
        super(x0, y0, x1, y1);
        this.type = type;
        this.arcs = arcs;
        this.text = text;
    }
    
        //This is for convenience in mupdf.c
    public Annotation(float x0, float y0, float x1, float y1, int type, PointF[][] arcs, String text) {
        this(x0, y0, x1, y1, type == -1 ? Type.UNKNOWN : Type.values()[type], arcs, text);
    }
}
