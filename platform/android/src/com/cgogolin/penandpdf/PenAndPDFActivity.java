package com.cgogolin.penandpdf;
import java.util.concurrent.Callable;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.PropertyValuesHolder;
import android.animation.ObjectAnimator;
import android.animation.LayoutTransition;
import android.app.ActivityManager;
import android.view.animation.OvershootInterpolator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.app.ActionBar;
import android.app.SearchManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.Point;
import android.graphics.drawable.TransitionDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Matrix;
import android.Manifest.permission;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Parcelable;
import android.os.Process;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.v7.widget.Toolbar;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.CardView;
import android.text.Editable;
import android.text.format.Time;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Toast;
import android.widget.ViewAnimator;
import android.widget.TextView;
import android.widget.ImageView;
import java.io.File;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.Runtime;
import java.lang.Math;
import java.lang.Integer;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.Set;
import android.view.Gravity;
import android.view.Display;

class ThreadPerTaskExecutor implements Executor {
    public void execute(Runnable r) {
        new Thread(r).start();
    }
}

public class PenAndPDFActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener, android.support.v7.widget.SearchView.OnQueryTextListener, android.support.v7.widget.SearchView.OnCloseListener, FilePicker.FilePickerSupport, TemporaryUriPermission.TemporaryUriPermissionProvider
{       
    enum ActionBarMode {Main, Annot, Edit, Search, Selection, Hidden, AddingTextAnnot, Empty};
    
    private SearchView searchView = null;
    private String latestTextInSearchBox = "";
    private String textOfLastSearch = "";
    private boolean mSaveOnStop = false;
    private boolean mSaveOnDestroy = false;
    private boolean mIgnoreSaveOnStopThisTime = false;
    private boolean mIgnoreSaveOnDestroyThisTime = false;
    private boolean mDocViewNeedsNewAdapter = false;
    private int mPageBeforeInternalLinkHit = -1;
    private float mNormalizedScaleBeforeInternalLinkHit = 1.0f;
    private float mNormalizedXScrollBeforeInternalLinkHit = 0;
    private float mNormalizedYScrollBeforeInternalLinkHit = 0;
    private int numberRecentFilesInMenu = 20;
    
    private final int    OUTLINE_REQUEST=0;
    private final int    PRINT_REQUEST=1;
    private final int    FILEPICK_REQUEST = 2;
    private final int    SAVEAS_REQUEST=3;
    private final int    EDIT_REQUEST = 4;

    private final int    WRITE_PERMISSION_DURING_RESUME_REQUEST = 1;
    
    private PenAndPDFCore    core;
    private MuPDFReaderView mDocView;
    Parcelable mDocViewParcelable;
    private EditText     mPasswordView;
    private ActionBarMode  mActionBarMode = ActionBarMode.Empty;
    private boolean selectedAnnotationIsEditable = false;
    private SearchTaskManager   mSearchTaskManager;
    private AlertDialog.Builder mAlertBuilder;
    private boolean    mLinkHighlight = false;
    private final Handler mHandler = new Handler();
    private boolean mAlertsActive= false;
    private boolean mReflow = false;
    private AsyncTask<Void,Void,MuPDFAlert> mAlertTask;
    private CancellableAsyncTask<RecentFile, RecentFile> mRenderThumbnailTask = null;
    private AlertDialog mAlertDialog;
    private FilePicker mFilePicker;
    
    private AsyncTask<Callable<Exception>,Void,Exception> mSaveAsOrSaveTask;
    
    private ArrayList<TemporaryUriPermission> temporaryUriPermissions = new ArrayList<TemporaryUriPermission>();

    private boolean mDashboardIsShown = false;
    
		
/**
 * Code from http://stackoverflow.com/questions/13209494/how-to-get-the-full-file-path-from-uri
 * 
 * Get a file path from a Uri. This will get the the path for Storage Access
 * Framework Documents, as well as the _data field for the MediaStore and
 * other file-based ContentProviders.
 *
 * @param context The context.
 * @param uri The Uri to query.
 * @author paulburke
 */
public static String getActualPath(final Context context, final Uri uri) {

    final boolean isKitKat = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT;

    // DocumentProvider
    if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
        // ExternalStorageProvider
        if (isExternalStorageDocument(uri)) {
            final String docId = DocumentsContract.getDocumentId(uri);
            final String[] split = docId.split(":");
            final String type = split[0];

            if ("primary".equalsIgnoreCase(type)) {
                return Environment.getExternalStorageDirectory() + "/" + split[1];
            }

            // TODO handle non-primary volumes
        }
        // DownloadsProvider
        else if (isDownloadsDocument(uri)) {
            final String id = DocumentsContract.getDocumentId(uri);
            try
            {
                final Long idl = Long.valueOf(id);
                final Uri contentUri = ContentUris.withAppendedId(
                    Uri.parse("content://downloads/public_downloads"), idl);
                
                return getDataColumn(context, contentUri, null, null);
            }
            catch(NumberFormatException ex) {
                    //Nothing we can do, just keep trying the other options
            }
        }
        // MediaProvider
        else if (isMediaDocument(uri)) {
            final String docId = DocumentsContract.getDocumentId(uri);
            final String[] split = docId.split(":");
            final String type = split[0];

            Uri contentUri = null;
            if ("image".equals(type)) {
                contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            } else if ("video".equals(type)) {
                contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            } else if ("audio".equals(type)) {
                contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            }

            final String selection = "_id=?";
            final String[] selectionArgs = new String[] {
                    split[1]
            };

            return getDataColumn(context, contentUri, selection, selectionArgs);
        }
    }
    // MediaStore (and general)
    else if ("content".equalsIgnoreCase(uri.getScheme())) {
        return getDataColumn(context, uri, null, null);
    }
    // File
    else if ("file".equalsIgnoreCase(uri.getScheme())) {
        return uri.getPath();
    }
    return uri.getPath();
}

/**
 * Get the value of the data column for this Uri. This is useful for
 * MediaStore Uris, and other file-based ContentProviders.
 *
 * @param context The context.
 * @param uri The Uri to query.
 * @param selection (Optional) Filter used in the query.
 * @param selectionArgs (Optional) Selection arguments used in the query.
 * @return The value of the _data column, which is typically a file path.
 */
public static String getDataColumn(Context context, Uri uri, String selection,
        String[] selectionArgs) {

    Cursor cursor = null;
    final String column = "_data";
    final String[] projection = {
            column
    };

    try {
        cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                null);
        if (cursor != null && cursor.moveToFirst()) {
            final int column_index = cursor.getColumnIndexOrThrow(column);
            return cursor.getString(column_index);
        }
    } finally {
        if (cursor != null)
            cursor.close();
    }
    return null;
}


/**
 * @param uri The Uri to check.
 * @return Whether the Uri authority is ExternalStorageProvider.
 */
public static boolean isExternalStorageDocument(Uri uri) {
    return "com.android.externalstorage.documents".equals(uri.getAuthority());
}

/**
 * @param uri The Uri to check.
 * @return Whether the Uri authority is DownloadsProvider.
 */
public static boolean isDownloadsDocument(Uri uri) {
    return "com.android.providers.downloads.documents".equals(uri.getAuthority());
}

/**
 * @param uri The Uri to check.
 * @return Whether the Uri authority is MediaProvider.
 */
public static boolean isMediaDocument(Uri uri) {
    return "com.android.providers.media.documents".equals(uri.getAuthority());
}
	
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
        destroyAlertWaiter();
        mAlertsActive = true;
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

    
    @Override
    public void onCreate(Bundle savedInstanceState)
        {   
            super.onCreate(savedInstanceState);

				//Initialize the layout
			setContentView(R.layout.main);
			Toolbar myToolbar = (Toolbar)findViewById(R.id.toolbar);
			setSupportActionBar(myToolbar);
			
                //Set default preferences on first start
            PreferenceManager.setDefaultValues(this, SettingsActivity.SHARED_PREFERENCES_STRING, MODE_MULTI_PROCESS, R.xml.preferences, false);
            
            getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, MODE_MULTI_PROCESS).registerOnSharedPreferenceChangeListener(this);
            onSharedPreferenceChanged(getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, MODE_MULTI_PROCESS),""); //Call this once so I don't need to duplicate code
            
                //Get various data from the bundle
            if(savedInstanceState != null)
            {   
                mActionBarMode = ActionBarMode.valueOf(savedInstanceState.getString("ActionBarMode", ActionBarMode.Main.toString ()));
                mPageBeforeInternalLinkHit = savedInstanceState.getInt("PageBeforeInternalLinkHit", mPageBeforeInternalLinkHit);
                mNormalizedScaleBeforeInternalLinkHit = savedInstanceState.getFloat("NormalizedScaleBeforeInternalLinkHit", mNormalizedScaleBeforeInternalLinkHit); 
                mNormalizedXScrollBeforeInternalLinkHit = savedInstanceState.getFloat("NormalizedXScrollBeforeInternalLinkHit", mNormalizedXScrollBeforeInternalLinkHit);
                mNormalizedYScrollBeforeInternalLinkHit = savedInstanceState.getFloat("NormalizedYScrollBeforeInternalLinkHit", mNormalizedYScrollBeforeInternalLinkHit);
                mDocViewParcelable = savedInstanceState.getParcelable("mDocView");

                latestTextInSearchBox = savedInstanceState.getString("latestTextInSearchBox", latestTextInSearchBox);
                invalidateOptionsMenu();
            }
            
			mAlertBuilder = new AlertDialog.Builder(this);
            
                //Get the core saved with onRetainNonConfigurationInstance()
            if (core == null) {
                core = (PenAndPDFCore)getLastCustomNonConfigurationInstance();
                if(core != null) mDocViewNeedsNewAdapter = true;
            }
        }
    
    @Override
    protected void onResume()
        {
            super.onResume();
            
                /*On Android >=v23 we might not be allowed to read all files,
                 * so if we are given a raw path (not a content:// uri we ask
                 * for READ permissions to external storage just in case... */
            if (android.os.Build.VERSION.SDK_INT >= 23 && (android.support.v4.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || android.support.v4.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) )
            {
                requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_PERMISSION_DURING_RESUME_REQUEST);
                return; //onResume() is then called again from onRequestPermissionsResult()
            }
            
			Intent intent = getIntent();

			if (Intent.ACTION_MAIN.equals(intent.getAction()) && core == null)
            {
                    //If showDashboard() is run directly from onResume() the animation doesn't play...
                final Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            showDashboard();
                        }
                    }, 100);
            }
            else if (Intent.ACTION_VIEW.equals(intent.getAction()))
            {		
					//If the core was not restored during onCreat() set it up now
				setupCore();
            
				if (core != null) //OK, so apparently we have a valid pdf open
				{
                        // Try to take permissions
                    tryToTakePersistablePermissions(intent);
                    rememberTemporaryUriPermission(intent);
                        
						//Setup the mDocView
					setupDocView();
					
						//Set the action bar title
					setTitle();
					
						//Setup the mSearchTaskManager
					setupSearchTaskManager();
					
						//Update the recent files list
					SharedPreferences prefs = getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
					SharedPreferences.Editor edit = prefs.edit();
					saveRecentFiles(prefs, edit, core.getUri());
                    edit.apply();
				}
			}
			
			invalidateOptionsMenu();
		}
    
    
    @Override
    protected void onPause() {
        super.onPause();
        
            //Stop searches
        if (mSearchTaskManager != null) mSearchTaskManager.stop();        

        if (core != null)
        {
                //Save the Viewport and update the recent files list
            saveViewport(core.getUri());
                //Stop receiving alerts
            core.stopAlerts();
            destroyAlertWaiter();
        }
    }
    

    @Override
    protected void onStop() {
        super.onStop();
            //Save only during onStop() as this can take some time
        if(core != null && !isChangingConfigurations())
        {
			if(mSaveOnStop && !mIgnoreSaveOnStopThisTime && core.canSaveToCurrentUri(this))
            {
                saveInBackground(null,
                                 new Callable<Void>() {
                                     @Override
                                     public Void call() {
                                         showInfo(getString(R.string.error_saveing));
                                         return null;
                                     }
                                 }
                                 );
            }
        }
        mIgnoreSaveOnStopThisTime = false;

        if(mRenderThumbnailTask!=null) 
        {
            mRenderThumbnailTask.cancel();
            mRenderThumbnailTask = null;
        }
    }
    
    
    @Override
    protected void onDestroy() {//There is no guarantee that this is ever called!!!
        super.onDestroy();
            
		getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, MODE_MULTI_PROCESS).unregisterOnSharedPreferenceChangeListener(this);            
		if(core != null && !isChangingConfigurations())
		{
			SharedPreferences sharedPref = getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, MODE_MULTI_PROCESS);
			if(mSaveOnDestroy && !mIgnoreSaveOnDestroyThisTime && core.canSaveToCurrentUri(this))
			{
                saveInBackground(
                    new Callable() {
                        @Override
                        public Void call() {
                            if(core!=null)
                            {
                                core.onDestroy();
                                core = null;
                            }
                            return null;
                        }
                    },
                    new Callable<Void>() {
                        @Override
                        public Void call() {
                            showInfo(getString(R.string.error_saveing));
                            if(core!=null)
                            {
                                core.onDestroy(); //Destroy even if not saved as we have no choice
                                core = null;
                            }
                            return null;
                        }
                    }
                                 );
			}
		}
        mIgnoreSaveOnDestroyThisTime = false;
		if (mAlertTask != null) {
			mAlertTask.cancel(true);
			mAlertTask = null;
		}
		searchView = null;
	}
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) //Inflates the options menu
        {
            super.onCreateOptionsMenu(menu);

            if(dashboardIsShown())
                mActionBarMode = ActionBarMode.Empty;
            
            MenuInflater inflater = getMenuInflater();
            switch (mActionBarMode)
            {
                case Main:
                    inflater.inflate(R.menu.main_menu, menu);

                        //Enable the delete note item if we have a note open
                    if(getIntent() != null && getIntent().getData() != null && getIntent().getData().getEncodedPath() != null)
                    {
                        File recentFile = new File(Uri.decode(getIntent().getData().getEncodedPath()));
                    
                        if(recentFile != null && recentFile.getAbsolutePath().startsWith(getNotesDir(this).getAbsolutePath()))
                        {
                            MenuItem deleteNoteItem = menu.findItem(R.id.menu_delete_note);
                            if(deleteNoteItem!=null) deleteNoteItem.setVisible(true);
                        }
                    }
                    
                        // Set up the back before link clicked icon
                    MenuItem linkBackItem = menu.findItem(R.id.menu_linkback);
                    if (mPageBeforeInternalLinkHit == -1) linkBackItem.setEnabled(false).setVisible(false);

                        // Set up the print action
                    MenuItem printItem = menu.findItem(R.id.menu_print);
                    if (core == null)
                        printItem.setEnabled(false).setVisible(false);
                    else
                        printItem.setEnabled(true).setVisible(true);
                    
                        // Set up the share action
                    MenuItem shareItem = menu.findItem(R.id.menu_share);
                    if (core == null)
                        shareItem.setEnabled(false).setVisible(false);
                    else
                        shareItem.setEnabled(true).setVisible(true);
                    break;
                case Selection:
                    inflater.inflate(R.menu.selection_menu, menu);
                    break;
                case Annot:
                    inflater.inflate(R.menu.annot_menu, menu);
                    if(mDocView==null || ((PageView)mDocView.getSelectedView()) == null || !((PageView)mDocView.getSelectedView()).canUndo()) {
                        MenuItem undoButton = menu.findItem(R.id.menu_undo);
                        undoButton.setEnabled(false).setVisible(false);
                    }
                    if(mDocView!=null)
                    {
                        MenuItem cancelButton = menu.findItem(R.id.menu_cancel);
                        View cancelButtonActionView = MenuItemCompat.getActionView(cancelButton);
                        ImageButton cancelImageButton = (ImageButton)cancelButtonActionView.findViewById(R.id.cancel_image_button);
                        final MuPDFView pageView = (MuPDFView) mDocView.getSelectedView();

                        if(pageView!=null)
                        {
                            cancelImageButton.setOnClickListener(new OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        showInfo(getString(R.string.long_press_to_delete));
                                    }
                                });
                            cancelImageButton.setOnLongClickListener(new OnLongClickListener() {
                                    @Override
                                    public boolean onLongClick(View v) {
                                        if (pageView != null) {
                                            pageView.deselectText();
                                            pageView.cancelDraw();
                                        }
                                        mDocView.setMode(MuPDFReaderView.Mode.Viewing);
                                        return true;
                                    }
                                });
                        }
                    
                        if(mDocView.getMode() == MuPDFReaderView.Mode.Drawing)
                        {
                            MenuItem drawButton = menu.findItem(R.id.menu_draw);
                            drawButton.setEnabled(false).setVisible(false);
                        }
                        else if(mDocView.getMode() == MuPDFReaderView.Mode.Erasing)
                        {
                            MenuItem eraseButton = menu.findItem(R.id.menu_erase);
                            eraseButton.setEnabled(false).setVisible(false);
                            
                            MenuItem drawButton = menu.findItem(R.id.menu_draw);
                            View drawButtonActionView = MenuItemCompat.getActionView(drawButton);
                            ImageButton drawImageButton = (ImageButton)drawButtonActionView.findViewById(R.id.draw_image_button);
                            drawImageButton.setOnClickListener(new OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        mDocView.setMode(MuPDFReaderView.Mode.Drawing);
                                    }
                                });
                            drawImageButton.setOnLongClickListener(new OnLongClickListener() {
                                    @Override
                                    public boolean onLongClick(View v) {
                                        showInfo("long click");
                                        return true;
                                    }
                                });
                        }
                    }
                    break;
                case Edit:
                    inflater.inflate(R.menu.edit_menu, menu);
                    if(!selectedAnnotationIsEditable){
                        MenuItem editButton = menu.findItem(R.id.menu_edit);
                        editButton.setEnabled(false).setVisible(false);
                    }
                    {
                        MenuItem cancelButton = menu.findItem(R.id.menu_cancel);
                        View cancelButtonActionView = MenuItemCompat.getActionView(cancelButton);
                        ImageButton cancelImageButton = (ImageButton)cancelButtonActionView.findViewById(R.id.cancel_image_button);
                        final MuPDFView pageView = (MuPDFView) mDocView.getSelectedView();
                        cancelImageButton.setOnClickListener(new OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    showInfo(getString(R.string.long_press_to_delete));
                                }
                            });
                        cancelImageButton.setOnLongClickListener(new OnLongClickListener() {
                                @Override
                                public boolean onLongClick(View v) {
                                    if (pageView != null)
                                        pageView.deleteSelectedAnnotation();
                                    mDocView.setMode(MuPDFReaderView.Mode.Viewing);
                                return true;
                                }
                            });
                    }
                    break;
                case Search:
                    inflater.inflate(R.menu.search_menu, menu);
                        // Associate searchable configuration with the SearchView
                    SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
					MenuItem searchItem = menu.findItem(R.id.menu_search_box);
					searchView = (android.support.v7.widget.SearchView)MenuItemCompat.getActionView(searchItem);
                    searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
                    searchView.setIconified(false);
                    searchView.setOnCloseListener(this); //Implemented in: public void onClose(View view)
                    searchView.setOnQueryTextListener(this); //Implemented in: public boolean onQueryTextChange(String query) and public boolean onQueryTextSubmit(String query)
                    textOfLastSearch = "";
                    searchView.setQuery(latestTextInSearchBox, true); //Set the query text and submit it to perform a search
                case Hidden:
                    inflater.inflate(R.menu.empty_menu, menu);
                    break;
                case AddingTextAnnot:
                    inflater.inflate(R.menu.add_text_annot_menu, menu);
                    break;
				case Empty:
					inflater.inflate(R.menu.empty_menu, menu);
					break;
                default:
            }
            return true;
        }

    @Override
    public boolean onClose() {
        hideKeyboard();
        textOfLastSearch = "";
        searchView.setQuery("", false);
        mDocView.clearSearchResults();
        mDocView.resetupChildren();
		mDocView.setMode(MuPDFReaderView.Mode.Viewing);
        mActionBarMode = ActionBarMode.Main;
        invalidateOptionsMenu();
        return false;
    }
    
    @Override
    public boolean onQueryTextChange(String query) {//Called when string in search box has changed
            //This is a hacky way to determine when the user has reset the text field with the X button 
        if (query.length() == 0 && latestTextInSearchBox.length() > 1) {
            if (mSearchTaskManager != null) mSearchTaskManager.stop();
            textOfLastSearch = "";
            if(mDocView.hasSearchResults())
            {
                mDocView.clearSearchResults();
                mDocView.resetupChildren();
            }
        }
        latestTextInSearchBox = query;
        return false;
    }

    @Override 
    public boolean onQueryTextSubmit(String query) {//For search
        mDocView.requestFocus();
        hideKeyboard();
        if(!query.equals(textOfLastSearch)) //only perform a search if the query has changed    
            search(1);
        return true; //We handle this here and don't want onNewIntent() to be called
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) { //Handel clicks in the options menu
        switch (item.getItemId()) 
        {
            case R.id.menu_addpage:
                    //Insert a new blank page at the end
                if(core!=null && mDocView!=null)
                {
                    core.insertBlankPageAtEnd();
                    invalidateOptionsMenu();
                        //Display the newly inserted page
                    mDocView.setDisplayedViewIndex(core.countPages()-1, true);
                    mDocView.setScale(1.0f);
                    mDocView.setNormalizedScroll(0.0f,0.0f);
                }
                return true;
            case R.id.menu_fullscreen:
                enterFullscreen();
                return true;
            case R.id.menu_settings:
                Intent intent = new Intent(this,SettingsActivity.class);
                startActivity(intent);
				overridePendingTransition(R.animator.enter_from_left, R.animator.fade_out);
                return true;
            case R.id.menu_draw:
                if(mDocView!=null)
                    mDocView.setMode(MuPDFReaderView.Mode.Drawing);
                return true;
            case R.id.menu_print:
                printDoc();
                return true;
            case R.id.menu_share:
                shareDoc();
                return true;
            case R.id.menu_search:
                if(mDocView==null) return true;
                mActionBarMode = ActionBarMode.Search;
                mDocView.setMode(MuPDFReaderView.Mode.Searching);
                invalidateOptionsMenu();
                return true;
            case R.id.menu_next:
                if (!latestTextInSearchBox.equals(""))
                {
                    hideKeyboard();
                    search(1);
                }
                return true;
            case R.id.menu_previous:
                if (!latestTextInSearchBox.equals(""))
                {
                    hideKeyboard();
                    search(-1);
                }
                return true;
            case R.id.menu_open:
                showDashboard();
                return true;
            case R.id.menu_delete_note:
                core.deleteDocument(this);
                Intent restartIntent = new Intent(this, PenAndPDFActivity.class);
                restartIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                restartIntent.setAction(Intent.ACTION_MAIN);
                startActivity(restartIntent);
                finish();
                return true;
            case R.id.menu_save:
                if (android.os.Build.VERSION.SDK_INT >= 23 && (android.support.v4.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) )
                    requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                
                DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            if (which == AlertDialog.BUTTON_POSITIVE) {
                                if(core != null)
                                {
                                    saveInBackground(
                                        new Callable<Void>() {
                                            @Override
                                            public Void call() {
                                                setTitle();
                                                return null;
                                            }
                                        },
                                        new Callable<Void>() {
                                            @Override
                                            public Void call() {
                                                showInfo(getString(R.string.error_saveing));
                                                return null;
                                            }
                                        }
                                                     );
                                }
                            }
                            if (which == AlertDialog.BUTTON_NEUTRAL) {
                            }
                            if (which == AlertDialog.BUTTON_NEGATIVE) {
								showSaveAsActivity();
                            }
                        }
                    };
                AlertDialog alert = mAlertBuilder.create();
				alert.setTitle(getString(R.string.save));
                alert.setMessage(getString(R.string.how_do_you_want_to_save));
                if (core != null && core.canSaveToCurrentUri(this))
                    alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.save), listener);
                alert.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.saveas), listener);
                alert.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.cancel), listener);
                alert.show();
                return true;
            case R.id.menu_gotopage:
                showGoToPageDialoge();
                return true;
            case R.id.menu_linkback:
                if(mPageBeforeInternalLinkHit>=0)
                    setViewport(mPageBeforeInternalLinkHit,mNormalizedScaleBeforeInternalLinkHit, mNormalizedXScrollBeforeInternalLinkHit, mNormalizedYScrollBeforeInternalLinkHit);
                mPageBeforeInternalLinkHit = -1;
                invalidateOptionsMenu();
                return true;
        }

        if(mDocView!=null)
        {
            MuPDFView pageView = (MuPDFView) mDocView.getSelectedView();
            if(pageView!=null)
            {
                switch (item.getItemId()) 
                {
                    case R.id.menu_undo:
                        pageView.undoDraw();
                        mDocView.onNumberOfStrokesChanged(pageView.getDrawingSize());
                        return true;
                    case R.id.menu_edit:
                        ((MuPDFPageView)pageView).editSelectedAnnotation();
                        mDocView.setMode(MuPDFReaderView.Mode.Drawing);
                        return true;
                    case R.id.menu_add_text_annot:
                        mDocView.setMode(MuPDFReaderView.Mode.AddingTextAnnot);
                        showInfo(getString(R.string.tap_to_add_annotation));
                        return true;
                    case R.id.menu_erase:
                        mDocView.setMode(MuPDFReaderView.Mode.Erasing);
                        return true;
                    case R.id.menu_highlight:
                        if (pageView.hasSelection()) {
                            pageView.markupSelection(Annotation.Type.HIGHLIGHT);
                            mDocView.setMode(MuPDFReaderView.Mode.Viewing);
                        }
                        else
                            showInfo(getString(R.string.select_text));
                        return true;
                    case R.id.menu_underline:
                        if (pageView.hasSelection()) {
                            pageView.markupSelection(Annotation.Type.UNDERLINE);
                            mDocView.setMode(MuPDFReaderView.Mode.Viewing);
                        }
                        else
                            showInfo(getString(R.string.select_text));
                        return true;
                    case R.id.menu_strikeout:
                        if (pageView.hasSelection()) {
                            pageView.markupSelection(Annotation.Type.STRIKEOUT);
                            mDocView.setMode(MuPDFReaderView.Mode.Viewing);
                        }
                        else
                            showInfo(getString(R.string.select_text));
                        return true;
                    case R.id.menu_copytext:
                        if (pageView.hasSelection()) {
                            boolean success = pageView.copySelection();
                            showInfo(success?getString(R.string.copied_to_clipboard):getString(R.string.no_text_selected));
                            mDocView.setMode(MuPDFReaderView.Mode.Viewing);
                        }
                        else
                            showInfo(getString(R.string.select_text));
                        return true;
                    case R.id.menu_cancel:
                        switch (mActionBarMode) {
                            case Search:
                                hideKeyboard();
                                if (mSearchTaskManager != null) mSearchTaskManager.stop();
                                mDocView.setMode(MuPDFReaderView.Mode.Viewing);
                                mDocView.clearSearchResults();
                                mDocView.resetupChildren();
                                break;
                            case AddingTextAnnot:
                                mDocView.setMode(MuPDFReaderView.Mode.Viewing);
                                break;
                        }
                        mDocView.setMode(MuPDFReaderView.Mode.Viewing);
                        return true;
                    case R.id.menu_accept:
                        switch (mActionBarMode) {
                            case Annot:
                                if (pageView != null) {
                                    pageView.saveDraw();
                                }
                                break;
                            case Edit:
                                if (pageView != null)
                                    pageView.deselectAnnotation();
                                break;
                        }
                        mDocView.setMode(MuPDFReaderView.Mode.Viewing);
                        return true;
                    default:
                        return super.onOptionsItemSelected(item);
                }
            }
        }
        
    }

	private void tryToTakePersistablePermissions(Intent intent) {
        Uri uri = intent.getData();
		if (android.os.Build.VERSION.SDK_INT >= 19)
		{
			try
			{
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
			}
			catch(Exception e)
			{
					//Nothing we can do if we don't get the permission
			}
            finally
            {
                try
                {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                }
                catch(Exception e)
                {
                        //Nothing we can do if we don't get the permission
                    Log.i(getString(R.string.app_name), "Failed to take persistable write uri permissions for "+uri+" Exception: "+e);
                }
            }
		}
	}
	

    public void setupCore() {//Called during onResume()		
        if (core == null) {            
            mDocViewNeedsNewAdapter = true;
            Intent intent = getIntent();
			
            Uri uri = intent.getData();
            
            try 
            {
                core = new PenAndPDFCore(this, uri);
                if(core == null) throw new Exception(getResources().getString(R.string.unable_to_interpret_uri)+" "+uri);
            }
            catch (Exception e)
            {
                AlertDialog alert = mAlertBuilder.create();
                alert.setTitle(R.string.cannot_open_document);
                alert.setMessage(getResources().getString(R.string.reason)+": "+e.toString());
                alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dismiss),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                    }
                                });
                alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        public void onDismiss(DialogInterface dialog) {
                        }
                    });
                alert.show();
                core = null;
            }
			
            if (core != null && core.needsPassword()) {
                requestPassword();
            }
            if (core != null && core.countPages() == 0) {
                core = null;
            }
            if (core != null) {
                    /*There seems to be some bug in this that sometimes make the native code lock up. As it is not so important I am disabeling this for now*/
                    //Start receiving alerts
                // createAlertWaiter();
                // core.startAlerts();
                
                    //Make the core read the current preferences
                SharedPreferences prefs = getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
                core.onSharedPreferenceChanged(prefs,"");
            }    
        }
    }
    
        
    public void setupSearchTaskManager() { //Is called during onResume()
            //Create a new search task (the core might have changed)
		mSearchTaskManager = new SearchTaskManager(this, core) {
                    @Override
                    protected void onTextFound(SearchResult result) {
                        mDocView.addSearchResult(result);
                    }
                    
                    @Override
                    protected void goToResult(SearchResult result) {
                            //Make the docview show the hits
                        mDocView.resetupChildren();
                            // Ask the ReaderView to move to the resulting page
                        if(mDocView.getSelectedItemPosition() != result.getPageNumber())
                            mDocView.setDisplayedViewIndex(result.getPageNumber());
                            // ... and the region on the page
                        RectF resultRect = result.getFocusedSearchBox();
                        if(resultRect!=null)
                        {
                            mDocView.doNextScrollWithCenter();
                            mDocView.setDocRelXScroll(resultRect.left);
                            mDocView.setDocRelYScroll(resultRect.top);
                        }
                    }
                };
    }
    
    public void setupDocView() { //Is called during onResume()
            //If we don't even have a core there is nothing to do
        if(core == null) return;            
            //If the doc view is not present create it
        if(mDocView == null)
        {
            mDocView = new MuPDFReaderView(this) {
                    
                    @Override
                    public void setMode(Mode m) {
                        super.setMode(m);

                        switch(m)
                        {
                            case Viewing:
                                mActionBarMode = ActionBarMode.Main;
                                break;
                            case Drawing:
                            case Erasing:
                                mActionBarMode = ActionBarMode.Annot;
                                break;
                            case Selecting:
                                mActionBarMode = ActionBarMode.Selection;
                                break;
                            case AddingTextAnnot:
                                mActionBarMode = ActionBarMode.AddingTextAnnot;
                                break;
                        }
                        invalidateOptionsMenu();
                    }
                    
                    @Override
                    protected void onMoveToChild(int pageNumber) {
                        setTitle();
                            //We deselect annotations on page changes so let the action bar act accordingly
                        if(mActionBarMode == ActionBarMode.Edit)
                        {
                            mActionBarMode = ActionBarMode.Main;
                            invalidateOptionsMenu();
                        }
                    }

                    @Override
                    protected void onTapMainDocArea() {
                        if (mActionBarMode == ActionBarMode.Edit || mActionBarMode == ActionBarMode.AddingTextAnnot) 
                        {
                            mActionBarMode = ActionBarMode.Main;
                            invalidateOptionsMenu();
                        }
                    }
                
                    @Override
                    protected void onTapTopLeftMargin() {
                        if (getSupportActionBar().isShowing())
                            smartMoveBackwards();
                        else {
                            mDocView.setDisplayedViewIndex(getSelectedItemPosition()-1);
                            mDocView.setScale(1.0f);
                            mDocView.setNormalizedScroll(0.0f,0.0f);
                        }
                    };

                    @Override
                    protected void onBottomRightMargin() {
                        if (getSupportActionBar().isShowing())
                            smartMoveForwards();
                        else {
                            mDocView.setDisplayedViewIndex(getSelectedItemPosition()+1);
                            mDocView.setScale(1.0f);
                            mDocView.setNormalizedScroll(0.0f,0.0f);
                        }
                    };
                
                    @Override
                    protected void onDocMotion() {

                    }

                    @Override
                    protected void addTextAnnotFromUserInput(final Annotation annot) {

						mAlertDialog = mAlertBuilder.create();
                        final LinearLayout editTextLayout = new LinearLayout(mAlertDialog.getContext());
                        editTextLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                        editTextLayout.setOrientation(LinearLayout.VERTICAL);
                        editTextLayout.setPadding(16, 16, 16, 0);//should not be hard coded
                        final EditText input = new EditText(editTextLayout.getContext());
                        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_NORMAL|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                        input.setHint(getString(R.string.add_a_note));
                        input.setBackgroundDrawable(null);
                        if(annot != null && annot.text != null) input.setText(annot.text);
                        editTextLayout.addView(input);
                        mAlertDialog.setView(editTextLayout);
                        mAlertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.save), new DialogInterface.OnClickListener() 
                                {
                                    public void onClick(DialogInterface dialog, int whichButton) 
                                        {
                                            ((MuPDFPageView)getSelectedView()).deleteSelectedAnnotation();
                                            annot.text = input.getText().toString();
                                            addTextAnnotion(annot);
                                            mAlertDialog.setOnCancelListener(null);
                                        }
                            });
                        mAlertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.cancel), new DialogInterface.OnClickListener() 
                                {public void onClick(DialogInterface dialog, int whichButton)
                                        {
                                            ((MuPDFPageView)getSelectedView()).deselectAnnotation();
                                            mAlertDialog.setOnCancelListener(null);
                                        }
                            });
                        if(annot != null && annot.text != null)
                            mAlertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.delete), new DialogInterface.OnClickListener() 
                                {public void onClick(DialogInterface dialog, int whichButton)
                                        {
                                            ((MuPDFPageView)getSelectedView()).deleteSelectedAnnotation();
                                            mAlertDialog.setOnCancelListener(null);
                                        }
                                });
                        mAlertDialog.setCanceledOnTouchOutside(true);
                        mAlertDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                                public void onCancel(DialogInterface dialog) {
                                    ((MuPDFPageView)getSelectedView()).deselectAnnotation();
                                }
                            });
                        mAlertDialog.show();
                        input.requestFocus();
                    }

                    @Override
                    protected void onHit(Hit item) {
                        switch(item){
                            case Annotation:
                            case InkAnnotation:
                                mActionBarMode = ActionBarMode.Edit;
                                invalidateOptionsMenu();
                                selectedAnnotationIsEditable = ((MuPDFPageView)getSelectedView()).selectedAnnotationIsEditable();
                                break;
                            case TextAnnotation:
                                break;
                            case Nothing:
                                if(mActionBarMode != ActionBarMode.Search && mActionBarMode != ActionBarMode.Hidden)
                                {
                                    mActionBarMode = ActionBarMode.Main;
                                    invalidateOptionsMenu();
                                }
                                break;
                            case LinkInternal:
                                if(mDocView.linksEnabled()) {
                                    mPageBeforeInternalLinkHit = getSelectedItemPosition();
                                    mNormalizedScaleBeforeInternalLinkHit = getNormalizedScale();
                                    mNormalizedXScrollBeforeInternalLinkHit = getNormalizedXScroll();
                                    mNormalizedYScrollBeforeInternalLinkHit = getNormalizedYScroll();
                                }
                                mActionBarMode = ActionBarMode.Main;
                                invalidateOptionsMenu();
                                break;
                        }
                    }

                    @Override
                    protected void onNumberOfStrokesChanged(int numberOfStrokes) {
                        invalidateOptionsMenu();
                    }
                
                };
			
            mDocViewNeedsNewAdapter = true;

				//Make content appear below the toolbar if completely zoomed out
            TypedValue tv = new TypedValue();
            if(getSupportActionBar().isShowing() && getTheme().resolveAttribute(android.support.v7.appcompat.R.attr.actionBarSize, tv, true)) {
                int actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data,getResources().getDisplayMetrics());
                mDocView.setPadding(0, actionBarHeight, 0, 0);
                mDocView.setClipToPadding(false);
            }
            
                //Make the doc view visible
			RelativeLayout layout = (RelativeLayout)findViewById(R.id.main_layout);
			layout.addView(mDocView, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            hideDashboard();
        }
        if(mDocView!=null)
        {
				//Synchronize modes of DocView and ActionBar 
            mDocView.setMode(mDocView.getMode());
			
                //Clear the search results 
            mDocView.clearSearchResults();  
            
                //Ascociate the mDocView with a new adapter if necessary
            if(mDocViewNeedsNewAdapter) {
                mDocView.setAdapter(new MuPDFPageAdapter(this, this, core));
                mDocViewNeedsNewAdapter = false;
            }
			
                //Reinstate last viewport if it was recorded
            restoreViewport();

                //Restore the state of mDocView from its saved state in case there is one
            if(mDocViewParcelable != null) mDocView.onRestoreInstanceState(mDocViewParcelable);
            mDocViewParcelable=null;
            
                //Make the mDocView read the prefernces 
            SharedPreferences prefs = getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);   
            mDocView.onSharedPreferenceChanged(prefs,"");
        }
    }

	private void hideDashboard() {
        final ScrollView entryScreenScrollView = (ScrollView)findViewById(R.id.entry_screen_scroll_view);
        LinearLayout entryScreenLayout = (LinearLayout)findViewById(R.id.entry_screen_layout);
        if(entryScreenLayout.getChildCount() > 0)
            entryScreenLayout.removeViews(0,entryScreenLayout.getChildCount());
        mActionBarMode = ActionBarMode.Main;
        mDashboardIsShown = false;
        invalidateOptionsMenu();
        TransitionDrawable transition = (TransitionDrawable) entryScreenScrollView.getBackground();
        int animationTime = (int)entryScreenLayout.getLayoutTransition().getDuration(LayoutTransition.DISAPPEARING);
        transition.reverseTransition(animationTime);
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    entryScreenScrollView.setVisibility(View.INVISIBLE);
                }
            }, animationTime);
    }

    
    private boolean dashboardIsShown() {
        return mDashboardIsShown;
    }

    
	private void showDashboard() {
        if(dashboardIsShown())
            return;
        mDashboardIsShown = true;
        
        mActionBarMode = ActionBarMode.Empty;
        invalidateOptionsMenu();

        final ScrollView entryScreenScrollView = (ScrollView)findViewById(R.id.entry_screen_scroll_view);
        final LinearLayout entryScreenLayout = (LinearLayout)findViewById(R.id.entry_screen_layout);
        entryScreenLayout.removeAllViews();
        entryScreenScrollView.scrollTo(0, 0);
        
        Animator scrollUp = ObjectAnimator.ofPropertyValuesHolder((Object)null, PropertyValuesHolder.ofFloat("translationY", entryScreenScrollView.getHeight(), 0));
        scrollUp.setInterpolator(new AccelerateDecelerateInterpolator());
        Animator scrollDown = ObjectAnimator.ofPropertyValuesHolder((Object)null, PropertyValuesHolder.ofFloat("translationY", 0, entryScreenScrollView.getHeight()));
        scrollDown.setInterpolator(new AccelerateDecelerateInterpolator());

        LayoutTransition layoutTransition;
        layoutTransition = new LayoutTransition();
        layoutTransition.setAnimator(LayoutTransition.APPEARING, scrollUp);
        layoutTransition.setAnimator(LayoutTransition.DISAPPEARING, scrollDown);
        entryScreenLayout.setLayoutTransition(layoutTransition);

        entryScreenScrollView.setVisibility(View.VISIBLE);
        
        TransitionDrawable transition = (TransitionDrawable) entryScreenScrollView.getBackground();
        int animationTime = (int)entryScreenLayout.getLayoutTransition().getDuration(LayoutTransition.DISAPPEARING);

        transition.startTransition(animationTime);
        
        
        SharedPreferences prefs = getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
        int elevation = 5;
        int elevationInc = 5;
        CardView fixedcard;
        ImageView icon;
        TextView title;
        TextView subtitle;

        fixedcard = (CardView)getLayoutInflater().inflate(R.layout.dashboard_card, entryScreenLayout, false);
        icon = (ImageView)fixedcard.findViewById(R.id.image);
        title = (TextView)fixedcard.findViewById(R.id.title);
        subtitle = (TextView)fixedcard.findViewById(R.id.subtitle);
        icon.setImageResource(R.drawable.ic_open);
        title.setText(R.string.entry_screen_open_document);
        subtitle.setText(R.string.entry_screen_open_document_summ);
        fixedcard.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					openDocument();
				}
			});
        fixedcard.setCardElevation(elevation);
        entryScreenLayout.addView(fixedcard);
        
        fixedcard = (CardView)getLayoutInflater().inflate(R.layout.dashboard_card, entryScreenLayout, false);
        icon = (ImageView)fixedcard.findViewById(R.id.image);
        title = (TextView)fixedcard.findViewById(R.id.title);
        subtitle = (TextView)fixedcard.findViewById(R.id.subtitle);
        icon.setImageResource(R.drawable.ic_new);
        title.setText(R.string.entry_screen_new_document);
        subtitle.setText(R.string.entry_screen_new_document_summ);
        fixedcard.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
                    showOpenNewDocumentDialoge();
				}
			});
        fixedcard.setCardElevation(elevation);
        entryScreenLayout.addView(fixedcard);
        
        fixedcard = (CardView)getLayoutInflater().inflate(R.layout.dashboard_card, entryScreenLayout, false);
        icon = (ImageView)fixedcard.findViewById(R.id.image);
        title = (TextView)fixedcard.findViewById(R.id.title);
        subtitle = (TextView)fixedcard.findViewById(R.id.subtitle);
        icon.setImageResource(R.drawable.ic_settings);
        title.setText(R.string.entry_screen_settings);
        subtitle.setText(R.string.entry_screen_settings_summ);
        fixedcard.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View view) {
					Intent intent = new Intent(view.getContext(), SettingsActivity.class);
					view.getContext().startActivity(intent);
					overridePendingTransition(R.animator.enter_from_left, R.animator.fade_out);
				}
			});
        fixedcard.setCardElevation(elevation);
        entryScreenLayout.addView(fixedcard);

        boolean beforeFirstCard = true;
        int cardNumber = 0;
        RecentFilesList recentFilesList = new RecentFilesList(getApplicationContext(), prefs);
        for(final RecentFile recentFile: recentFilesList) {
            cardNumber++;
            if(cardNumber > numberRecentFilesInMenu) break;
            
            if (!PenAndPDFCore.canReadFromUri(this, recentFile.getUri()))
                continue;

            if(beforeFirstCard)
            {
                final CardView recentFilesListHeading = (CardView)getLayoutInflater().inflate(R.layout.dashboard_recent_files_list_heading, entryScreenLayout, false);
                entryScreenLayout.addView(recentFilesListHeading);
                beforeFirstCard = false;
            }
            
            final CardView card = (CardView)getLayoutInflater().inflate(R.layout.dashboard_card_recent_file, entryScreenLayout, false);

            elevation += elevationInc;
            card.setCardElevation(elevation);
            TextView tv = (TextView)card.findViewById(R.id.title);
            tv.setText(recentFile.getDisplayName());
            
            card.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        checkSaveThenCall(new Callable<Void>(){
                                public Void call() {
                                    Intent intent = new Intent(Intent.ACTION_VIEW, recentFile.getUri(), card.getContext(), PenAndPDFActivity.class);
                                    intent.putExtra(Intent.EXTRA_TITLE, recentFile.getDisplayName());
                                    startActivity(intent);
                                    overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                                    hideDashboard();
                                    finish();
                                    return null;
                                }});
                    }
                });

            AsyncTask<RecentFile,Void,BitmapDrawable> setRecentFileThumbnailTask = new AsyncTask<RecentFile,Void,BitmapDrawable>() {
                @Override
                protected BitmapDrawable doInBackground(RecentFile... recentFile0) {
                    if(memoryLow())
                        return null;
                    
                    RecentFile recentFile = recentFile0[0];
                    PdfThumbnailManager pdfThumbnailManager = new PdfThumbnailManager(card.getContext());
                    return pdfThumbnailManager.getDrawable(getResources(), recentFile.getThumbnailString());
                }
                @Override
                protected void onPostExecute(BitmapDrawable drawable) {
                    if(drawable!=null) 
                    {
                        ImageView imageView = (ImageView)card.findViewById(R.id.image);
                        imageView.setImageDrawable(drawable);
                        final Matrix matrix = imageView.getImageMatrix();
                        final float imageWidth = drawable.getIntrinsicWidth();
                        final int screenWidth = entryScreenLayout.getWidth();
                        final float scaleRatio = screenWidth / imageWidth;
                        matrix.postScale(scaleRatio, scaleRatio);
                        imageView.setImageMatrix(matrix);
                    }

                    entryScreenLayout.addView(card);
                }
            };
            setRecentFileThumbnailTask.execute(recentFile);
        }
	}
    
    
    public void showOpenDocumentDialog() {
		Intent intent = null;
		if (android.os.Build.VERSION.SDK_INT < 19)
        {
			intent = new Intent(this, PenAndPDFFileChooser.class);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
			intent.setAction(Intent.ACTION_EDIT);
		}
		else
		{
            Intent openDocumentIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            openDocumentIntent.addCategory(Intent.CATEGORY_OPENABLE);
            openDocumentIntent.setType("application/pdf");
            openDocumentIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
			intent = openDocumentIntent;
		}
		
		startActivityForResult(intent, EDIT_REQUEST);
		overridePendingTransition(R.animator.enter_from_left, R.animator.fade_out);
	}

    
    public void openNewDocument(final String filename) throws java.io.IOException {		
        File dir = getNotesDir(this);
		File file = new File(dir, filename);
		final Uri uri = Uri.fromFile(file);
		
		PenAndPDFCore.createEmptyDocument(this, uri);

        checkSaveThenCall(new Callable<Void>(){
                public Void call() {
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri, getApplicationContext(), PenAndPDFActivity.class);
                    intent.putExtra(Intent.EXTRA_TITLE, filename);
                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    startActivity(intent);
                    overridePendingTransition(R.animator.fade_in, R.animator.fade_out);
                    hideDashboard();
                    finish();
                    return null;
                }});
	}

    
    public void openDocument() {
        checkSaveThenCall(new Callable<Void>(){
                public Void call() {
                    showOpenDocumentDialog();
                    return null;
                }});
    }
    
    public void checkSaveThenCall(final Callable callable) {
		if (core!=null && core.hasChanges()) {
            final PenAndPDFActivity activity = this;
            
			DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						if (which == AlertDialog.BUTTON_POSITIVE) {
							if(core.canSaveToCurrentUri(activity))
							{
                                saveInBackground(callable,
                                                 new Callable<Void>() {
                                                     @Override
                                                     public Void call() {
                                                         showInfo(getString(R.string.error_saveing));
                                                         return null;
                                                     }
                                                 }
                                                 );
							}
							else
							{
								showSaveAsActivity();
							}
						}
						if (which == AlertDialog.BUTTON_NEGATIVE) {
                            try{callable.call();}catch(Exception e){}
						}
						if (which == AlertDialog.BUTTON_NEUTRAL) {
						}
					}
                    };
			AlertDialog alert = mAlertBuilder.create();
			alert.setTitle(getString(R.string.save_question));
			alert.setMessage(getString(R.string.document_has_changes_save_them));
			if (core.canSaveToCurrentUri(this))
				alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.yes), listener);
			else
				alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.saveas), listener);
			alert.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.cancel), listener);
			alert.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.no), listener);
			alert.show();
		}
		else
		{
            try{callable.call();}catch(Exception e){}
		}
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {        
        switch (requestCode) {
            case EDIT_REQUEST:
                overridePendingTransition(R.animator.fade_in, R.animator.exit_to_left);
                if(resultCode == AppCompatActivity.RESULT_OK)
                {
                    if (intent != null) {
                        getIntent().setAction(Intent.ACTION_VIEW);
                        getIntent().setData(intent.getData());
                        getIntent().setFlags((getIntent().getFlags() & ~Intent.FLAG_GRANT_WRITE_URI_PERMISSION & ~Intent.FLAG_GRANT_READ_URI_PERMISSION) | (intent.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) | (intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION));//Set the read and writ flags to what they are in the received intent
                        
                        if (core != null) {
                            core.onDestroy();
                            core = null;
                        }
//                        tryToTakePersistablePermissions(intent);//No need to do this, is done during onResume()
//                        rememberTemporaryUriPermission(intent);//No need to do this, is done during onResume()
//                        onResume();//New core and new docview are setup during onResume(), which is automatically called after onActivityResult()
                        hideDashboard();
                    }
                }
                break;
            case OUTLINE_REQUEST:
                if (resultCode >= 0 && mDocView!=null)
                    mDocView.setDisplayedViewIndex(resultCode);
                break;
            case PRINT_REQUEST:
                // if (resultCode == RESULT_CANCELED)
                //     showInfo(getString(R.string.print_failed));
                break;
            case SAVEAS_REQUEST:
                overridePendingTransition(R.animator.fade_in, R.animator.exit_to_left);
                if (resultCode == RESULT_OK) {
                    final Uri uri = intent.getData();
                    File file = null;
                    if (uri!=null)
                        file = new File(getActualPath(this, uri));
					if(file != null && file.isFile() && file.length() > 0) //Warn if file already exists
                    {
                        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    if (which == AlertDialog.BUTTON_POSITIVE) {
                                        saveAsInBackground(uri,
                                                           new Callable<Void>() {
                                                               @Override
                                                               public Void call() {
                                                                   setTitle();
                                                                   return null;
                                                               }
                                                           },
                                                           new Callable<Void>() {
                                                               @Override
                                                               public Void call() {
                                                                   showInfo(getString(R.string.error_saveing));
                                                                   return null;
                                                               }
                                                           }
                                                           );
                                    }
                                    if (which == AlertDialog.BUTTON_NEGATIVE) {
                                    }
                                }
                            };
                        AlertDialog alert = mAlertBuilder.create();
                        alert.setTitle(R.string.overwrite_question);
                        alert.setMessage(getString(R.string.overwrite)+" "+uri.getPath()+" ?");
                        alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.yes), listener);
                        alert.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.no), listener);
                        alert.show();
                    }
                    else
                    {
                        if(uri == null)
                            showInfo(getString(R.string.error_saveing));
                        else
                        {
                            saveAsInBackground(uri,
                                               new Callable<Void>() {
                                                   @Override
                                                   public Void call() {
                                                       setTitle();
                                                       return null;
                                                   }
                                               },
                                               new Callable<Void>() {
                                                   @Override
                                                   public Void call() {
                                                       showInfo(getString(R.string.error_saveing));
                                                       return null;
                                                   }
                                               }
                                               );         
                        }
                    }
                }
        }
        super.onActivityResult(requestCode, resultCode, intent);
    }

    private void showSaveAsActivity() {
        if(core == null)
            return;
        if (android.os.Build.VERSION.SDK_INT < 19)
        {
            Intent intent = new Intent(getApplicationContext(),PenAndPDFFileChooser.class);
            if (core.getUri() != null) intent.setData(core.getUri());
            intent.putExtra(Intent.EXTRA_TITLE, core.getFileName());
            intent.setAction(Intent.ACTION_PICK);
            mIgnoreSaveOnStopThisTime = true;
            startActivityForResult(intent, SAVEAS_REQUEST);
        }
        else
        {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                                    
                // Filter to only show results that can be "opened", such as
                // a file (as opposed to a list of contacts or timezones).
            intent.addCategory(Intent.CATEGORY_OPENABLE);
                                    
                // Create a file with the requested MIME type.
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_TITLE, core.getFileName());
            mIgnoreSaveOnStopThisTime = true;
            startActivityForResult(intent, SAVEAS_REQUEST);
            overridePendingTransition(R.animator.enter_from_left, R.animator.fade_out);
        }
    }

    private void saveAsInBackground(final Uri uri, final Callable successCallable, final Callable failureCallable) {
        callInBackgroundAndShowDialog(getString(R.string.saving),
                                 new Callable<Exception>() {
                                     @Override
                                     public Exception call() {
                                         return saveAs(uri);
                                     }
                                 },
                                 successCallable, failureCallable);
    }
    
    private void saveInBackground(final Callable successCallable, final Callable failureCallable) {
        callInBackgroundAndShowDialog(getString(R.string.saving),
                                 new Callable<Exception>() {
                                     @Override
                                     public Exception call() {
                                         return save();
                                     }
                                 },
                                 successCallable, failureCallable);
    }

    private void callInBackgroundAndShowDialog(final String messege, final Callable<Exception> saveCallable, final Callable successCallable, final Callable failureCallable) {
        final AlertDialog waitWhileSavingDialog = mAlertBuilder.create();
        waitWhileSavingDialog.setTitle(messege);
        waitWhileSavingDialog.setCancelable(false);
        waitWhileSavingDialog.setCanceledOnTouchOutside(false);
        final ProgressBar busyIndicator = new ProgressBar(this);
        busyIndicator.setIndeterminate(true);
        int extraSpace = dpToPixel(20);
        waitWhileSavingDialog.setView(busyIndicator, 0, extraSpace, 0, extraSpace);

        mSaveAsOrSaveTask = new AsyncTask<Callable<Exception>,Void,Exception>() {
                @Override
                protected void onPreExecute() {
                    if(!isFinishing()) waitWhileSavingDialog.show();
                }
                @Override
                protected Exception doInBackground(Callable<Exception>... saveCallable0) {
                    Callable<Exception> saveCallable = saveCallable0[0];
//                    try{Thread.sleep(2000);}catch(Exception e){}//ONLY FOR DEBUGGING REMOVE THIS!
                    try{
                        return saveCallable.call();
                    }
                    catch(Exception e)
                    {
                        return e;
                    }
                }
                @Override
                protected void onPostExecute(Exception result) {
                    if(waitWhileSavingDialog!=null && waitWhileSavingDialog.isShowing())
                        try{waitWhileSavingDialog.dismiss();}
                        catch(java.lang.IllegalArgumentException e){}//This throws an IllegalArgumentException if the app has been stopped while we were saving.
                    if(result==null)
                    {
                        if(successCallable!=null) {
                            try{successCallable.call();}
                            catch(Exception e){showInfo(getString(R.string.error_saveing)+": "+e);}
                        }
                    }
                    else
                    {
                        showInfo(getString(R.string.error_saveing)+": "+result);
                        if(failureCallable!=null) {
                            try{failureCallable.call();}
                            catch(Exception e) {showInfo(getString(R.string.error_saveing)+": "+e);}
                        }
                    }
                }
            };
        mSaveAsOrSaveTask.execute(saveCallable);
    }
    
    private synchronized Exception saveAs(Uri uri) {
        if (core == null) return new Exception("core is null");
        try
        {
            core.saveAs(this, uri);
        }
        catch(Exception e)
        {
            Log.e(getString(R.string.app_name), "Exception during saveAs(): "+e);
            return e;
        }
            //Set the uri of this intent to the new file path
        getIntent().setData(uri);
            //Save the viewport under the new name
        saveViewportAndRecentFiles(core.getUri());
			//Try to take permissions
		tryToTakePersistablePermissions(getIntent());
        rememberTemporaryUriPermission(getIntent());
        return null;
    }
    

    private synchronized Exception save() {
        if (core == null) return new Exception("core is null");
        try
        {   
            core.save(this);
        }
        catch(Exception e)
        {
            Log.e(getString(R.string.app_name), "Exception during save(): "+e);
            return e;
        }
            //Save the viewport
        saveViewportAndRecentFiles(core.getUri());
        return null;
    }
    
            
    private void saveViewport(SharedPreferences.Editor edit, String path) {
        if(mDocView == null) return;
        if(path == null) path = "/nopath";
        edit.putInt("page"+path, mDocView.getSelectedItemPosition());
        edit.putFloat("normalizedscale"+path, mDocView.getNormalizedScale());
        edit.putFloat("normalizedxscroll"+path, mDocView.getNormalizedXScroll());
        edit.putFloat("normalizedyscroll"+path, mDocView.getNormalizedYScroll());
        edit.commit();
    }


    private void restoreViewport() {
        if (core != null && mDocView != null) {
            SharedPreferences prefs = getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
            setViewport(prefs, core.getUri());
        }
    }


    private void setViewport(SharedPreferences prefs, Uri uri) {
        setViewport(prefs.getInt("page"+uri.toString(), 0),prefs.getFloat("normalizedscale"+uri.toString(), 0.0f),prefs.getFloat("normalizedxscroll"+uri.toString(), 0.0f), prefs.getFloat("normalizedyscroll"+uri.toString(), 0.0f));
    }

    
    private void setViewport(int page, float normalizedscale, float normalizedxscroll, float normalizedyscroll) {
        mDocView.setDisplayedViewIndex(page);
        mDocView.setNormalizedScale(normalizedscale);
        mDocView.setNormalizedScroll(normalizedxscroll, normalizedyscroll);
    }


    private void saveRecentFiles(SharedPreferences prefs, final SharedPreferences.Editor edit, Uri uri) {
            //Read the recent files list from preferences
        final RecentFilesList recentFilesList = new RecentFilesList(getApplicationContext(), prefs);                    
            //Add the current file
        RecentFile recentFile = new RecentFile(uri.toString(), core.getFileName());
        recentFilesList.push(recentFile);
        
            //Write the recent files list
        recentFilesList.write(edit);
        edit.apply();

            //Generate and add a thubnail in the background
        if(mRenderThumbnailTask!=null)
            mRenderThumbnailTask.cancel();

        final PdfThumbnailManager thumbnailManager = new PdfThumbnailManager(this, core);
        final MuPDFCore.Cookie cookie = core.new Cookie();
        mRenderThumbnailTask = new CancellableAsyncTask<RecentFile, RecentFile>(new MuPDFCancellableTaskDefinition<RecentFile,RecentFile>(core) 
            {
                @Override
                public RecentFile doInBackground(MuPDFCore.Cookie cookie, RecentFile... recentFile0) {
                    RecentFile recentFile = recentFile0[0];
                    int bmWidth;
                    int bmHeight;
                    Display display = getWindowManager().getDefaultDisplay();
                    if (android.os.Build.VERSION.SDK_INT < 13) {
                        bmWidth = Math.min(display.getWidth(), display.getHeight());
                    } else {
                        Point size = new Point();
                        display.getSize(size);
                        bmWidth = Math.min(size.x,size.y);
                    }
                    bmHeight = (int)((float)bmWidth*0.5);

                    String thunbnailString = thumbnailManager.generate(bmWidth, bmHeight, cookie);
                    if(thunbnailString != null && !cookie.aborted())
                    {
                        recentFile.setThumbnailString(thunbnailString);
                        recentFilesList.write(edit);
                        edit.apply();
                    }
                    
                    return recentFile;
                }
            })
                               {
                                       // @Override
                                       // protected void onPostExecute(final RecentFile recentFile) {                       
                                       //     recentFilesList.push(recentFile);//this replaces the previously pushed version
                                       //     recentFilesList.write(edit);
                                       //     edit.apply();
                                       // }
            };
        mRenderThumbnailTask.execute(recentFile);
    }
    
    
    private void saveViewportAndRecentFiles(Uri uri) {
        if(uri != null)
        {
            SharedPreferences prefs = getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
            SharedPreferences.Editor edit = prefs.edit();
            saveRecentFiles(prefs, edit, uri);
            saveViewport(edit, uri.toString());
            edit.apply();
        }
    }
    

    private void saveViewport(Uri uri) {
        if(uri != null)
        {
            SharedPreferences prefs = getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
            SharedPreferences.Editor edit = prefs.edit();
            saveViewport(edit, uri.toString());
            edit.apply();
        }
    }

    
    @Override
    public Object onRetainCustomNonConfigurationInstance() { //Called if the app is destroyed for a configuration change
        PenAndPDFCore mycore = core;
        core = null;
        return mycore;
    }
    
    
    @Override
    protected void onSaveInstanceState(Bundle outState) { //Called when the app is destroyed by the system and in various other cases
        super.onSaveInstanceState(outState);

        outState.putString("ActionBarMode", mActionBarMode.toString());
        outState.putInt("PageBeforeInternalLinkHit", mPageBeforeInternalLinkHit);
        outState.putFloat("NormalizedScaleBeforeInternalLinkHit", mNormalizedScaleBeforeInternalLinkHit);
        outState.putFloat("NormalizedXScrollBeforeInternalLinkHit", mNormalizedXScrollBeforeInternalLinkHit);
        outState.putFloat("NormalizedYScrollBeforeInternalLinkHit", mNormalizedYScrollBeforeInternalLinkHit);
        if(mDocView != null) outState.putParcelable("mDocView", mDocView.onSaveInstanceState());
        outState.putString("latestTextInSearchBox", latestTextInSearchBox);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPref, String key) {
            //Take care of some preference changes directly
        if (sharedPref.getBoolean(SettingsActivity.PREF_KEEP_SCREEN_ON, false ))
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mSaveOnStop = getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, MODE_MULTI_PROCESS).getBoolean(SettingsActivity.PREF_SAVE_ON_STOP, true);
        mSaveOnDestroy = getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, MODE_MULTI_PROCESS).getBoolean(SettingsActivity.PREF_SAVE_ON_DESTROY, true);

        try{
            numberRecentFilesInMenu = Integer.parseInt(getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, MODE_MULTI_PROCESS).getString(SettingsActivity.PREF_NUMBER_RECENT_FILES, "20"));
        }
        catch(NumberFormatException ex) {
            numberRecentFilesInMenu = Integer.parseInt(getResources().getString(R.string.number_recent_files_default));
        }    
            
            //Also notify other classes and members of the preference change
        ReaderView.onSharedPreferenceChanged(sharedPref, key);
        PageView.onSharedPreferenceChanged(sharedPref, key, this);
        if(core != null) core.onSharedPreferenceChanged(sharedPref, key);
    }    

    
    private void printDoc() {
        if (!core.fileFormat().startsWith("PDF")) {
            showInfo(getString(R.string.format_currently_not_supported));
            return;
        }

        final Intent printIntent = new Intent();

        callInBackgroundAndShowDialog(
            getString(R.string.preparing_to_print),
            new Callable<Exception>() {
                @Override
                public Exception call() {
                    try
                    {
                        printIntent.setDataAndType(core.export(getApplicationContext()), "aplication/pdf");
                        printIntent.putExtra("title", core.getFileName());
                    }
                    catch(Exception e)
                    {
                        return e;
                    }
                    return null;
                }
            },
            new Callable<Void>() {
                @Override
                public Void call() {
                    mIgnoreSaveOnStopThisTime = true;
                    startActivityForResult(printIntent, PRINT_REQUEST);
                    return null;
                }
            },
            null);
        // Intent printIntent = new Intent(this, PrintDialogActivity.class);
        // try
        // {
        //     printIntent.setDataAndType(core.export(this), "aplication/pdf");
        // }
        // catch(Exception e)
        // {
        //     showInfo(getString(R.string.error_exporting)+" "+e.toString());
        // }
        // printIntent.putExtra("title", core.getFileName());
        // mIgnoreSaveOnStopThisTime = true;
        // startActivityForResult(printIntent, PRINT_REQUEST);
    }

    private void shareDoc() {
        final Intent shareIntent = new Intent();

        callInBackgroundAndShowDialog(
            getString(R.string.preparing_to_share),
            new Callable<Exception>() {
                @Override
                public Exception call() {
                    Uri exportedUri = null;
                    try
                    {
                        exportedUri = core.export(getApplicationContext());
                    }
                    catch(Exception e)
                    {
                        return e;
                    }
                    shareIntent.setAction(Intent.ACTION_SEND);
                    shareIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    shareIntent.setDataAndType(exportedUri, getContentResolver().getType(exportedUri));
                    shareIntent.putExtra(Intent.EXTRA_STREAM, exportedUri);
                    return null;
                }
            },
            new Callable<Void>() {
                @Override
                public Void call() {
                    mIgnoreSaveOnStopThisTime = true;
                    startActivity(Intent.createChooser(shareIntent, getString(R.string.share_with)));
                    return null;
                }
            },
            null);
        // try
        // {
        //     exportedUri = core.export(this);    
        // }
        // catch(Exception e)
        // {
        //     showInfo(getString(R.string.error_exporting)+" "+e.toString());
        // }
        // if(exportedUri != null) 
        // {
        //     Intent shareIntent = new Intent();
        //     shareIntent.setAction(Intent.ACTION_SEND);
        //     shareIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        //     shareIntent.setDataAndType(exportedUri, getContentResolver().getType(exportedUri));
        //     shareIntent.putExtra(Intent.EXTRA_STREAM, exportedUri);
        //     mIgnoreSaveOnStopThisTime = true;
        //     startActivity(Intent.createChooser(shareIntent, getString(R.string.share_with)));
        // }
    }
    
    
    private void showInfo(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
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
                                if (core==null || !core.authenticatePassword(mPasswordView.getText().toString()))
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

		mAlertDialog = mAlertBuilder.create();

		final LinearLayout editTextLayout = new LinearLayout(mAlertDialog.getContext());
		editTextLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
		editTextLayout.setOrientation(LinearLayout.VERTICAL);
        int padding = dpToPixel(16);
		editTextLayout.setPadding(padding, padding, padding, 0);
		
        final EditText input = new EditText(editTextLayout.getContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine();
		input.setBackgroundDrawable(null);
		input.setHint(getString(R.string.dialog_gotopage_hint));
        input.setFocusable(true);
		mAlertDialog.setTitle(R.string.dialog_gotopage_title);
		DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int which) {
					if (which == AlertDialog.BUTTON_POSITIVE) {
							// User clicked OK button
                        int pageNumber;
                        try{
                            pageNumber = Integer.parseInt(input.getText().toString());
                        }catch(NumberFormatException e){
                            pageNumber = 0;
                        }
                        if(mDocView!=null)
                        {
                            mDocView.setDisplayedViewIndex(pageNumber == 0 ? 0 : pageNumber -1 );
                            mDocView.setScale(1.0f);
                            mDocView.setNormalizedScroll(0.0f,0.0f);
                        }
					} else if (which == AlertDialog.BUTTON_NEGATIVE) {
					}
				}
			};
		mAlertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dialog_gotopage_ok), listener);
		mAlertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.dialog_gotopage_cancel), listener);
		editTextLayout.addView(input);
		mAlertDialog.setView(editTextLayout);
		mAlertDialog.show();
		input.requestFocus();
    }


    private void showOpenNewDocumentDialoge() {

		mAlertDialog = mAlertBuilder.create();

		final LinearLayout editTextLayout = new LinearLayout(mAlertDialog.getContext());
		editTextLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
		editTextLayout.setOrientation(LinearLayout.VERTICAL);
        int padding = dpToPixel(16);
		editTextLayout.setPadding(padding, padding, padding, 0);
        final EditText input = new EditText(editTextLayout.getContext());
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        input.setSingleLine();
		input.setBackgroundDrawable(null);
        TextDrawable textDrawable = new TextDrawable(".pdf", input.getTextSize(), input.getCurrentTextColor());
        input.setCompoundDrawablesWithIntrinsicBounds(null , null, textDrawable, null);
        input.setFocusable(true);
        input.setGravity(Gravity.END);
		mAlertDialog.setTitle(R.string.dialog_newdoc_title);
		DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int which) {
					if (which == AlertDialog.BUTTON_POSITIVE) {
							// User clicked OK button
                        String filename = input.getText().toString();
                        try
                        {
                            if(filename != "")
                            {
                                filename+=".pdf";
                                File dir = getNotesDir(mAlertDialog.getContext());
                                File file = new File(dir, filename);

                                if(file != null && file.isFile() && file.length() > 0)
                                    showInfo(String.format(getString(R.string.file_alrady_exists), filename));
                                else
                                    openNewDocument(filename);
                            }
                        }
                        catch(java.io.IOException e){
                            AlertDialog alert = mAlertBuilder.create();
                            alert.setTitle(R.string.cannot_open_document);
                            alert.setMessage(getResources().getString(R.string.reason)+": "+e.toString());
                            alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dismiss),
                                            new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog, int which) {
                                                    finish();
                                                }
                                            });
                            alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
                                    public void onDismiss(DialogInterface dialog) {
                                        finish();
                                    }
                                });
                            alert.show();	
                        }   
					}
					else if (which == AlertDialog.BUTTON_NEGATIVE) {
					}
				}
			};
		mAlertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dialog_newdoc_ok), listener);
		mAlertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.dialog_newdoc_cancel), listener);
		editTextLayout.addView(input);
		mAlertDialog.setView(editTextLayout);
		mAlertDialog.show();
		input.requestFocus();
        input.setSelection(0);
    }

    
    
    private void search(int direction) {
        if(mDocView.hasSearchResults() && textOfLastSearch.equals(latestTextInSearchBox))
            mDocView.goToNextSearchResult(direction);
        else
        {
            mSearchTaskManager.start(latestTextInSearchBox, direction, mDocView.getSelectedItemPosition());
            textOfLastSearch = latestTextInSearchBox;
        }
    }
    

    @Override
    public void onBackPressed() {
        if (getSupportActionBar() != null && !getSupportActionBar().isShowing()) {
            exitFullScreen();
            return;
        };
        if(dashboardIsShown() && mDocView != null) {
            hideDashboard();
            return;
        }
        switch (mActionBarMode) {
            case Annot:
                return;
            case Search:
                hideKeyboard();
                textOfLastSearch = "";
                searchView.setQuery("", false);
				mDocView.setMode(MuPDFReaderView.Mode.Viewing);
                mDocView.clearSearchResults();
                mDocView.resetupChildren();
                mActionBarMode = ActionBarMode.Main;
                invalidateOptionsMenu();
                return;
            case Selection:
                mDocView.setMode(MuPDFReaderView.Mode.Viewing);
                MuPDFView pageView = (MuPDFView) mDocView.getSelectedView();
                if (pageView != null) pageView.deselectText();
                return;
        }
        
        if (core != null && core.hasChanges()) {
            DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == AlertDialog.BUTTON_POSITIVE) {
                            if(core.canSaveToCurrentUri(PenAndPDFActivity.this))
                            {
                                saveInBackground(
                                    new Callable<Void>() {
                                        @Override
                                        public Void call() {
                                            mIgnoreSaveOnStopThisTime = true;//No need to save twice
                                            mIgnoreSaveOnDestroyThisTime = true;//No need to save twice
                                            finish();
                                            return null;
                                        }
                                    },
                                    new Callable<Void>() {
                                        @Override
                                        public Void call() {
                                            showInfo(getString(R.string.error_saveing));
                                            return null;
                                        }
                                    }
                                                 );                                
                            }
                            else
                                showSaveAsActivity();
                        }
                        if (which == AlertDialog.BUTTON_NEGATIVE) {
                            mIgnoreSaveOnStopThisTime = true;
                            mIgnoreSaveOnDestroyThisTime = true;
                            finish();
                        }
                        if (which == AlertDialog.BUTTON_NEUTRAL) {
                        }
                    }
                };
            AlertDialog alert = mAlertBuilder.create();
            alert.setTitle(getString(R.string.save_question));
            alert.setMessage(getString(R.string.document_has_changes_save_them));
            if(core.canSaveToCurrentUri(this))
                alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.save), listener);
            else
                alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.saveas), listener);      
            alert.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.cancel), listener);
            alert.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.no), listener);
            alert.show();
        } else {
            super.onBackPressed();
        }
    }

    
    @Override
    public void performPickFor(FilePicker picker) {
        mFilePicker = picker;
        Intent intent = new Intent(this, PenAndPDFFileChooser.class);
        startActivityForResult(intent, FILEPICK_REQUEST);
    }

    private void setTitle() {
        if (core == null || mDocView == null)  return;
        int pageNumber = mDocView.getSelectedItemPosition();
        String title = getString(R.string.app_name)+" "+Integer.toString(pageNumber+1)+"/"+Integer.toString(core.countPages());
		String subtitle = "";
		if(core.getFileName() != null) subtitle+=core.getFileName();
        android.support.v7.app.ActionBar actionBar = getSupportActionBar();
		if(actionBar != null){
			actionBar.setTitle(title);
			actionBar.setSubtitle(subtitle);
		}
    }
    
    
    private void showKeyboard()
    {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if(inputMethodManager!=null && getCurrentFocus() != null) inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
    }

	
	private void hideKeyboard()
    {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if(inputMethodManager!=null && getCurrentFocus() != null) inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
    }
	

    private void enterFullscreen() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getSupportActionBar().hide();
        mActionBarMode = ActionBarMode.Hidden;
        invalidateOptionsMenu();
        mDocView.setScale(1.0f);
        mDocView.setLinksEnabled(false);
        mDocView.setPadding(0, 0, 0, 0);
    }
            
    private void exitFullScreen() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getSupportActionBar().show();
        if(mActionBarMode == ActionBarMode.Hidden)
            mActionBarMode = ActionBarMode.Main;
        invalidateOptionsMenu();
        mDocView.setScale(1.0f);
        mDocView.setLinksEnabled(true);
            //Make content appear below the toolbar if completely zoomed out
        TypedValue tv = new TypedValue();
        if(getSupportActionBar().isShowing() && getTheme().resolveAttribute(android.support.v7.appcompat.R.attr.actionBarSize, tv, true)) {
            int actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data,getResources().getDisplayMetrics());
            mDocView.setPadding(0, actionBarHeight, 0, 0);
            mDocView.setClipToPadding(false);
        }
    }

    private void resetupDocViewAfterActionBarAnimation(final boolean linksEnabled) {
        final android.support.v7.app.ActionBar actionBar = getSupportActionBar();
        try {
                // Make the Animator accessible
            final Class<?> actionBarImpl = actionBar.getClass();
            final Field currentAnimField = actionBarImpl.getDeclaredField("mCurrentShowAnim");
            currentAnimField.setAccessible(true);
            
                // Monitor the animation
            final Animator currentAnim = (Animator) currentAnimField.get(actionBar);
            currentAnim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if(mDocView != null) {
                            mDocView.setScale(1.0f);
                            saveViewport(core.getUri()); //So that we show the right page when the mDocView is recreated
                        }
                        mDocView = null;
                        setupDocView();
                        mDocView.setLinksEnabled(linksEnabled);
                    }
                    
                });
        } catch (final Exception ignored) {
                // Nothing to do
        }
    }

    public static File getNotesDir(Context contex) {
        File notesDir = new File(Environment.getExternalStorageDirectory(), "PenAndPDFNotes");
        notesDir.mkdirs();
        
            //Migrate old notes
        try
        {
            File oldNotesDir = contex.getDir("notes", Context.MODE_WORLD_READABLE);
            File[] listOfFiles = oldNotesDir.listFiles();
            if(listOfFiles != null && listOfFiles.length > 0)
            {
                for (File child : listOfFiles)
                {
                    File targetFile = new File(notesDir, child.getName());
                    if(child.isFile() && !targetFile.exists()) 
                    {
                        FileInputStream in = new FileInputStream(child);
                        FileOutputStream out = new FileOutputStream(targetFile);
                        byte[] buf = new byte[1024];
                        int len;
                        while ((len = in.read(buf)) > 0) {
                            out.write(buf, 0, len);
                        }
                        in.close();
                        out.close();
                        child.delete();
                    }
                }
            }
        }
        catch (Exception e)
        {
                //Nothing we could do
        }
        
        return notesDir;
    }
    
    public ArrayList<TemporaryUriPermission> getTemporaryUriPermissions() {
        return temporaryUriPermissions;
    }

    public void rememberTemporaryUriPermission(Intent intent) {
        temporaryUriPermissions.add(new TemporaryUriPermission(intent));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == WRITE_PERMISSION_DURING_RESUME_REQUEST) {
                /*We just resume irrespective of whether the permission was
                 * granted and then handle cases where we can not access a
                 * file on a per case basis.
                 * Addendum: We should be able to simply resume here, but
                 * due to a bug in Android we have to kill the current process
                 * because we only actually get the permission after the app
                 * is restarted from scratch.
                 * */
            //onResume();
            Boolean anyResultPositive = false;
            for (int result : grantResults)
                if(result ==  android.content.pm.PackageManager.PERMISSION_GRANTED ) {
                    anyResultPositive = true;
                    break;
                }
            
            if(anyResultPositive) 
            {   
                AlertDialog alert = mAlertBuilder.create();
                alert.setTitle(R.string.dialog_newpermissions_title);
                alert.setMessage(getResources().getString(R.string.dialog_newpermissions_message));
                alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dialog_newpermissions_ok),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                    }
                                });
                alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        public void onDismiss(DialogInterface dialog) {
                            android.os.Process.killProcess(android.os.Process.myPid());
                        }
                    });
                alert.show();
            }
        }
    }

    public int dpToPixel(float sizeInDp) {
        float scale = getResources().getDisplayMetrics().density;
        int dpAsPixels = (int) (sizeInDp*scale + 0.5f);
        return dpAsPixels;
    }

    private boolean memoryLow() {
        ActivityManager.MemoryInfo memoryInfo = getAvailableMemory();
        if (memoryInfo.lowMemory)
            return true;
        else
            return false;
    }

    private ActivityManager.MemoryInfo getAvailableMemory() {
        ActivityManager activityManager = (ActivityManager) this.getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        return memoryInfo;
    }
}
