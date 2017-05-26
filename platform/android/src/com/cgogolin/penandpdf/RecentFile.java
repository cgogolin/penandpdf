package com.cgogolin.penandpdf;

import android.net.Uri;

public class RecentFile {
    private String recentFileString;
    private long lastModified;
    private String displayName;
    
    public RecentFile(String recentFileString) {
        init(recentFileString);
    }

    public RecentFile(String recentFileString, String displayName, long lastModified) {
        init(recentFileString, displayName, lastModified);
    }

    protected void init(String recentFileString) {
        init(recentFileString, null, System.currentTimeMillis());
    }

    protected void init(String recentFileString, String displayName, long lastModified) {
        this.recentFileString = recentFileString;
        this.lastModified = lastModified;
        this.displayName = displayName;
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
}
