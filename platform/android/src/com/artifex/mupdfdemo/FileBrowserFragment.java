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

    static private File  mDirectory;
    static private Map<String, Integer> mPositions = new HashMap<String, Integer>();
    private File         mParent;
    private File []      mDirs;
    private File []      mFiles;
    private Handler	     mHandler;
    private Runnable     mUpdateFiles;
    private ChoosePDFAdapter mAdapter;
    private Purpose      mPurpose;
//    private ListView mListView;
    private String mFilename;
    
    FileBrowserFragment(Intent intent) {
        super();
        
        if (Intent.ACTION_MAIN.equals(intent.getAction())) 
            mPurpose = Purpose.ChoosePDF;
        else if (Intent.ACTION_PICK.equals(intent.getAction()))
            mPurpose = Purpose.PickFile;
        else
            mPurpose = Purpose.PickKeyFile;

        if(mPurpose == Purpose.PickFile) {
            mFilename = null;
            if(intent.getData() != null) mFilename = intent.getData().getLastPathSegment();
        }
        
        String storageState = Environment.getExternalStorageState();

        if (!Environment.MEDIA_MOUNTED.equals(storageState)
            && !Environment.MEDIA_MOUNTED_READ_ONLY.equals(storageState))
        {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.no_media_warning);
            builder.setMessage(R.string.no_media_hint);
            AlertDialog alert = builder.create();
            alert.setButton(AlertDialog.BUTTON_POSITIVE,getString(R.string.dismiss),
                            new OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    getActivity().finish();
                                }
                            });
            alert.show();
            return;
        }

        if (mDirectory == null)
        {
            if(mPurpose == Purpose.PickFile && intent.getData() != null)
                mDirectory = (new File(intent.getData().getPath())).getParentFile();
            else    
                mDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        }

        
            // ...that is updated dynamically when files are scanned
        mHandler = new Handler();
        mUpdateFiles = new Runnable() {
                public void run() {
                    if(!isAdded()) return;
                    
                    Resources res = getResources();
                    String appName = res.getString(R.string.app_name);
                    String version = res.getString(R.string.version);
                    String title = res.getString(R.string.picker_title_App_Ver_Dir);
                        //setTitle(String.format(title, appName, version, mDirectory));
//                    setTitle(mDirectory.getPath());
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
                                            // if (fname.endsWith(".xps"))
                                            // 	return true;
                                            // if (fname.endsWith(".cbz"))
                                            // 	return true;
                                            // if (fname.endsWith(".png"))
                                            // 	return true;
                                            // if (fname.endsWith(".jpe"))
                                            // 	return true;
                                            // if (fname.endsWith(".jpeg"))
                                            // 	return true;
                                            // if (fname.endsWith(".jpg"))
                                            // 	return true;
                                            // if (fname.endsWith(".jfif"))
                                            // 	return true;
                                            // if (fname.endsWith(".jfif-tbnl"))
                                            // 	return true;
                                            // if (fname.endsWith(".tif"))
                                            // 	return true;
                                            // if (fname.endsWith(".tiff"))
                                            // 	return true;
                                        return false;
                                    case PickKeyFile:
                                        if (fname.endsWith(".pfx"))
                                            return true;
                                        return false;
                                    default:
                                        return false;
                                }
                            }
                        });
                    if (mFiles == null)
                        mFiles = new File[0];

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

        View view = inflater.inflate(R.layout.filebrowser, container, false);

        mAdapter = new ChoosePDFAdapter(inflater);
        
        if(mPurpose == Purpose.PickFile) {
//            String filename = null;
//            if(intent.getData() != null) filename = intent.getData().getLastPathSegment();
            EditText editText = (EditText)view.findViewById(R.id.newfilenamefield);
            if(mFilename != null) editText.setText(mFilename);
            editText.setVisibility(View.VISIBLE);
            editText.requestFocus();
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
        
        return view;
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

    
    // @Override
    // public View onCreateView(LayoutInflater inflater,
    //         ViewGroup container, Bundle savedInstanceState) {
    //     // The last two arguments ensure LayoutParams are inflated
    //     // properly.
    //     View rootView = inflater.inflate(
    //             R.layout.browsefiles, container, false);
    //     ((EditText) rootView.findViewById(R.id.newfilenamefield)).setText("bla");
    //     return rootView;
    // }
}
