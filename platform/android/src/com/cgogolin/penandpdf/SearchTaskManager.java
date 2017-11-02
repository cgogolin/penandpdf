package com.cgogolin.penandpdf;

import java.lang.Runnable;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.os.Handler;
import android.widget.Toast;
import android.util.Log;


class SearchProgressDialog extends ProgressDialog {
    public SearchProgressDialog(Context context) {
        super(context);
    }

    private boolean mCancelled = false;
    private boolean mDismissed = false;

    public boolean isCancelled() {
        return mCancelled;
    }

    public boolean isDismissed() {
        return mDismissed;
    }

    @Override
    public void cancel() {
        mCancelled = true;
        super.cancel();
    }

    @Override
    public void dismiss() {
        mDismissed = true;
        super.dismiss();
    }
}
                      
public abstract class SearchTaskManager {
    private static final int SEARCH_PROGRESS_DELAY = 1000;
    protected final Context mContext;
    private final MuPDFCore mCore;
    private final Handler mHandler;
    private AsyncTask<Void,Integer,SearchResult> mSearchTask;
    
    public SearchTaskManager(Context context, MuPDFCore core) {
        mContext = context;
        mCore = core;
        mHandler = new Handler();
    }

    protected abstract void onTextFound(SearchResult result);
    protected abstract void goToResult(SearchResult result);
    
    public void start(final String text, int direction, int displayPage) {
        if (mCore == null)
            return;
        stop();

        final int increment = direction;
        final int startIndex = displayPage;

        final SearchProgressDialog progressDialog = new SearchProgressDialog(mContext);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setProgressPercentFormat(null);
        progressDialog.setTitle(mContext.getString(R.string.searching_));
        progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    stop();
                }
            });
        progressDialog.setMax(mCore.countPages());

        mSearchTask = new AsyncTask<Void,Integer,SearchResult>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                progressDialog.setProgress(startIndex);
                mHandler.postDelayed(new Runnable() {
                        public void run() {
                            if(!(progressDialog.isCancelled() || progressDialog.isDismissed() ))
                            {
                                progressDialog.show();
                            }
                        }
                    }, SEARCH_PROGRESS_DELAY);
            }
            
            @Override
            protected SearchResult doInBackground(Void... params) {
                SearchResult firstResult = null;
                int index = startIndex;
                do
                {
                    publishProgress(index+1);
                    RectF searchHits[] = mCore.searchPage(index, text);
                    if (searchHits != null && searchHits.length > 0)
                    {
                        final SearchResult result = new SearchResult(text, index, searchHits, increment);
                        if(increment == 1)
                            result.focusFirst();
                        else
                            result.focusLast();
                        onTextFound(result);
                        if(firstResult == null)
                        {
                            firstResult = result;
                            mHandler.post(new Runnable() 
                                {
                                    @Override
                                    public void run() {
                                       progressDialog.dismiss();
                                       goToResult(result);
                                    }
                                }
                                );
                        }
                    }
                    index = (index+increment % mCore.countPages() + mCore.countPages()) % mCore.countPages();
                }
                while(index != (startIndex % mCore.countPages() + mCore.countPages()) % mCore.countPages() && !isCancelled());
                    
                return firstResult;
            }

            @Override
            protected void onPostExecute(SearchResult result) {
                super.onPostExecute(result);
                progressDialog.cancel();
                if(result == null) Toast.makeText(mContext, R.string.text_not_found, Toast.LENGTH_SHORT).show();
            }

            @Override
            protected void onCancelled() {
                super.onCancelled();
                progressDialog.cancel();
            }

            @Override
            protected void onProgressUpdate(Integer... page) {
                super.onProgressUpdate(page);
                progressDialog.setProgress(page[0]);
            }
        };

        mSearchTask.execute();
    }

    public void stop() {
        if (mSearchTask != null) {
            mSearchTask.cancel(true);
            mSearchTask = null;
        }
    }

}
