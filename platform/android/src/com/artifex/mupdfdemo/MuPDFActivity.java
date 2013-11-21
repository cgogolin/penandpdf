//  -*- compile-command: cd ~/src/android/mupdf/platform/android && ant clean && ~/src/android/android-ndk-r9/ndk-build && ant debug && cp bin/MuPDF-debug.apk /home/cgogolin/Dropbox/galaxynote8/ -*-

package com.artifex.mupdfdemo;

import java.io.InputStream;
import java.io.File;
import java.util.concurrent.Executor;

import com.artifex.mupdfdemo.ReaderView.ViewMapper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ViewAnimator;
import android.widget.SearchView;
import android.widget.ShareActionProvider;
import android.preference.PreferenceManager;
import android.app.ActionBar;
import android.app.SearchManager;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;


import android.text.InputType;



class ThreadPerTaskExecutor implements Executor {
    public void execute(Runnable r) {
        new Thread(r).start();
    }
}

public class MuPDFActivity extends Activity implements FilePicker.FilePickerSupport, SharedPreferences.OnSharedPreferenceChangeListener, SearchView.OnQueryTextListener, SearchView.OnCloseListener
{
        /* The core rendering instance */
//    private final static String[] ActionBarModeStrings = {"Main", "Annot", "Edit", "Search"};
    enum ActionBarMode {Main, Annot, Edit, Search, Copy};
    enum AcceptMode {Highlight, Underline, StrikeOut, Ink, CopyText};
    
    private SearchView searchView = null;
    private String oldQueryText = "";
    private String mQuery = "";
    private ShareActionProvider mShareActionProvider = null;
    private boolean mNotSaveOnDestroyThisTime = false;
    private boolean mNotSaveOnPauseThisTime = false;
    
    private final int    OUTLINE_REQUEST=0;
    private final int    PRINT_REQUEST=1;
    private final int    FILEPICK_REQUEST=2;
    private MuPDFCore    core;
    private MuPDFReaderView mDocView;
    private EditText     mPasswordView;
    private ActionBarMode   mActionBarMode = ActionBarMode.Main;
    private AcceptMode   mAcceptMode = AcceptMode.Highlight;
    private SearchTask   mSearchTask;
    private AlertDialog.Builder mAlertBuilder;
    private boolean    mLinkHighlight = false;
    private final Handler mHandler = new Handler();
    private boolean mAlertsActive= false;
    private boolean mReflow = false;
    private AsyncTask<Void,Void,MuPDFAlert> mAlertTask;
    private AlertDialog mAlertDialog;
    private FilePicker mFilePicker;

    public void createAlertWaiter() {
        mAlertsActive = true;
            // All mupdf library calls are performed on asynchronous tasks to avoid stalling
            // the UI. Some calls can lead to javascript-invoked requests to display an
            // alert dialog and collect a reply from the user. The task has to be blocked
            // until the user's reply is received. This method creates an asynchronous task,
            // the purpose of which is to wait of these requests and produce the dialog
            // in response, while leaving the core blocked. When the dialog receives the
            // user's response, it is sent to the core via replyToAlert, unblocking it.
            // Another alert-waiting task is then created to pick up the next alert.
        if (mAlertTask != null) {
            mAlertTask.cancel(true);
            mAlertTask = null;
        }
        if (mAlertDialog != null) {
            mAlertDialog.cancel();
            mAlertDialog = null;
        }
        mAlertTask = new AsyncTask<Void,Void,MuPDFAlert>() {

            @Override
            protected MuPDFAlert doInBackground(Void... arg0) {
                if (!mAlertsActive)
                    return null;

                return core.waitForAlert();
            }

            @Override
            protected void onPostExecute(final MuPDFAlert result) {
                    // core.waitForAlert may return null when shutting down
                if (result == null)
                    return;
                final MuPDFAlert.ButtonPressed pressed[] = new MuPDFAlert.ButtonPressed[3];
                for(int i = 0; i < 3; i++)
                    pressed[i] = MuPDFAlert.ButtonPressed.None;
                DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            mAlertDialog = null;
                            if (mAlertsActive) {
                                int index = 0;
                                switch (which) {
                                    case AlertDialog.BUTTON1: index=0; break;
                                    case AlertDialog.BUTTON2: index=1; break;
                                    case AlertDialog.BUTTON3: index=2; break;
                                }
                                result.buttonPressed = pressed[index];
                                    // Send the user's response to the core, so that it can
                                    // continue processing.
                                core.replyToAlert(result);
                                    // Create another alert-waiter to pick up the next alert.
                                createAlertWaiter();
                            }
                        }
                    };
                mAlertDialog = mAlertBuilder.create();
                mAlertDialog.setTitle(result.title);
                mAlertDialog.setMessage(result.message);
                switch (result.iconType)
                {
                    case Error:
                        break;
                    case Warning:
                        break;
                    case Question:
                        break;
                    case Status:
                        break;
                }
                switch (result.buttonGroupType)
                {
                    case OkCancel:
                    mAlertDialog.setButton(AlertDialog.BUTTON2, getString(R.string.cancel), listener);
                    pressed[1] = MuPDFAlert.ButtonPressed.Cancel;
                    case Ok:
                    mAlertDialog.setButton(AlertDialog.BUTTON1, getString(R.string.okay), listener);
                    pressed[0] = MuPDFAlert.ButtonPressed.Ok;
                    break;
                    case YesNoCancel:
                    mAlertDialog.setButton(AlertDialog.BUTTON3, getString(R.string.cancel), listener);
                    pressed[2] = MuPDFAlert.ButtonPressed.Cancel;
                    case YesNo:
                    mAlertDialog.setButton(AlertDialog.BUTTON1, getString(R.string.yes), listener);
                    pressed[0] = MuPDFAlert.ButtonPressed.Yes;
                    mAlertDialog.setButton(AlertDialog.BUTTON2, getString(R.string.no), listener);
                    pressed[1] = MuPDFAlert.ButtonPressed.No;
                    break;
                }
                mAlertDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                        public void onCancel(DialogInterface dialog) {
                            mAlertDialog = null;
                            if (mAlertsActive) {
                                result.buttonPressed = MuPDFAlert.ButtonPressed.None;
                                core.replyToAlert(result);
                                createAlertWaiter();
                            }
                        }
                    });

                mAlertDialog.show();
            }
        };

        mAlertTask.executeOnExecutor(new ThreadPerTaskExecutor());
    }

    public void destroyAlertWaiter() {
        mAlertsActive = false;
        if (mAlertDialog != null) {
            mAlertDialog.cancel();
            mAlertDialog = null;
        }
        if (mAlertTask != null) {
            mAlertTask.cancel(true);
            mAlertTask = null;
        }
    }

    private MuPDFCore openFile(String path)
	{
            System.out.println("Trying to open "+path);
            try
            {
                core = new MuPDFCore(this, path);
                    // New file: drop the old outline data
                OutlineActivityData.set(null);
            }
            catch (Exception e)
            {
                System.out.println(e);
                return null;
            }
            return core;
	}

    private MuPDFCore openBuffer(byte buffer[], String displayName)
	{
            System.out.println("Trying to open byte buffer");
            try
            {
                core = new MuPDFCore(this, buffer, displayName);
                    // New file: drop the old outline data
                OutlineActivityData.set(null);
            }
            catch (Exception e)
            {
                System.out.println(e);
                return null;
            }
            return core;
	}


    // @Override
    // public void onNewIntent(Intent intent)
    //     {}

    
    @Override
    public void onCreate(Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            
                //Set default preferences on first start
            PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
            PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
            
                //Get the ActionBarMode and AcceptMode from the bundle
            if(savedInstanceState != null)
            {
                    //We don't want to do this at the moment because we can't save what was selected ar drawn so easily 
                    // mActionBarMode = ActionBarMode.valueOf(savedInstanceState.getString("ActionBarMode", ActionBarMode.Main.toString ()));
                    // mAcceptMode = AcceptMode.valueOf(savedInstanceState.getString("AcceptMode", AcceptMode.Highlight.toString ()));
            }
            
                //Initialize the alert builder
            mAlertBuilder = new AlertDialog.Builder(this);
            
                //Get the core saved with onRetainNonConfigurationInstance()
            if (core == null) {
            core = (MuPDFCore)getLastNonConfigurationInstance();
            }
        }


    // @Override
    // protected void onStart()
    //     {}

    
    @Override
    protected void onResume()
        {
            super.onResume();
            
                //If core was not restored during onCreat()
                //or is not still present because the app was
                //only paused and save on pause is off set it up now
            if (core == null) setupCore();
            if (core != null) //OK, so apparently we have a valid pdf open
            {
                SearchTaskResult.set(null);
                createAlertWaiter();
                core.startAlerts();
                core.onSharedPreferenceChanged(this);
                setupUI();
            }
            else //Something went wrong
            {
                AlertDialog alert = mAlertBuilder.create();
                alert.setTitle(R.string.cannot_open_document);
                alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dismiss),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        finish();
                                    }
                                });
                alert.show();
            }
        }


        @Override
    protected void onPause()
        {
            super.onPause();
            
            if (mSearchTask != null) mSearchTask.stop();
            
            if(mDocView != null)
            {
                mDocView.applyToChildren(new ReaderView.ViewMapper() {
                        void applyToView(View view) {
                            ((MuPDFView)view).releaseBitmaps();
                        }
                    });
            }
            
            if (core != null && core.getFileName() != "" && mDocView != null) {
                SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
                SharedPreferences.Editor edit = prefs.edit();
                edit.putInt("page"+core.getFileName(), mDocView.getDisplayedViewIndex());
                edit.commit();
            }
            
            if (core != null)
            {
                destroyAlertWaiter();
                core.stopAlerts();
            }
            
            if(core != null && !isChangingConfigurations())
            {
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
                if(!mNotSaveOnPauseThisTime && core.hasChanges() && core.getFileName() != "" && sharedPref.getBoolean(SettingsActivity.PREF_SAVE_ON_PAUSE, true))
                {
                    core.save();
                    core.onDestroy(); //Destroy only if we have saved
                    core = null;
                }
            }
        }
    

    // @Override
    // protected void onStop()
    //     {}


    protected void onDestroy() //There is no guarantee that this is ever called!!!
	{
            PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);

            if(core != null && !isChangingConfigurations())
            {
                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
                if(!mNotSaveOnDestroyThisTime && core.hasChanges() && core.getFileName() != "" && sharedPref.getBoolean(SettingsActivity.PREF_SAVE_ON_DESTROY, true))
                    core.save();
                core.onDestroy(); //Destroy in any case
                core = null;
            }
            
            if (mAlertTask != null) {
                mAlertTask.cancel(true);
                mAlertTask = null;
            }
            searchView = null;
            mShareActionProvider = null;
            super.onDestroy();
	}







    

    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) //Inflates the options menu
        {
            MenuInflater inflater = getMenuInflater();
            switch (mActionBarMode)
            {
                case Main:
                    inflater.inflate(R.menu.main_menu, menu);
                
                        // Set up the share action
                    MenuItem shareItem = menu.findItem(R.id.menu_share);
                    if (core == null || core.getPath() == "")
                    {
                        shareItem.setEnabled(false).setVisible(false);
                    }
                    else
                    {
                        if (mShareActionProvider == null)
                        {
                            mShareActionProvider = (ShareActionProvider) shareItem.getActionProvider();
                            Intent shareIntent = new Intent();
                            shareIntent.setAction(Intent.ACTION_SEND);
                            shareIntent.setType("plain/text");
                            shareIntent.setType("*/*");
                            shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(core.getPath())));
                            if (mShareActionProvider != null) mShareActionProvider.setShareIntent(shareIntent);
                        }   
                    }
                    break;
                case Annot:
                case Edit:
                case Copy:
                    inflater.inflate(R.menu.annot_menu, menu);
                    MenuItem undoButton = menu.findItem(R.id.menu_undo);
                    undoButton.setEnabled(false).setVisible(false);
                    break;
                case Search:
                    inflater.inflate(R.menu.search_menu, menu);
                        // Associate searchable configuration with the SearchView
                    SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
                    searchView = (SearchView) menu.findItem(R.id.menu_search_box).getActionView();
                    searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
                    searchView.setIconified(false);
                    searchView.setOnCloseListener(this); //Implemented in: public void onClose(View view)
                    searchView.setOnQueryTextListener(this); //Implemented in: public boolean onQueryTextChange(String query) and public boolean onQueryTextSubmit(String query)
                default:
            }
            return true;
        }

    @Override
    public boolean onClose() //X button in search box
        {
            SearchTaskResult.set(null);
                // Make the ReaderView act on the change to mSearchTaskResult
                // via overridden onChildSetup method.
            mDocView.resetupChildren();
            return false;
        }
    
    @Override
    public boolean onQueryTextChange(String query) //For search
        { //This is a hacky way to determine when the user has reset the text field with the X button 
            if ( query.length() == 0 && oldQueryText.length() > 1) {
                SearchTaskResult.set(null);
                mDocView.resetupChildren();
            }
            oldQueryText = query;
            return false;
        }

    @Override 
    public boolean onQueryTextSubmit(String query) //For search
        {
            if(mQuery != query)
            {
                mQuery = query;
                search(1);
            }
            return true; //We handle this here and don't want to call onNewIntent()
        }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) //Handel clicks in the options menu 
        {
            MuPDFView pageView = (MuPDFView) mDocView.getDisplayedView();
            switch (item.getItemId()) 
            {
                case R.id.menu_settings:
                    Intent intent = new Intent(this,SettingsActivity.class);
                    startActivity(intent);
                    return true;
                case R.id.menu_draw:
                    mAcceptMode = AcceptMode.Ink;
                    mDocView.setMode(MuPDFReaderView.Mode.Drawing);
                    mActionBarMode = ActionBarMode.Annot;
                    invalidateOptionsMenu();
                    return true;
                case R.id.menu_highlight:
                    mAcceptMode = AcceptMode.Highlight;
                    mDocView.setMode(MuPDFReaderView.Mode.Selecting);
                    mActionBarMode = ActionBarMode.Annot;
                    invalidateOptionsMenu();
                    return true;
                case R.id.menu_underline:
                    mAcceptMode = AcceptMode.Underline;
                    mDocView.setMode(MuPDFReaderView.Mode.Selecting);
                    mActionBarMode = ActionBarMode.Annot;
                    invalidateOptionsMenu();
                    return true;
                case R.id.menu_strikeout:
                    mAcceptMode = AcceptMode.StrikeOut;
                    mDocView.setMode(MuPDFReaderView.Mode.Selecting);
                    mActionBarMode = ActionBarMode.Annot;
                    invalidateOptionsMenu();
                    return true;
                case R.id.menu_cancel:
                    switch (mActionBarMode) {
                        case Annot:
                        case Copy:
                            if (pageView != null) {
                                pageView.deselectText();
                                pageView.cancelDraw();
                            }
                            mDocView.setMode(MuPDFReaderView.Mode.Viewing);
                            break;
                        case Edit:
                            if (pageView != null)
                                pageView.deleteSelectedAnnotation();
                            break;
                        case Search:
                            SearchTaskResult.set(null);
                            mDocView.resetupChildren();
                            break;
                    }
                    mActionBarMode = ActionBarMode.Main;
                    invalidateOptionsMenu();
                    return true;
                case R.id.menu_accept:
                    switch (mActionBarMode) {
                        case Annot:
                        case Copy:
                            if (pageView != null) {
                                switch (mAcceptMode) {
                                    case Highlight:
                                        pageView.markupSelection(Annotation.Type.HIGHLIGHT);
                                        break;
                                    case Underline:
                                        pageView.markupSelection(Annotation.Type.UNDERLINE);
                                        break;
                                    case StrikeOut:
                                        pageView.markupSelection(Annotation.Type.STRIKEOUT);
                                        break;
                                    case Ink:
                                        pageView.saveDraw();
                                        break;
                                    case CopyText:    
                                        boolean success = pageView.copySelection();
                                        showInfo(success?getString(R.string.copied_to_clipboard):getString(R.string.no_text_selected));
                                        break;
                                }
                            }
                            mDocView.setMode(MuPDFReaderView.Mode.Viewing);
                            break;
                        case Edit:
                            if (pageView != null)
                                pageView.deselectAnnotation();
                    }
                    mActionBarMode = ActionBarMode.Main;
                    invalidateOptionsMenu();
                    return true;
                case R.id.menu_print:
                    printDoc();
                    return true;
                case R.id.menu_copytext:
                    mActionBarMode = ActionBarMode.Copy;
                    invalidateOptionsMenu();
                    mAcceptMode = AcceptMode.CopyText;
                    mDocView.setMode(MuPDFReaderView.Mode.Selecting);
                    showInfo(getString(R.string.select_text));
                    return true;
                case R.id.menu_search:
                    mActionBarMode = ActionBarMode.Search;
                    invalidateOptionsMenu();
                    return true;
                case R.id.menu_next:
                    if (mQuery != "") search(1);
                    return true;
                case R.id.menu_previous:
                    if (mQuery != "") search(-1);
                    return true;
                case R.id.menu_save:
                    return true;
                case R.id.menu_gotopage:
                    showGoToPageDialoge();
                    return true;
                default:
                    return super.onOptionsItemSelected(item);
            }
        }


    public void requestPassword() {
        mPasswordView = new EditText(this);
        mPasswordView.setInputType(EditorInfo.TYPE_TEXT_VARIATION_PASSWORD);
        mPasswordView.setTransformationMethod(new PasswordTransformationMethod());

        AlertDialog alert = mAlertBuilder.create();
        alert.setTitle(R.string.enter_password);
        alert.setView(mPasswordView);
        alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.okay),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                    // if (core.authenticatePassword(mPasswordView.getText().toString())) {
                                    //     setupUI();
				if (!core.authenticatePassword(mPasswordView.getText().toString()))
                                    requestPassword();
                                
                            }
                        });
        alert.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.cancel),
                        new DialogInterface.OnClickListener() {
                            
                            public void onClick(DialogInterface dialog, int which) {
				finish();
                            }
                        });
        alert.show();
    }


    private void showGoToPageDialoge() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine();
        new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_gotopage_title)
            .setPositiveButton(R.string.dialog_gotopage_ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                            // User clicked OK button
                        int pageNumber = Integer.parseInt(input.getText().toString());
                        mDocView.setDisplayedViewIndex(pageNumber == 0 ? 0 : pageNumber -1 );
                    }
                })
            .setNegativeButton(R.string.dialog_gotopage_cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                            // User cancelled the dialog
                    }
                })
            .setView(input)
            .show();
    }

    public void setupCore() {
        Intent intent = getIntent();
        if (Intent.ACTION_VIEW.equals(intent.getAction()))
        {
            Uri uri = intent.getData();
            String error = null;
            
            if(new File(Uri.decode(uri.getEncodedPath())).exists()) //Uri points to a file
            {
                core = openFile(Uri.decode(uri.getEncodedPath()));
            }
            else if (uri.toString().startsWith("content://")) //Uri points to a content provider
            {
                byte buffer[] = null;
                Cursor cursor = getContentResolver().query(uri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATA}, null, null, null); //This should be done asynchonously!
                if (cursor != null && cursor.moveToFirst())
                {
                    String displayName = "";
                    String data = "";
                    int displayNameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
                    int dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA);
                    if(displayNameIndex >= 0) displayName = cursor.getString(displayNameIndex);
                    if(dataIndex >= 0) data = cursor.getString(dataIndex);
                    try {
                        InputStream is = getContentResolver().openInputStream(uri);
                        if(is != null)
                        {
                            int len = is.available();
                            buffer = new byte[len];
                            is.read(buffer, 0, len);
                            is.close();
                        }
                    }
                    catch (Exception e) {
                        error = e.toString();
                    }
                    cursor.close();
                    if(buffer != null) core = openBuffer(buffer,displayName);
                }
            }
            else
            {
                error = getResources().getString(R.string.unable_to_interpret_uri)+" "+uri;
            }
            if (error != null) //There was an error
            {
                AlertDialog alert = mAlertBuilder.create();
                alert.setTitle(R.string.cannot_open_document);
                alert.setMessage(getResources().getString(R.string.reason)+": "+error);
                alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dismiss),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        finish();
                                    }
                                });
                alert.show();
                finish();
            }
        }
        if (core != null && core.needsPassword()) {
            requestPassword();
        }
        if (core != null && core.countPages() == 0)
        {
            core = null;
        }
    }
    
    
    public void setupUI() {
        if (core == null) return;
            
            // Now create the UI.
            // First create the document view
        mDocView = new MuPDFReaderView(this) {
                @Override
                protected void onMoveToChild(int pageNumber) {
//                    if (core == null) return;
                    getActionBar().setTitle(
                        Integer.toString(pageNumber+1)+"/"+Integer.toString(core.countPages())+" "+core.getFileName()
                                            );
                    super.onMoveToChild(pageNumber);
                }

                @Override
                protected void onTapMainDocArea() {
                    if (mActionBarMode == ActionBarMode.Edit) 
                    {
                        mActionBarMode = ActionBarMode.Main;
                        invalidateOptionsMenu();
                    }
                }

                @Override
                protected void onDocMotion() {

                }

                @Override
                protected void onHit(Hit item) {
                    if (item == Hit.Annotation) {
                        mActionBarMode = ActionBarMode.Edit;
                        invalidateOptionsMenu();
                    }
                    if (item == Hit.Nothing) {
                        mActionBarMode = ActionBarMode.Main;
                        invalidateOptionsMenu();
                    }
                }
            };
        mDocView.setAdapter(new MuPDFPageAdapter(this, this, core));

            //Enable link highlighting by default
        mDocView.setLinksEnabled(true);
                                
        mSearchTask = new SearchTask(this, core) {
                @Override
                protected void onTextFound(SearchTaskResult result) {
                    SearchTaskResult.set(result);
                        // Ask the ReaderView to move to the resulting page
                    mDocView.setDisplayedViewIndex(result.pageNumber);
                        // Make the ReaderView act on the change to SearchTaskResult
                        // via overridden onChildSetup method.
                    mDocView.resetupChildren();
                }
            };

            // Reenstate last state if it was recorded
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        mDocView.setDisplayedViewIndex(prefs.getInt("page"+core.getFileName(), 0));
        
            // Stick the document view into a parent view
        RelativeLayout layout = new RelativeLayout(this);
        layout.addView(mDocView);
        setContentView(layout);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case OUTLINE_REQUEST:
                if (resultCode >= 0)
                    mDocView.setDisplayedViewIndex(resultCode);
                break;
            case PRINT_REQUEST:
                if (resultCode == RESULT_CANCELED)
                    showInfo(getString(R.string.print_failed));
                break;
            case FILEPICK_REQUEST:
                if (mFilePicker != null && resultCode == RESULT_OK)
                    mFilePicker.onPick(data.getData());
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public Object onRetainNonConfigurationInstance()
	{
            MuPDFCore mycore = core;
            core = null;
            return mycore;
	}

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        
        outState.putString("ActionBarMode", mActionBarMode.toString());
        outState.putString("AcceptMode", mAcceptMode.toString());
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(core != null) core.onSharedPreferenceChanged(this);
            //mDocView.resetupChildren();//This should be used to set preferences in page views...
    }    

    
    private void printDoc() {
        if (!core.fileFormat().startsWith("PDF")) {
            showInfo(getString(R.string.format_currently_not_supported));
            return;
        }

        Intent myIntent = getIntent();
        Uri docUri = myIntent != null ? myIntent.getData() : null;

        if (docUri == null) {
            showInfo(getString(R.string.print_failed));
        }

        if (docUri.getScheme() == null)
            docUri = Uri.parse("file://"+docUri.toString());

        Intent printIntent = new Intent(this, PrintDialogActivity.class);
        printIntent.setDataAndType(docUri, "aplication/pdf");
        printIntent.putExtra("title", core.getFileName());
        startActivityForResult(printIntent, PRINT_REQUEST);
    }


    // private void shareDoc() {
    //     Intent myIntent = getIntent();
    //     Uri docUri = myIntent != null ? myIntent.getData() : null;

    //     if (docUri == null) {
    //             //showInfo(getString(R.string.print_failed)); //???
    //     }

    //     if (docUri.getScheme() == null)
    //         docUri = Uri.parse("file://"+docUri.toString());

    //         //???
    // }

    private void showInfo(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }    

    private void search(int direction) {
        int displayPage = mDocView.getDisplayedViewIndex();
        SearchTaskResult r = SearchTaskResult.get();
        int searchPage = r != null ? r.pageNumber : -1;
        mSearchTask.go(mQuery, direction, displayPage, searchPage);
    }

	// @Override
	// public boolean onSearchRequested() {
	// 	if (mButtonsVisible && mTopBarMode == TopBarMode.Search) {
	// 		hideButtons();
	// 	} else {
	// 		showButtons();
	// 		searchModeOn();
	// 	}
	// 	return super.onSearchRequested();
	// }

	// @Override
	// public boolean onPrepareOptionsMenu(Menu menu) {
	// 	if (mButtonsVisible && mTopBarMode != TopBarMode.Search) {
	// 		hideButtons();
	// 	} else {
	// 		showButtons();
	// 		searchModeOff();
	// 	}
	// 	return super.onPrepareOptionsMenu(menu);
	// }


    @Override
    public void onBackPressed() {
        if (core.hasChanges()) {
            DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == AlertDialog.BUTTON_POSITIVE) {
                            core.save();
                            mNotSaveOnDestroyThisTime = mNotSaveOnPauseThisTime = true; //No need to save twice
                            finish();
                        }
                        if (which == AlertDialog.BUTTON_NEGATIVE) {
                            mNotSaveOnDestroyThisTime = mNotSaveOnPauseThisTime = true;
                            finish();
                        }
                    }
                };
            AlertDialog alert = mAlertBuilder.create();
            alert.setTitle("MuPDF");
            alert.setMessage(getString(R.string.document_has_changes_save_them_));
            alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.yes), listener);
            alert.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.no), listener);
            alert.show();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void performPickFor(FilePicker picker) {
        mFilePicker = picker;
        Intent intent = new Intent(this, ChoosePDFActivity.class);
        startActivityForResult(intent, FILEPICK_REQUEST);
    }
}
