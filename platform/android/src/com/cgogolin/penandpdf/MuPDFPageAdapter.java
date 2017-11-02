package com.cgogolin.penandpdf;

import android.content.Context;
import android.graphics.Point;
import android.graphics.PointF;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.os.AsyncTask;

import android.util.Log;

public class MuPDFPageAdapter extends BaseAdapter {
    private final Context mContext;
    private final FilePicker.FilePickerSupport mFilePickerSupport;
    private final MuPDFCore mCore;
    private final SparseArray<PointF> mPageSizes = new SparseArray<PointF>();
    
    public MuPDFPageAdapter(Context c, FilePicker.FilePickerSupport filePickerSupport, MuPDFCore core) {
        mContext = c;
        mFilePickerSupport = filePickerSupport;
        mCore = core;
    }
    
    @Override
    public int getCount() {
        return mCore.countPages();
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }
    
    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        final MuPDFPageView pageView;
        if (convertView == null) {
            pageView = new MuPDFPageView(mContext, mFilePickerSupport, mCore, parent);
            
        } else {
            pageView = (MuPDFPageView) convertView;
        }
        
        PointF pageSize = mPageSizes.get(position);
        if (pageSize != null) {
                // We already know the page size. Set it up
                // immediately
            pageView.setPage(position, pageSize);
        } else {
                // Page size as yet unknown so find it out
            PointF size = mCore.getPageSize(position);
            mPageSizes.put(position, size);
            pageView.setPage(position, size);            
                // Warning: Page size must be known for measuring so 
                // we can't do this in background!!!
        }
        return pageView;
    }
}
