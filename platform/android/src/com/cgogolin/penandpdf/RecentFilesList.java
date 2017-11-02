package com.cgogolin.penandpdf;

import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import android.content.Context;
import android.util.Log;

public class RecentFilesList extends LinkedList<RecentFile> implements List<RecentFile> { //Probably not the most appropriate list type...

    static final int MAX_RECENT_FILES=100;

    private Context context = null;
    
    public RecentFilesList() {
        super();
    }
    
    public RecentFilesList(Context context, SharedPreferences prefs) {
        this.context = context;
        
        for (int i = MAX_RECENT_FILES-1; i>=0; i--) //Read in reverse order because we use push
        {
            String recentFileString = prefs.getString("recentfile"+i, null);
            long recentFileLastModified = prefs.getLong("recentfile_lastModified"+i, 0l);
            String displayName = prefs.getString("recentfile_displayName"+i, null);
            String bitmapString = prefs.getString("recentfile_thumbnailString"+i, null);
            
            if(recentFileString != null)
            {
                Uri recentFileUri = Uri.parse(recentFileString);
                if( android.os.Build.VERSION.SDK_INT < 19 ) 
                {
                        //Make sure we add only readable files
                    File recentFile = new File(Uri.decode(recentFileUri.getEncodedPath()));
                    if(recentFile != null && recentFile.isFile() && recentFile.canRead())
                        push(new RecentFile(recentFileString, displayName, recentFileLastModified, bitmapString));
                } else {
                    push(new RecentFile(recentFileString, displayName, recentFileLastModified, bitmapString));
                }
            }
        }
    }

    void write(SharedPreferences.Editor edit) {
        for (int i = 0; i<size(); i++)
        {
            edit.putString("recentfile"+i, get(i).getFileString());
            edit.putLong("recentfile_lastModified"+i, get(i).getLastOpened());
            edit.putString("recentfile_displayName"+i, get(i).getDisplayName());
            edit.putString("recentfile_thumbnailString"+i, get(i).getThumbnailString());
        }
    }


    public void push(String recentFileString) {
        push(new RecentFile(recentFileString));
    }
    
    
    @Override
    public void push(RecentFile recentFile) {
            //Make sure we don't put duplicates
        PdfThumbnailManager pdfThumbnailManager = new PdfThumbnailManager(context);
        int index = -1;
        while((index=indexOf(recentFile))!=-1) {
            RecentFile recentFileToRemove = super.remove(index);
            if(recentFile.getThumbnailString() == null)
                recentFile.setThumbnailString(recentFileToRemove.getThumbnailString());
            else
                pdfThumbnailManager.delete(recentFileToRemove.getThumbnailString());
        };
            //Add
        super.addFirst(recentFile);
            //Remove elements until lenght is short enough
        while (size() > MAX_RECENT_FILES) {
            pdfThumbnailManager.delete(removeLast().getThumbnailString());
        }
    }

    public String[] toStringArray() {
        String[] array = new String[size()];
        for (int i = 0; i<size(); i++)
        {
            array[i] = get(i).getFileString();
        }
        return array;
    }
}
