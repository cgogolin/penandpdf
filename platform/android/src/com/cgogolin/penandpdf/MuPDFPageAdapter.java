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
    private AsyncTask<MuPDFCore,Void,Void> getPageSizesTask;
    
    public MuPDFPageAdapter(Context c, FilePicker.FilePickerSupport filePickerSupport, MuPDFCore core) {
        mContext = c;
        mFilePickerSupport = filePickerSupport;
        mCore = core;

        if(mCore!=null)
        {   
            getPageSizesTask = new AsyncTask<MuPDFCore,Void,Void>(){
                    @Override
                    protected Void doInBackground(MuPDFCore... core) {
                        int numPages = getCount();
                        for(int position = 0; position < numPages; position++) {
                            PointF size = core[0].getPageSize(position);
                            mPageSizes.put(position, size);
                        }
                        return null;
                    }
                };
            getPageSizesTask.execute(mCore);
        }
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
                // we can't do this in background, but we try to fetch
                // all page sizes in the background when the adapter is created
        }
        return pageView;
    }
}
