package com.cgogolin.penandpdf;
import android.print.PrintDocumentAdapter;
//import android.support.v7.app.AppCompatActivity;
import android.app.Activity;
import android.os.ParcelFileDescriptor;
import java.io.FileInputStream;
import android.net.Uri;
import android.os.Bundle;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.os.CancellationSignal;
import android.print.PrintDocumentInfo;

public class PenAndPDFPrintDocumentAdapter extends PrintDocumentAdapter {
    private PenAndPDFCore core = null;
    private Activity activity = null;
    private String jobName = null;

    public PenAndPDFPrintDocumentAdapter(Activity activity, PenAndPDFCore core, String jobName) {
        this.core = core;
        this.activity = activity;
        this.jobName = jobName;
    }

    @Override
    public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes, CancellationSignal cancellationSignal, PrintDocumentAdapter.LayoutResultCallback callback, Bundle extras) {

        PrintDocumentInfo info = new PrintDocumentInfo
            .Builder(jobName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(core.countPages())
            .build();

        if (cancellationSignal.isCanceled() ) {
            callback.onLayoutCancelled();
            return;
        }
        callback.onLayoutFinished(info, false);
    }

    @Override
    public void onWrite(PageRange[] pages,
                        ParcelFileDescriptor destination,
                        CancellationSignal cancellationSignal,
                        PrintDocumentAdapter.WriteResultCallback callback) {
            //TODO: pages are so far not take into account
        try
        {
            Uri tmp_file_uri = core.export(activity);
            ParcelFileDescriptor tmp_file_pfd = activity.getContentResolver().openFileDescriptor(tmp_file_uri, "r");
            FileInputStream is = new FileInputStream(tmp_file_pfd.getFileDescriptor());
            ParcelFileDescriptor.AutoCloseOutputStream os = new ParcelFileDescriptor.AutoCloseOutputStream(destination);
            byte buffer[] = new byte[1024];
            int num = 0;
            int len = 0;
            while((num = is.read(buffer)) != -1) {
                os.write(buffer, 0, num);
                len += num;
            }
            is.close();
            tmp_file_pfd.close();
            os.close();//destination is closed automatically here

            PageRange[] range = {new PageRange(1, core.countPages())};
            callback.onWriteFinished(range);
                //callback.onWriteFinished(pages);
                //callback.onWriteFailed("Job "+jobName+" failed after exporting to "+tmp_file_uri+" for range "+pages+" and then copying "+len+" bytes to destination ");
        }
        catch(Exception e)
        {
            callback.onWriteFailed(e.getMessage());
        }
    }
}
