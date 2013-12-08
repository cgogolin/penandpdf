package com.artifex.mupdfdemo;

import java.util.LinkedList;
import java.util.ArrayList;
import android.content.SharedPreferences;
import java.io.File;

public class RecentFilesList extends LinkedList<String> { //Probably not the mode appropriate list type...

    static final int MAX_RECENT_FILES=10;
    
    public RecentFilesList(SharedPreferences prefs) {
        for (int i = 0; i<MAX_RECENT_FILES; i++)
        {
            String recentFileString = prefs.getString("recentfile"+i,null);
            if(recentFileString != null)
            {
                    //Make sure we add only actual files
//                File recentFile = new File(recentFileString);
//                if(recentFile != null && recentFile.isFile() && recentFile.canRead()) push(recentFileString);
//                if(recentFile != null)
                    push(recentFileString);
            }
        }
    }

    void write(SharedPreferences.Editor edit) {
        for (int i = 0; i<size(); i++)
        {
            edit.putString("recentfile"+i, get(i));
        }
    }
        
    @Override
    public void push(String recentFileString) {
//        if(!contains(recentFileString))
            //Make sure we don't put duplicates
        remove(recentFileString);
            //Add
        super.push(recentFileString);
            //Remove elements until lenght is short enough
        while (size() > MAX_RECENT_FILES) { removeLast(); }
    }
}
