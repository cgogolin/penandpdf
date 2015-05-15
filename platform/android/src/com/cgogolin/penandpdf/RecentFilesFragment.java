package com.cgogolin.penandpdf;

import java.io.File;

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
import java.util.Collections;
import java.util.ListIterator;

//public static class SettingsFragment extends PreferenceFragment {
public class RecentFilesFragment extends ListFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    public interface goToDirInterface {
        public void goToDir(File dir);
    }
    
    private enum Purpose { ChoosePDF, PickKeyFile, PickFile }
    
    private ArrayAdapter<String> mRecentFilesAdapter;
    private Purpose mPurpose;

    static final String PURPOSE = "purpose";
    static final String FILENAME = "filename";
    static final String DIRECTORY = "directory";

    private int numDirectories = 0;
    
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
        loadRecentFilesList();
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
                if(position < numDirectories)
                    ((ImageView)view.findViewById(R.id.icon)).setImageResource(R.drawable.ic_dir);  
                else
                    ((ImageView)view.findViewById(R.id.icon)).setImageResource(R.drawable.ic_doc);
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

        Uri uri = Uri.parse(mRecentFilesAdapter.getItem(position));
        
        if (mRecentFilesAdapter == null ) return;

            //A directory was clicked and we are in pick a file mode
//        if(mPurpose == Purpose.PickFile && position < numDirectories)
        if(position < numDirectories)
        {
            ((goToDirInterface)getActivity()).goToDir(new File(uri.getPath()));
            return;
        }

        Intent intent = new Intent(getActivity(),PenAndPDFActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(uri);
        switch (mPurpose) {
            case ChoosePDF:
                    // Start an activity to display the PDF file
                startActivity(intent);
                getActivity().finish();
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
    }

    
    private void loadRecentFilesList() {
        if (getActivity() == null || mRecentFilesAdapter == null) return;
        
            //Read the recent files list from preferences
        SharedPreferences prefs = getActivity().getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
        RecentFilesList recentFilesList = new RecentFilesList(prefs);

            //Add the directories of the most recent files to the list if we were asked to pick a file
        RecentFilesList recentDirectoriesList = new RecentFilesList();
        // if(mPurpose == Purpose.PickFile)
        // {
            ListIterator<String> iterartor = recentFilesList.listIterator(0);
            while (iterartor.hasNext()) {
                String file = iterartor.next();
                recentDirectoriesList.push(file.substring(0,file.lastIndexOf("/")));
            }
            recentFilesList.addAll(0,recentDirectoriesList);
        // }
        numDirectories = recentDirectoriesList.size();

            //Make newest elements appear first
//        Collections.reverse(recentFilesList);

            //Update the data in the adapter
        mRecentFilesAdapter.clear();
        mRecentFilesAdapter.addAll(recentFilesList.toArray(new String[recentFilesList.size()]));
        mRecentFilesAdapter.notifyDataSetChanged();
    }
    
}
