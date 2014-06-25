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
                // Page size as yet unknown so find out find the size
            PointF size = mCore.getPageSize(position);
            mPageSizes.put(position, size);
            pageView.setPage(position, size);            
                // Warning: Page size must be known for measuring so 
                // we can't do this in background!!!
                // Page size as yet unknown. Blank it for now, and
                // start a background task to find the size
            // pageView.setBlankPage(position);
            // AsyncTask<Void,Void,PointF> sizingTask = new AsyncTask<Void,Void,PointF>() {
            //     @Override
            //     protected PointF doInBackground(Void... arg0) {
            //         return mCore.getPageSize(position);
            //     }
                
            //     @Override
            //     protected void onPostExecute(PointF result) {
            //         super.onPostExecute(result);
            //             // We now know the page size
            //         mPageSizes.put(position, result);
            //             // Check that this view hasn't been reused for
            //             // another page since we started
            //         if (pageView.getPage() == position)
            //             pageView.setPage(position, result);
            //     }
            // };
            
            // sizingTask.execute((Void)null);
        }
        return pageView;
    }
    
        //Usually we should here notify the associated view to reload its data, but as we never need to call this function and hence do not even keep a reference to our view we just don't do anything
    // @Override
    // public void notifyDataSetChanged() {
    // }
}
