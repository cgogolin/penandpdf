package com.artifex.mupdfdemo;

import java.util.LinkedList;

public class RecentFilesList extends LinkedList<String> {

    static final int MAX_RECENT_FILES=10;
    
    private final int limit;
    
    public RecentFilesList(int limit) {
        this.limit = limit;
    }
        
    @Override
     public boolean add(String o) {
        super.add(o);
        while (size() > limit) { super.remove(); }
        return true;
    }
}
