package com.cgogolin.penandpdf;

import android.net.Uri;

public class RecentFile extends Object {
    private String recentFileString;
    private long lastModified;
    private String displayName;
    private String bitmapString = null;

    
    public RecentFile(String recentFileString) {
        init(recentFileString, null, System.currentTimeMillis(), null);
    }

    public RecentFile(String recentFileString, String displayName, long lastModified) {
        init(recentFileString, displayName, lastModified, null);
    }

    public RecentFile(String recentFileString, String displayName, long lastModified, String bitmapString) {
        init(recentFileString, displayName, lastModified, bitmapString);
    }

    public RecentFile(RecentFile recentFile) {
        init(recentFile.getRecentFileString(), recentFile.getDisplayName(), recentFile.getLastModified(), recentFile.getThumbnailString());
    }
    
    protected void init(String recentFileString, String displayName, long lastModified, String bitmapString) {
        this.recentFileString = recentFileString;
        this.lastModified = lastModified;
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

    public long getLastModified() {
        return lastModified;
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
