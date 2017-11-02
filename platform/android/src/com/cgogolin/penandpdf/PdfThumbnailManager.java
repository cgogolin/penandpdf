package com.cgogolin.penandpdf;

import android.content.Context;
import android.graphics.Bitmap;
import android.content.res.Resources;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Point;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import android.graphics.BitmapFactory;
import java.lang.Exception;
import android.util.Log;
import android.graphics.PointF;
import java.util.Random;

public class PdfThumbnailManager
{
    private PenAndPDFCore core = null;
    private Context context = null;
    private PointF size;
    
    public PdfThumbnailManager(Context context, PenAndPDFCore core) {
        this.core = core;
        this.context = context;
        size = core.getPageSize(0); //We have to do this here as generate() will be called on a background thread and then this can cause a segfault if the core hase been destroyed in the meantime.
    }
    
    public PdfThumbnailManager(Context context) {
        this.context = context;
    }

        /* This should only ever be called in a background process as
         * it can take a long time*/
    public String generate(int bmWidth, int bmHeight, MuPDFCore.Cookie cookie) {
        if(core==null || context==null) return null;
        Bitmap bm = Bitmap.createBitmap(bmWidth, bmHeight, Bitmap.Config.ARGB_8888);;
            /*If the core is destroyed drawPage() sometimes simply doesn't draw anything and this seems impossible to detect from just the coockie. As really bad hack around this problem we hide a random number in the first pixel and check if it has changed.*/
        Random random = new Random();
        int testPixelColor = random.nextInt();
        bm.setPixel(0,0,testPixelColor);
        testPixelColor=bm.getPixel(0,0);
        if(bm==null || cookie.aborted()) return null;
        core.drawPage(bm, 0, bmWidth, (int)(((float)bmWidth)/size.x*size.y), 0, 0, bmWidth, bmHeight, cookie);
        if(bm.getPixel(0,0) == testPixelColor) return null;
        
        File cacheDir = context.getCacheDir();
        File bitmapFile = null;
        FileOutputStream out = null;
        if(cookie.aborted()) return null;
        try {
            bitmapFile = File.createTempFile("thumbnail", ".png", cacheDir);
            out = new FileOutputStream(bitmapFile);
            bm.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if(!cookie.aborted())
            return bitmapFile.getName();
        else 
        {
            delete(bitmapFile.getName());
            return null;
        }
    }

        /* This should only ever be called in a background process as
         * it can take a long time*/
    public Bitmap get(String thumbnail) {
        File cacheDir = context.getCacheDir();
        File bitmapFile = new File(cacheDir, thumbnail);
        if(bitmapFile != null && bitmapFile.isFile()) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(bitmapFile.getPath(), options);
        }
        else
            return null;
    }

    
    public BitmapDrawable getDrawable(Resources res, String thumbnail) {
        if(res == null || thumbnail == null)
            return null;
        
        File cacheDir = context.getCacheDir();
        File bitmapFile = new File(cacheDir, thumbnail);

        if(bitmapFile != null && bitmapFile.isFile()) {
            return new BitmapDrawable(res, bitmapFile.getPath());
        }
        else
            return null;
    }
    
    
    public void delete(String thumbnail) {
        if(thumbnail==null)
            return;
        
        File cacheDir = context.getCacheDir();
        File bitmapFile = new File(cacheDir, thumbnail);

        if(bitmapFile!=null && bitmapFile.isFile())
            try
            {
                bitmapFile.delete();
            } catch (Exception e) {
                e.printStackTrace();
            }
    }
}
