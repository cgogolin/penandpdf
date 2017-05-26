package com.cgogolin.penandpdf;

import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class RecentFilesList extends LinkedList<RecentFile> implements List<RecentFile> { //Probably not the most appropriate list type...

    static final int MAX_RECENT_FILES=20;

    public RecentFilesList() {
        super();
    }
    
    public RecentFilesList(SharedPreferences prefs) {
        for (int i = MAX_RECENT_FILES-1; i>=0; i--) //Read in revers order because we use push
        {
            String recentFileString = prefs.getString("recentfile"+i, null);
            long recentFileLastModified = prefs.getLong("recentfile_lastModified"+i, 0l);
            String displayName = prefs.getString("recentfile_displayName"+i, null);
            if(recentFileString != null)
            {
                Uri recentFileUri = Uri.parse(recentFileString);
                    //Make sure we add only readable files
				File recentFile = new File(Uri.decode(recentFileUri.getEncodedPath()));
                if( (recentFile != null && recentFile.isFile() && recentFile.canRead()) || android.os.Build.VERSION.SDK_INT >= 23 ) {
                    push(new RecentFile(recentFileString, displayName, recentFileLastModified));
				}
            }
        }
    }

    void write(SharedPreferences.Editor edit) {
        for (int i = 0; i<size(); i++)
        {
            edit.putString("recentfile"+i, get(i).getFileString());
            edit.putLong("recentfile_lastModified"+i, get(i).getLastModified());
            edit.putString("recentfile_displayName"+i, get(i).getDisplayName());
        }
    }


    public void push(String recentFileString) {
        push(new RecentFile(recentFileString));
    }
    
    
    @Override
    public void push(RecentFile recentFile) {
            //Make sure we don't put duplicates
        while(remove(recentFile)) {};
            //Add
        super.addFirst(recentFile);
            //Remove elements until lenght is short enough
        while (size() > MAX_RECENT_FILES) { removeLast(); }
    }
}
