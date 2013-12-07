package com.artifex.mupdfdemo;

import android.app.Fragment;
import android.view.View;
import android.view.ViewGroup;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.ListView;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.Context;
import android.widget.ArrayAdapter;
import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

//public static class SettingsFragment extends PreferenceFragment {
public class RecentFilesFragment extends Fragment {

//    Activity mActivity;
    
    RecentFilesFragment(){
        super();
//        mActivity = activity;
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.recentfiles, container, false);
        ListView listView = (ListView)view.findViewById(R.id.listview);
        
            //Read the recent files list from preferences
        SharedPreferences prefs = getActivity().getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
        RecentFilesList recentFilesList = new RecentFilesList(RecentFilesList.MAX_RECENT_FILES);
        for (int i = 0; i<RecentFilesList.MAX_RECENT_FILES; i++)
        {
            String recentPath = prefs.getString("recentfile"+i,null);
            if(recentPath != null) recentFilesList.push(recentPath);
        }
        
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1, recentFilesList.toArray(new String[recentFilesList.size()]));
        listView.setAdapter(arrayAdapter);
        
        return view;
    }

    
    // public void onSharedPreferenceChanged(SharedPreferences sharedPref, String key) {
        
    // }
}
