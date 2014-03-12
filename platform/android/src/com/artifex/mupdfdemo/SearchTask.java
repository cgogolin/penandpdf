package com.artifex.mupdfdemo;

import java.lang.Runnable;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.RectF;
import android.os.Handler;
import android.widget.Toast;
import android.util.Log;


class ProgressDialogX extends ProgressDialog {
    public ProgressDialogX(Context context) {
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

public abstract class SearchTask {
    private static final int SEARCH_PROGRESS_DELAY = 500;
    private final Context mContext;
    private final MuPDFCore mCore;
    private final Handler mHandler;
    private SearchTaskResult firstResult;
    private AsyncTask<Void,Integer,SearchTaskResult> mSearchTask;

    public SearchTask(Context context, MuPDFCore core) {
        mContext = context;
        mCore = core;
        mHandler = new Handler();
    }

    protected abstract void onTextFound(SearchTaskResult result);
    protected abstract void goToResult(SearchTaskResult result);

    public void stop() {
        if (mSearchTask != null) {
            mSearchTask.cancel(true);
            mSearchTask = null;
        }
        firstResult = null;
    }

    public void go(final String text, int direction, int displayPage) {
        if (mCore == null)
            return;
        stop();

        final int increment = direction;
//        final int startIndex = searchPage == -1 ? displayPage : searchPage + increment;
        final int startIndex = displayPage;

        final ProgressDialogX progressDialog = new ProgressDialogX(mContext);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setTitle(mContext.getString(R.string.searching_));
        progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    stop();
                }
            });
        progressDialog.setMax(mCore.countPages());

        mSearchTask = new AsyncTask<Void,Integer,SearchTaskResult>() {
            @Override
            protected SearchTaskResult doInBackground(Void... params) {
                int index = startIndex;
                while (0 <= index && index < mCore.countPages() && !isCancelled()) {
                    Log.v("SearchTask", "searching page " + index);
                    
                    publishProgress(index+1);
                    RectF searchHits[] = mCore.searchPage(index, text);

                    if (searchHits != null && searchHits.length > 0)
                    {
                        Log.v("SearchTask", searchHits.length+" hits fonud");
                        float pageHeight = mCore.getPageSize(index).y;
                        // for(RectF hit : searchHits) //Mirror the y coordinate
                        // {
                        //     hit.set(hit.left, pageHeight - hit.top, hit.right, pageHeight - hit.bottom);
                        // }
                        SearchTaskResult result = new SearchTaskResult(text, index, searchHits, increment);
                        if(increment == 1)
                            result.focusFirst();
                        else
                            result.focusLast();
                        onTextFound(result);
                        if(firstResult == null)
                        {
                            firstResult = result;
                            publishProgress(-1); //Hides the progress dialoge as soon as the first results are found
//                            goToResult(firstResult);
                        }
                    }
                    index += increment;
                }
                return firstResult;
//                return SearchTaskResult.get();
            }

            @Override
            protected void onPostExecute(SearchTaskResult result) {
                progressDialog.cancel();
                if (result == null) {
                    Toast.makeText(mContext, R.string.text_not_found, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            protected void onCancelled() {
                progressDialog.cancel();
            }

            @Override
            protected void onProgressUpdate(Integer... values) {
                if(values[0].intValue() == -1)
                {
                    progressDialog.dismiss();
                }
                else
                    progressDialog.setProgress(values[0].intValue());
            }

            // @Override
            // protected void onPreExecute() {
            //     super.onPreExecute();
            //     progressDialog.setProgress(startIndex);
            //     mHandler.postDelayed(new Runnable() {
            //             public void run() {
            //                 if(!(progressDialog.isCancelled() || progressDialog.isDismissed() ))
            //                 {
            //                     progressDialog.show();
            //                 }
            //             }
            //         }, SEARCH_PROGRESS_DELAY);
            // }
        };

        mSearchTask.execute();
    }
}
