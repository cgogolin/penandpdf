package com.cgogolin.penandpdf;

import android.net.Uri;

public class RecentFile extends Object {
    private String recentFileString;
    private long lastOpened;
    private String displayName;
    private String bitmapString = null;

    
    public RecentFile(String recentFileString) {
        init(recentFileString, null, System.currentTimeMillis(), null);
    }

    public RecentFile(String recentFileString, String displayName) {
        init(recentFileString, displayName, System.currentTimeMillis(), null);
    }

    public RecentFile(String recentFileString, String displayName, long lastOpened) {
        init(recentFileString, displayName, lastOpened, null);
    }

    public RecentFile(String recentFileString, String displayName, long lastOpened, String bitmapString) {
        init(recentFileString, displayName, lastOpened, bitmapString);
    }

    public RecentFile(RecentFile recentFile) {
        init(recentFile.getRecentFileString(), recentFile.getDisplayName(), recentFile.getLastOpened(), recentFile.getThumbnailString());
    }
    
    protected void init(String recentFileString, String displayName, long lastOpened, String bitmapString) {
        this.recentFileString = recentFileString;
        this.lastOpened = lastOpened;
        this.displayName = displayName;
        this.bitmapString = bitmapString;
    }

    public String getRecentFileString() {
        return recentFileString;
    }
    
    public Uri getUri() {
        return Uri.parse(recentFileString);
    }
    
    public String getFileString() {
        return recentFileString;
    }

    public long getLastOpened() {
        return lastOpened;
    }

    public String getDisplayName() {
        return displayName;
    }

    
    @Override
    public boolean equals(Object obj) {
        if (obj== null || !(obj instanceof RecentFile))
            return false;
        if (obj == this)
            return true;
        
        RecentFile recentFile = (RecentFile)obj;
        return recentFile.getFileString().equals(getFileString());
    }

    @Override
    public int hashCode() {
        return getFileString().hashCode();
    }

    public void setThumbnailString(String bitmapString) {
        this.bitmapString = bitmapString;
    }

    public String getThumbnailString() {
        return bitmapString;
    }
}
