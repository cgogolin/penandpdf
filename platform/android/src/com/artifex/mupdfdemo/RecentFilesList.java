package com.artifex.mupdfdemo;

import java.util.LinkedList;
import java.util.ArrayList;

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

//     public ArrayList<String> toArrayList() {
// //        return (new ArrayList()).addAll((toArray(new String[size()]))); //This is horrible ...
//         return (new ArrayList()).addAll(this); //This is horrible ...
//     }
}
