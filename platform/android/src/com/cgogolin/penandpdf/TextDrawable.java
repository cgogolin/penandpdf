package com.cgogolin.penandpdf;

//Code mostly taken from http://stackoverflow.com/questions/19788386/set-unchangeable-some-part-of-edittext-android/19789317#19789317 and slightly edited

import android.graphics.drawable.Drawable;
import android.graphics.Paint;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import java.lang.Math;


public class TextDrawable extends Drawable {

    private final String text;
    private final Paint paint;

    public TextDrawable(String text, float textSize, int color) {
        this.text = text;
        this.paint = new Paint();
        paint.setColor(color);
        paint.setTextSize(textSize);
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawText(text, 0, 6, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        paint.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return (int)Math.ceil(paint.measureText(text));
    }
}
