package com.artifex.mupdfdemo;

import android.graphics.Color;
import android.app.Fragment;
import android.app.ListFragment;
import android.view.View;
import android.view.ViewGroup;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.ListView;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.Context;
import android.widget.ArrayAdapter;
import android.app.Activity;
import android.content.Context;
import android.widget.Toast;
import android.widget.TextView;
import android.widget.ImageView;
import android.net.Uri;
import android.content.Intent;

//public static class SettingsFragment extends PreferenceFragment {
public class RecentFilesFragment extends ListFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    private ArrayAdapter<String> mRecentFilesAdapter;
    private LayoutInflater mInflater;
    private Purpose      mPurpose;
    
    RecentFilesFragment(Intent intent){
        super();

        if (Intent.ACTION_MAIN.equals(intent.getAction())) 
            mPurpose = Purpose.ChoosePDF;
        else if (Intent.ACTION_PICK.equals(intent.getAction()))
            mPurpose = Purpose.PickFile;
        else
            mPurpose = Purpose.PickKeyFile;
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mInflater = inflater;
        
            // Inflate the layout for this fragment
        View rootView = mInflater.inflate(R.layout.recentfiles, container, false);
//        ListView listView = (ListView)rootView.findViewById(R.id.listview);

        loadRecentFilesList();

//        listView.setAdapter(mRecentFilesAdapter);
        setListAdapter(mRecentFilesAdapter);
        
        return rootView;
    }


    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        // mPositions.put(mDirectory.getAbsolutePath(), getListView().getFirstVisiblePosition());

        // if (position < (mParent == null ? 0 : 1)) {
        //     mDirectory = mParent;
        //     mHandler.post(mUpdateFiles);
        //     return;
        // }

        // position -= (mParent == null ? 0 : 1);

        // if (position < mDirs.length) {
        //     mDirectory = mDirs[position];
        //     mHandler.post(mUpdateFiles);
        //     return;
        // }

        // position -= mDirs.length;

//        Uri uri = Uri.parse(mFiles[position].getAbsolutePath());

        if (mRecentFilesAdapter == null ) return;
        Uri uri = Uri.parse(mRecentFilesAdapter.getItem(position));
        Intent intent = new Intent(getActivity(),MuPDFActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(uri);
        switch (mPurpose) {
            case ChoosePDF:
                    // Start an activity to display the PDF file
                startActivity(intent);
                break;
            case PickFile:
                getActivity().setResult(Activity.RESULT_OK, intent);
                getActivity().finish();
                break;
            case PickKeyFile:
                    // Return the uri to the caller
                getActivity().setResult(Activity.RESULT_OK, intent);
                getActivity().finish();
                break;
        }
    }

    
    public void onSharedPreferenceChanged(SharedPreferences sharedPref, String key) {
        loadRecentFilesList();
        mRecentFilesAdapter.notifyDataSetChanged();
    }

    
    private void loadRecentFilesList() {
            //Read the recent files list from preferences
        SharedPreferences prefs = getActivity().getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
        RecentFilesList recentFilesList = new RecentFilesList(RecentFilesList.MAX_RECENT_FILES);
        for (int i = 0; i<RecentFilesList.MAX_RECENT_FILES; i++)
        {
            String recentPath = prefs.getString("recentfile"+i,null);
            if(recentPath != null) recentFilesList.push(recentPath);
        }
        
        if(mRecentFilesAdapter == null)
//            mRecentFilesAdapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1, recentFilesList.toArray(new String[recentFilesList.size()])) {
            mRecentFilesAdapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1, recentFilesList) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View view;
                    if (convertView == null) {
			view = mInflater.inflate(R.layout.picker_entry, null);
                    } else {
			view = convertView;
                    }
                    ((TextView)view.findViewById(R.id.name)).setText(getItem(position));
                    ((ImageView)view.findViewById(R.id.icon)).setImageResource(R.drawable.ic_doc);
                    ((ImageView)view.findViewById(R.id.icon)).setColorFilter(Color.argb(255, 0, 0, 0));
                    return view;
                }
                
            };
        else
        {
            mRecentFilesAdapter.clear();
            mRecentFilesAdapter.addAll(recentFilesList.toArray(new String[recentFilesList.size()]));
        }
    }
}
