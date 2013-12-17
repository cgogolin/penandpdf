package com.artifex.mupdfdemo;

import android.app.Activity;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;


import android.os.FileObserver;
import android.os.Handler;
import android.os.Environment;
import android.os.Bundle;
import android.content.Intent;
import android.app.AlertDialog;

import android.app.Fragment;
import android.app.ListFragment;
import android.view.View;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
//import android.widget.Toast;
import android.net.Uri;

//public static class SettingsFragment extends PreferenceFragment {
public class FileBrowserFragment extends ListFragment {

    private enum Purpose { ChoosePDF, PickKeyFile, PickFile }
    
    static private File mDirectory;
    static private Map<String, Integer> mPositions = new HashMap<String, Integer>();
    private File  mParent;
    private File[] mDirs;
    private File[] mFiles;
    private Handler mHandler;
    private Runnable mUpdateFiles;
    private ChoosePDFAdapter mAdapter;
    private Purpose mPurpose;
    private String mFilename;

    static final String PURPOSE = "purpose";
    static final String FILENAME = "filename";
    static final String DIRECTORY = "directory";
    
    public static final FileBrowserFragment newInstance(Intent intent) {
        
            //Collect data from intent
        Purpose purpose;
        if (Intent.ACTION_MAIN.equals(intent.getAction())) 
            purpose = Purpose.ChoosePDF;
        else if (Intent.ACTION_PICK.equals(intent.getAction()))
            purpose = Purpose.PickFile;
        else
            purpose = Purpose.PickKeyFile;
        
        String filename = null;
        if(purpose == Purpose.PickFile) {
            if(intent.getData() != null) filename = intent.getData().getLastPathSegment();
        }
        
        File directory = null;
        if(purpose == Purpose.PickFile && intent.getData() != null)
            directory = (new File(intent.getData().getPath())).getParentFile();
            
            //Put the collected data in a Bundle
        Bundle bundle = new Bundle(3);
        if(purpose != null) bundle.putString(PURPOSE,purpose.toString());
        if(filename != null) bundle.putString(FILENAME,filename);
        if(directory != null) bundle.putString(DIRECTORY,directory.getAbsolutePath());
        
            //Pass it to the Fragment and return 
        FileBrowserFragment fileBrowserFragment = new FileBrowserFragment();
        fileBrowserFragment.setArguments(bundle);
        return fileBrowserFragment;
    }
    

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        bundle.putString(PURPOSE,mPurpose.toString());
        bundle.putString(FILENAME,mFilename);
        bundle.putString(DIRECTORY,mDirectory.getAbsolutePath());
    }

    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
            //Retrieve the data that was set with setArguments()
        if(getArguments() != null) 
        {
            mFilename = getArguments().getString(FILENAME);
            mPurpose = Purpose.valueOf(getArguments().getString(PURPOSE));
            if(getArguments().getString(DIRECTORY) != null) mDirectory = new File(getArguments().getString(DIRECTORY));
        }
        else if(savedInstanceState != null)
        {
            mFilename = savedInstanceState.getString(FILENAME);
            mPurpose = Purpose.valueOf(savedInstanceState.getString(PURPOSE));
            if(savedInstanceState.getString(DIRECTORY) != null) mDirectory = new File(savedInstanceState.getString(DIRECTORY));
        }
            //If we didn't get a directory we default to downloads
        if(mDirectory == null)
            mDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);    
        
        // String storageState = Environment.getExternalStorageState();
        // if (!Environment.MEDIA_MOUNTED.equals(storageState)
        //     && !Environment.MEDIA_MOUNTED_READ_ONLY.equals(storageState))
        // {
        //     AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        //     builder.setTitle(R.string.no_media_warning);
        //     builder.setMessage(R.string.no_media_hint);
        //     AlertDialog alert = builder.create();
        //     alert.setButton(AlertDialog.BUTTON_POSITIVE,getString(R.string.dismiss),
        //                     new OnClickListener() {
        //                         public void onClick(DialogInterface dialog, int which) {
        //                             getActivity().finish();
        //                         }
        //                     });
        //     alert.show();
        //     return;
        // }
        
            // Create a new handler that is updated dynamically when files are scanned
        mHandler = new Handler();
        mUpdateFiles = new Runnable() {
                public void run() {
                    if(!isAdded()) return;
                    if(mDirectory==null) return;

                        //Set the title from the current direcory
                    Resources res = getResources();
                    String appName = res.getString(R.string.app_name);
//                    String version = res.getString(R.string.version);
                    String title = res.getString(R.string.picker_title_App_Ver_Dir);
                    getActivity().setTitle(mDirectory.getPath());

                        //Get the parent directory and the directories and files
                    mParent = mDirectory.getParentFile();
                    mDirs = mDirectory.listFiles(new FileFilter() {
                            public boolean accept(File file) {
                                return file.isDirectory();
                            }
                        });
                    if (mDirs == null)
                        mDirs = new File[0];
                    mFiles = mDirectory.listFiles(new FileFilter() {
                            public boolean accept(File file) {
                                if (file.isDirectory())
                                    return false;
                                String fname = file.getName().toLowerCase();
                                switch (mPurpose) {
                                    case ChoosePDF:
                                    case PickFile:
                                        if (fname.endsWith(".pdf"))
                                            return true;
                                        else
                                            return false;
                                    // case PickKeyFile:
                                    //     if (fname.endsWith(".pfx"))
                                    //         return true;
                                    //     else
                                    //         return false;
                                    default:
                                        return false;
                                }
                            }
                        });
                    if (mFiles == null)
                        mFiles = new File[0];

                        //Sort the file and directory lists
                    Arrays.sort(mFiles, new Comparator<File>() {
                            public int compare(File arg0, File arg1) {
                                return arg0.getName().compareToIgnoreCase(arg1.getName());
                            }
                        });
                    Arrays.sort(mDirs, new Comparator<File>() {
                            public int compare(File arg0, File arg1) {
                                return arg0.getName().compareToIgnoreCase(arg1.getName());
                            }
                        });

                        //Add them to the adapter
                    mAdapter.clear();
                    if (mParent != null)
                        mAdapter.add(new ChoosePDFItem(ChoosePDFItem.Type.PARENT, getString(R.string.parent_directory)));
                    for (File f : mDirs)
                        mAdapter.add(new ChoosePDFItem(ChoosePDFItem.Type.DIR, f.getName()));
                    for (File f : mFiles)
                        mAdapter.add(new ChoosePDFItem(ChoosePDFItem.Type.DOC, f.getName()));
                    lastPosition();
                }
            };
        
            // Start initial file scan...
        mHandler.post(mUpdateFiles);
            // ...and observe the directory and scan files upon changes.
        FileObserver observer = new FileObserver(mDirectory.getPath(), FileObserver.CREATE | FileObserver.DELETE) {
                public void onEvent(int event, String path) {
                    mHandler.post(mUpdateFiles);
                }
            };
        observer.startWatching();
    }


    @Override
    public void onResume() {
        super.onResume();
        mHandler.post(mUpdateFiles);
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.filebrowser, container, false);

        mAdapter = new ChoosePDFAdapter(inflater);
        
        if(mPurpose == Purpose.PickFile) {
            EditText editText = (EditText)rootView.findViewById(R.id.newfilenamefield);
            if(mFilename != null) editText.setText(mFilename);
            editText.setVisibility(View.VISIBLE);
            editText.requestFocus();
            editText.setSelection(mFilename.lastIndexOf("."));
            editText.setOnEditorActionListener(new OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                        Uri uri = Uri.parse(mDirectory.getPath()+"/"+v.getText());
                        Intent intent = new Intent(getActivity(),MuPDFActivity.class);
                        intent.setAction(Intent.ACTION_VIEW);//?
                        intent.setData(uri);
                        getActivity().setResult(Activity.RESULT_OK, intent);
                        getActivity().finish();
                        return true;
                    }
                });
        }
        
        // ListView listView = view.findViewById(R.id.list);
        // listView.setListAdapter(adapter);
        setListAdapter(mAdapter);
        
        return rootView;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        mPositions.put(mDirectory.getAbsolutePath(), getListView().getFirstVisiblePosition());

        if (position < (mParent == null ? 0 : 1)) {
            mDirectory = mParent;
            mHandler.post(mUpdateFiles);
            return;
        }

        position -= (mParent == null ? 0 : 1);

        if (position < mDirs.length) {
            mDirectory = mDirs[position];
            mHandler.post(mUpdateFiles);
            return;
        }

        position -= mDirs.length;

        Uri uri = Uri.parse(mFiles[position].getAbsolutePath());
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

    @Override
    public void onPause() {
        super.onPause();
        mPositions.put(mDirectory.getAbsolutePath(), getListView().getFirstVisiblePosition());
    }


    private void lastPosition() {
        String p = mDirectory.getAbsolutePath();
        if (mPositions.containsKey(p))
            getListView().setSelection(mPositions.get(p));
    }

    void goToDir(File dir) {
            mDirectory = dir;
            mHandler.post(mUpdateFiles);
    }
}
