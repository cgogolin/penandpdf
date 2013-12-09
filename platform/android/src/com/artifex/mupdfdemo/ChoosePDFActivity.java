package com.artifex.mupdfdemo;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.res.Resources;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuInflater;
import android.view.KeyEvent;
import android.widget.ListView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.preference.PreferenceManager;
import android.content.SharedPreferences;
import android.app.ActionBar;

//enum Purpose { ChoosePDF, PickKeyFile, PickFile }

public class ChoosePDFActivity extends ListActivity
{
    private enum Purpose { ChoosePDF, PickKeyFile, PickFile }
    
    static private File  mDirectory;
    static private Map<String, Integer> mPositions = new HashMap<String, Integer>();
    private File         mParent;
    private File []      mDirs;
    private File []      mFiles;
    private Handler	     mHandler;
    private Runnable     mUpdateFiles;
    private ChoosePDFAdapter adapter;
    private Purpose      mPurpose;

    private RecentFilesList recentFilesList;
    

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
                
        Intent intent = getIntent();
                
            //Set default preferences on first start
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
                
//		mPurpose = Intent.ACTION_MAIN.equals(getIntent().getAction()) ? Purpose.ChoosePDF : Purpose.PickKeyFile;

        if (Intent.ACTION_MAIN.equals(intent.getAction())) 
            mPurpose = Purpose.ChoosePDF;
        else if (Intent.ACTION_PICK.equals(intent.getAction()))
            mPurpose = Purpose.PickFile;
        else
            mPurpose = Purpose.PickKeyFile;
                
            //Read the recent files list from preferences
//        SharedPreferences prefs = getPreferences(Context.MODE_MULTI_PROCESS);
        SharedPreferences prefs = getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
        // recentFilesList = new RecentFilesList(RecentFilesList.MAX_RECENT_FILES);
        // for (int i = 0; i<recentFilesList.size(); i++)
        // {
        //     String recentFile = prefs.getString("recentfile"+i,null);
        //     if(recentFile != null) recentFilesList.push(recentFile);
        // }
        recentFilesList = new RecentFilesList(prefs);
        
        setContentView(R.layout.choosepdf);

        if(mPurpose == Purpose.PickFile) {
            String filename = null;
            if(intent.getData() != null) filename = intent.getData().getLastPathSegment();
            EditText editText = (EditText)findViewById(R.id.newfilenamefield);
            if(filename != null) editText.setText(filename);
            editText.setVisibility(View.VISIBLE);
            editText.requestFocus();
            editText.setOnEditorActionListener(new OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                        Uri uri = Uri.parse(mDirectory.getPath()+"/"+v.getText());
                        Intent intent = new Intent(getApplicationContext(),MuPDFActivity.class);
                        intent.setAction(Intent.ACTION_VIEW);//?
                        intent.setData(uri);
                        setResult(RESULT_OK, intent);
                        finish();
                        return true;
                    }
                });
        }
                
                
        String storageState = Environment.getExternalStorageState();

        if (!Environment.MEDIA_MOUNTED.equals(storageState)
            && !Environment.MEDIA_MOUNTED_READ_ONLY.equals(storageState))
        {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.no_media_warning);
            builder.setMessage(R.string.no_media_hint);
            AlertDialog alert = builder.create();
            alert.setButton(AlertDialog.BUTTON_POSITIVE,getString(R.string.dismiss),
                            new OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    finish();
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
                
            // Create a list adapter...
        adapter = new ChoosePDFAdapter(getLayoutInflater());

        setListAdapter(adapter);

            // ...that is updated dynamically when files are scanned
        mHandler = new Handler();
        mUpdateFiles = new Runnable() {
                public void run() {
                    Resources res = getResources();
                    String appName = res.getString(R.string.app_name);
                    String version = res.getString(R.string.version);
                    String title = res.getString(R.string.picker_title_App_Ver_Dir);
                        //setTitle(String.format(title, appName, version, mDirectory));
                    setTitle(mDirectory.getPath());
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

                    adapter.clear();
                    if (mParent != null)
                        adapter.add(new ChoosePDFItem(ChoosePDFItem.Type.PARENT, getString(R.string.parent_directory)));
                    for (File f : mDirs)
                        adapter.add(new ChoosePDFItem(ChoosePDFItem.Type.DIR, f.getName()));
                    for (File f : mFiles)
                        adapter.add(new ChoosePDFItem(ChoosePDFItem.Type.DOC, f.getName()));
                    lastPosition();
                }
            };

            // Start initial file scan...
        mHandler.post(mUpdateFiles);

        //     // ...and observe the directory and scan files upon changes.
        // FileObserver observer = new FileObserver(mDirectory.getPath(), FileObserver.CREATE | FileObserver.DELETE) {
        //         public void onEvent(int event, String path) {
        //             mHandler.post(mUpdateFiles);
        //         }
        //     };
        // observer.startWatching();
    }

    @Override
    protected void onResume()
        {
            super.onResume();
            mHandler.post(mUpdateFiles);
        }
    
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) //Inflates the options menu
        {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.choosepdf_menu, menu);
            return true;
        }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) //Handel clicks in the options menu 
        {
            switch (item.getItemId()) 
            {
                case R.id.menu_settings:
                    Intent intent = new Intent(this,SettingsActivity.class);
                    startActivity(intent);
                    return true;
                default:
                    return super.onOptionsItemSelected(item);
            }
        }
    
    private void lastPosition() {
        String p = mDirectory.getAbsolutePath();
        if (mPositions.containsKey(p))
            getListView().setSelection(mPositions.get(p));
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
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
        Intent intent = new Intent(this,MuPDFActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(uri);
        switch (mPurpose) {
            case ChoosePDF:
                    // Start an activity to display the PDF file
                startActivity(intent);
                break;
            case PickFile:
                setResult(RESULT_OK, intent);
                finish();
                break;
            case PickKeyFile:
                    // Return the uri to the caller
                setResult(RESULT_OK, intent);
                finish();
                break;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPositions.put(mDirectory.getAbsolutePath(), getListView().getFirstVisiblePosition());
    }
}
