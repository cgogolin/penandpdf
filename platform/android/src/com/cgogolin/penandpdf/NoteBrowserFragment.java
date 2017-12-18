package com.cgogolin.penandpdf;

import android.app.AlertDialog;
import android.app.Activity;
import android.support.v4.app.Fragment;
import android.support.v4.app.ListFragment;
import android.support.v7.app.AppCompatActivity;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.TextView;
import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class NoteBrowserFragment extends ListFragment {

    private enum Purpose { ChooseFileForOpening, PickKeyFile, ChooseFileForSaving, ChooseFileForOpeningAndLaunch }
    
    private File mDirectory;
    private Map<String, Integer> mPositions = new HashMap<String, Integer>();
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
    
    public static final NoteBrowserFragment newInstance(Intent intent) {
        
            //Collect data from intent
        Purpose purpose;

        if(intent.ACTION_MAIN.equals(intent.getAction()))
            purpose = Purpose.ChooseFileForOpeningAndLaunch;
        else if(intent.ACTION_EDIT.equals(intent.getAction()))
            purpose = Purpose.ChooseFileForOpening;
        else if((intent.ACTION_PICK.equals(intent.getAction())))
            purpose = Purpose.ChooseFileForSaving;
        else
            purpose = Purpose.PickKeyFile;

            //Try to retrieve file name and path
        String filename = null;
        if(purpose == Purpose.ChooseFileForSaving) {
            filename = intent.getStringExtra(Intent.EXTRA_TITLE);
            if(filename == null) filename = intent.getData().getLastPathSegment();
        }
        File directory = null;
        try
        {
            if(purpose == Purpose.ChooseFileForSaving && intent.getData() != null)
            {
                File file = new File(Uri.decode(intent.getData().getEncodedPath()));
                if(file.canWrite())
                    directory = file.getParentFile();
            }
        }
        catch(Exception e)
        {
                //Nothing we can do if that fails, and it seems to do so on certain android versions if the path contains special characters...
        }
            
            //Put the collected data in a Bundle
        Bundle bundle = new Bundle(3);
        if(purpose != null) bundle.putString(PURPOSE,purpose.toString());
        if(filename != null) bundle.putString(FILENAME,filename);
        if(directory != null) bundle.putString(DIRECTORY,directory.getAbsolutePath());
        
            //Pass it to the Fragment and return 
        NoteBrowserFragment noteBrowserFragment = new NoteBrowserFragment();
        noteBrowserFragment.setArguments(bundle);
        return noteBrowserFragment;
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
            //We default to the notes directory
        if(mDirectory == null)
            mDirectory = PenAndPDFActivity.getNotesDir(getActivity());
        
            // Create a new handler that is updated dynamically when files are scanned
        mHandler = new Handler();
        mUpdateFiles = new Runnable() {
                public void run() {
                    if(!isAdded()) return;
                    if(mDirectory==null) return;

                        //Get the parent directory and the directories and files
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
                                    case ChooseFileForOpening:
                                    case ChooseFileForOpeningAndLaunch:
                                    case ChooseFileForSaving:
                                        if (fname.endsWith(".pdf"))
                                            return true;
                                        else
                                            return false;
                                    case PickKeyFile:
                                        if (fname.endsWith(".pfx"))
                                            return true;
                                        else
                                            return false;
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

                        //Add them to the adapter
                    mAdapter.clear();
                    for (File f : mFiles)
                        mAdapter.add(new ChoosePDFItem(ChoosePDFItem.Type.DOC, f.getName()));
                    mAdapter.notifyDataSetChanged();
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

        View rootView = inflater.inflate(R.layout.notebrowser, container, false);

        mAdapter = new ChoosePDFAdapter(inflater);
        
        if(mPurpose == Purpose.ChooseFileForSaving) {
            final EditText editText = (EditText)rootView.findViewById(R.id.newfilenamefield);
            if(mFilename != null)
            {
                editText.setText(mFilename);
                int indexOfDot = mFilename.lastIndexOf(".");
                if(indexOfDot > -1) editText.setSelection(indexOfDot);
            }
            editText.setVisibility(View.VISIBLE);
            editText.requestFocus();
            editText.setOnEditorActionListener(new OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                        Uri uri = Uri.parse(mDirectory.getPath()+"/"+v.getText());
                        passUriBack(uri);
                        return true;
                    }
                });
            Button saveButton = (Button)rootView.findViewById(R.id.savebutton);
            saveButton.setVisibility(View.VISIBLE);
            saveButton.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        Uri uri = Uri.parse(mDirectory.getPath()+"/"+editText.getText());
                        passUriBack(uri);
                    }
                });
        }
        
        setListAdapter(mAdapter);
        
        return rootView;
    }
    
    private void passUriBack(Uri uri){
        Intent intent = new Intent(getActivity(),PenAndPDFActivity.class);
        intent.setAction(Intent.ACTION_VIEW);//?
        intent.setData(uri);
        getActivity().setResult(AppCompatActivity.RESULT_OK, intent);
        getActivity().finish();
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
        Intent intent = new Intent(getActivity(),PenAndPDFActivity.class);
        intent.setData(uri);
        
        switch (mPurpose) {
            case ChooseFileForOpeningAndLaunch:
                intent.setAction(Intent.ACTION_VIEW);
                startActivity(intent);
                getActivity().finish();
                break;
            case ChooseFileForOpening:
            case ChooseFileForSaving:
            case PickKeyFile:
                getActivity().setResult(AppCompatActivity.RESULT_OK, intent);
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

    private void setTitle() {
        Activity activity = getActivity(); 
        if (isAdded() && activity != null) {
            Resources res = getResources();
            String appName = res.getString(R.string.app_name);
            activity.setTitle(appName);
        }
    }

    public void inForground() {
        setTitle();
    }
}
