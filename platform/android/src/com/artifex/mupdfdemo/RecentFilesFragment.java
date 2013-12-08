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

    private enum Purpose { ChoosePDF, PickKeyFile, PickFile }
    
    private ArrayAdapter<String> mRecentFilesAdapter;
    private Purpose mPurpose;

    static final String PURPOSE = "purpose";
    static final String FILENAME = "filename";
    static final String DIRECTORY = "directory";

    public static final RecentFilesFragment newInstance(Intent intent) {
            //Collect data from intent
        Purpose purpose;
        if (Intent.ACTION_MAIN.equals(intent.getAction())) 
            purpose = Purpose.ChoosePDF;
        else if (Intent.ACTION_PICK.equals(intent.getAction()))
            purpose = Purpose.PickFile;
        else
            purpose = Purpose.PickKeyFile;
        
            //Put the collected data in a Bundle
        Bundle bundle = new Bundle(3);
        bundle.putString(PURPOSE,purpose.toString());
        
        RecentFilesFragment recentFilesFragment = new RecentFilesFragment();
        recentFilesFragment.setArguments(bundle);
        return recentFilesFragment;
    }
    

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        bundle.putString(PURPOSE,mPurpose.toString());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
            //Retrieve the data that was set with setArguments()
        if(getArguments()!=null)
            mPurpose = Purpose.valueOf(getArguments().getString(PURPOSE));
        else if(savedInstanceState != null)
            mPurpose = Purpose.valueOf(savedInstanceState.getString(PURPOSE));
    }  

    @Override
    public void onResume() {
        super.onResume();
            //Listen for changes in the recent files list
        getActivity().getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS).registerOnSharedPreferenceChangeListener(this);
    }


    @Override
    public void onPause() {
        super.onPause();
            //Stop listening for changes in the recent files list
        getActivity().getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS).unregisterOnSharedPreferenceChangeListener(this);
    }


    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        final LayoutInflater layoutInflater = inflater; //used to pass on the inflator to the Adapter
            //Create the RecentFilesAdapter (an ArrayListAdapter)
//        mRecentFilesAdapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1, recentFilesList) {
        mRecentFilesAdapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view;
                if (convertView == null) {
                    view = layoutInflater.inflate(R.layout.picker_entry, null);
                } else {
                    view = convertView;
                }
                ((TextView)view.findViewById(R.id.name)).setText(getItem(position));
                ((ImageView)view.findViewById(R.id.icon)).setImageResource(R.drawable.ic_doc);
//                ((ImageView)view.findViewById(R.id.icon)).setColorFilter(Color.argb(255, 0, 0, 0));
                return view;
            }    
        };
        
        loadRecentFilesList();
            
            // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.recentfiles, container, false);
        setListAdapter(mRecentFilesAdapter);  
        return rootView;
    }


    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        
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
        if (getActivity() == null) return;
        
            //Read the recent files list from preferences
        SharedPreferences prefs = getActivity().getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
        RecentFilesList recentFilesList = new RecentFilesList(prefs);
        // RecentFilesList recentFilesList = new RecentFilesList(RecentFilesList.MAX_RECENT_FILES);
        // for (int i = 0; i<RecentFilesList.MAX_RECENT_FILES; i++)
        // {
        //     String recentPath = prefs.getString("recentfile"+i,null);
        //     if(recentPath != null) recentFilesList.push(recentPath);
        // }
        
        if(mRecentFilesAdapter != null)
            mRecentFilesAdapter.clear();
            mRecentFilesAdapter.addAll(recentFilesList.toArray(new String[recentFilesList.size()]));
        }
    }
