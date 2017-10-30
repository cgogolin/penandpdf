package com.cgogolin.penandpdf;
import java.util.ArrayList;

import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.content.pm.PackageManager;
import android.content.UriPermission;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.provider.MediaStore.MediaColumns;
import android.provider.MediaStore;
import android.database.Cursor;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap.Config;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.io.ByteArrayOutputStream;
import java.lang.OutOfMemoryError;

import android.os.ParcelFileDescriptor;
import android.content.pm.PackageManager;
import android.content.Intent;

public class PenAndPDFCore extends MuPDFCore
{
    private Uri uri = null;
    private File tmpFile = null;

        /* File IO is terribly inconsistent and badly documented on Android
         * to make matters worse the native part of the Core stops beeing
         * useful once the method saveInternal() is call by MuPDFCore.
         * Here we try to abstract away the complexity this brings with it
         * by implementing three methods export() save() and saveAs() that
         * try to do what one would expect such methods to do.
         * Unoftunately this leads to terribly messy code that is really
         * hard to maintain...
         */
    
    public PenAndPDFCore(Context context, Uri uri) throws Exception
	{
            init(context, uri);
        }
    
    
    public synchronized void init(Context context, Uri uri) throws Exception
	{
//            Log.i("context.getString(R.string.app_name)", "creating with uri="+uri);
            
            this.uri = uri;

                /*Sometimes we can open a uri both as a file and via a content provider. On old versions of Android the former works better, whereas on new versions the latter works generally better. Hence we switch the order in which we try depending on the Android version.*/
            
            if(android.os.Build.VERSION.SDK_INT < 23 && new File(Uri.decode(uri.getEncodedPath())).isFile()) //Uri points to a file
            {
                Log.i(context.getString(R.string.app_name), "uri "+uri.toString()+" points to file");
                super.init(context, Uri.decode(uri.getEncodedPath()));
            }
            else if (uri.toString().startsWith("content://")) //Uri points to a content provider
            {
                String displayName = getFileName(context, uri);
                
                InputStream is = null;
                ParcelFileDescriptor pfd = null;
                is = context.getContentResolver().openInputStream(uri);
                if(is == null || is.available() == 0)
                {
                    pfd = context.getContentResolver().openFileDescriptor(uri, "r");
                    if(pfd != null)
                        is = new FileInputStream(pfd.getFileDescriptor());
                }
                if(is != null && is.available() > 0)
                {
                    try
                    {
                        int len = 0;
                        ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
                        try {
                            byte buffer[] = new byte[1024];
                            int num = 0;
                            while((num = is.read(buffer)) != -1) {
                                len+=num;
                                baos.write(buffer, 0, num);
                            }
                            Log.i(context.getString(R.string.app_name), "reached end of stream");
                        }
                        finally
                        {
                            is.close();
                            if(pfd != null) pfd.close();
                        }
                        byte buffer[] = baos.toByteArray();
                        Log.i(context.getString(R.string.app_name), "read "+len+" bytes into buffer "+buffer);
                        super.init(context, buffer, displayName);
                    }
                    catch (OutOfMemoryError E)
                    {
                        throw new Exception("ran out of memory while opening "+uri.toString());
                    }
                }
                else
                    throw new Exception("unable to open input stream to uri "+uri.toString());
            }
            else if(android.os.Build.VERSION.SDK_INT >= 23 && new File(Uri.decode(uri.getEncodedPath())).isFile()) //Uri points to a file
            {
                Log.i(context.getString(R.string.app_name), "uri "+uri.toString()+" points to file");
                super.init(context, Uri.decode(uri.getEncodedPath()));
            }
        }

    public synchronized Uri export(Context context) throws java.io.IOException, java.io.FileNotFoundException, Exception
        {
            Uri oldUri = this.uri;
            String oldPath = getPath();
            String oldFileName = getFileName();
            boolean oldHasChanges = hasChanges();
            
                //If no tmpflie has been created or the file name has changed, creat a new tmpFile and, if appropriate, remeber the old tmpFile to delete it after the core has saved to the new location. 
            File oldTmpFile = null;
            if(tmpFile==null || !tmpFile.getName().equals(oldFileName) )
            {
                oldTmpFile = tmpFile;
                File cacheDir = new File(context.getCacheDir(), "tmpfiles");
                cacheDir.mkdirs();
                tmpFile = new File(cacheDir, oldFileName);
            }
            
            // File oldTmpFile = tmpFile;
            // // if(tmpFile == null)
            // // {
            // File cacheDir = new File(context.getCacheDir(), "tmpfiles");
            // cacheDir.mkdirs();
            // tmpFile = new File(cacheDir, oldFileName);
            // // }
            
            if(super.saveAs(tmpFile.getPath()) != 0)
                throw new java.io.IOException("native code failed to save to tmp file: "+tmpFile.getPath());

                //Delete old tmp file if we created a new one
            if(oldTmpFile!=null)
                oldTmpFile.delete();
            
                //reinit because the MuPDFCore core gets useless after saveIntenal()
            init(context, Uri.fromFile(tmpFile)); 
                //But now the Uri, as well as mFilenName and mPath in the superclass are wrong, so we repair this
            uri = oldUri;
            relocate(oldPath, oldFileName);
            setHasAdditionalChanges(oldHasChanges);
            
            return FileProvider.getUriForFile(context, "com.cgogolin.penandpdf.fileprovider", tmpFile);
        }
    
    public synchronized void save(Context context) throws java.io.IOException, java.io.FileNotFoundException, Exception
        {
            saveAs(context, this.uri);
        }

    public synchronized void saveAs(Context context, Uri uri) throws java.io.IOException, java.io.FileNotFoundException, Exception
        {
            ParcelFileDescriptor pfd = null;
            FileOutputStream fileOutputStream = null;
            FileInputStream fileInputStream = null;
            try
            {
                    //Export to tmpFile
                export(context);
                
                    //Open the result as fileInputStream
                fileInputStream = new FileInputStream(tmpFile);
                
                    //Open FileOutputStream to actual destination
                try
                {
                    pfd = context.getContentResolver().openFileDescriptor(uri, "w");
                    if(pfd != null)
                        fileOutputStream = new FileOutputStream(pfd.getFileDescriptor());
                }
                catch(Exception e)
                {
                    String path = uri.getPath();
                    File file = null;
                    if(path != null)
                        file = new File(path);
                    if(file != null)
                        fileOutputStream = new FileOutputStream(file);
                }
                finally
                {
                    if(fileOutputStream == null)
                        throw new java.io.IOException("Unable to open output stream to given uri: "+uri);
                }
                copyStream(fileInputStream,fileOutputStream);
                Log.i(context.getString(R.string.app_name), "copyStream() succesfull");
            }
            catch (java.io.FileNotFoundException e) 
            {
                Log.i("context.getString(R.string.app_name)", "Exception for uri="+uri);
                throw e;
            }
            catch (java.io.IOException e)
            {
                throw e;
            }
            finally
            {
                if(fileInputStream != null) fileInputStream.close();
                if(fileOutputStream != null) fileOutputStream.close();
                if(pfd != null) pfd.close();
            }
                //remeber the new uri and tell the core that all changes were saved
            this.uri = uri;
            
            relocate(uri.getPath(), getFileName(context, uri));
            
            setHasAdditionalChanges(false);
        }
    
    private synchronized static void copyStream(InputStream input, OutputStream output)
        throws java.io.IOException
        {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while((bytesRead = input.read(buffer)) != -1)
            {
                output.write(buffer, 0, bytesRead);
            }
        }
    
    public synchronized <T extends Context & TemporaryUriPermission.TemporaryUriPermissionProvider> boolean canSaveToUriViaContentResolver(T context, Uri uri) {
        
        boolean haveWritePermissionToUri = false;
        try
        {
            //     haveWritePermissionToUri = true;
            for(TemporaryUriPermission permission : (context).getTemporaryUriPermissions()) {
                Log.i(context.getString(R.string.app_name), "checking saved temporary permission for "+permission.getUri()+" while uri="+uri+" write permission is "+permission.isWritePermission()+" and uris are equal "+permission.getUri().equals(uri));
                if(permission.isWritePermission() && permission.getUri().equals(uri))
                {
                    haveWritePermissionToUri = true;
                    break;
                }
            }
            if(!haveWritePermissionToUri)
            {
                if (android.os.Build.VERSION.SDK_INT >= 19)
                {
                    for(UriPermission permission : (context).getContentResolver().getPersistedUriPermissions()) {
                        if(permission.isWritePermission() && permission.getUri().equals(uri))
                        {
                            haveWritePermissionToUri = true;
                            break;
                        }
                    }
                }
                else
                {
                    if(context.checkCallingOrSelfUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED)
                    {
                        haveWritePermissionToUri = true;
                    }
                }
            }
        }
        catch(Exception e)
        {
            Log.i(context.getString(R.string.app_name), "exception while trying to figure out permissions: "+e);
            return false;
        }
        if(!haveWritePermissionToUri)
            return false;
            
        boolean canWrite = false;
        OutputStream os = null;
        Log.i(context.getString(R.string.app_name), "we have write permissions, so checking if we can somehow open an output stream");
        try{
            os = context.getContentResolver().openOutputStream(uri, "wa");
            if(os != null)
            {
                Log.i(context.getString(R.string.app_name), "opened os succesfully");
                os.close();
                canWrite = true;
            }
        }
        catch(Exception e)
        {
            Log.i(context.getString(R.string.app_name), "exception while opening os: "+e);
            if(os != null)
                try
                {
                    os.close();
                }
                catch(Exception e2)
                {
                    os = null;
                }
        }
        if(!canWrite){
            os = null;
            ParcelFileDescriptor pfd = null;
            Log.i(context.getString(R.string.app_name), "checking if we can open a pfd");
            try{
                pfd = context.getContentResolver().openFileDescriptor(uri, "wa");
                if(pfd != null) {
                    Log.i(context.getString(R.string.app_name), "opened pfd succesfully so trying to open os via pfd");
                    os = new FileOutputStream(pfd.getFileDescriptor());
                    if(os != null)
                    {
                        os.close();
                        pfd.close();
                        canWrite = true;
                    }
                }
            }
            catch(Exception e)
            {
                Log.i(context.getString(R.string.app_name), "exception while opening pfd or os via pfd: "+e);
                if(e.getMessage().contains("Unsupported mode: wa"))
                {
                    Log.i(context.getString(R.string.app_name), "assuming that the only problem was the mode 'wa' and setting canWrite = true");
                    canWrite = true; //We assume that writing with "w" would work to make google dirve work!
                }
                if(os != null)
                    try
                    {
                        os.close();
                    }
                    catch(Exception e2)
                    {
                        os = null;
                    }
                if(pfd != null)
                    try
                    {
                        pfd.close();
                    }
                    catch(Exception e2)
                    {
                        pfd = null;
                    }
            }
        }
        
        return canWrite;
    }
    
    public synchronized boolean canSaveToUriAsFile(Context context, Uri uri) {
        try
        {
                //The way we use here to determine whether we can write to a file is error prone but I have so far not found a better way
            if(uri.toString().startsWith("content:"))
                return false;
            File file = new File(Uri.decode(uri.getEncodedPath()));
            if(file.exists() && file.isFile() && file.canWrite())
                return true;
            else
                return false;
        }
        catch(Exception e)
        {
            return false;
        }
    }

    public synchronized <T extends Context & TemporaryUriPermission.TemporaryUriPermissionProvider> boolean canSaveToCurrentUri(T context) {
        return canSaveToUriViaContentResolver(context, getUri()) || canSaveToUriAsFile(context, getUri());
    }    

    public synchronized Uri getUri(){
        return uri;
    }
    
    @Override
    public synchronized void onDestroy() {
        super.onDestroy();
        if(tmpFile != null) tmpFile.delete();
    }

    public synchronized boolean deleteDocument(Context context) {
        try
        {
            context.getContentResolver().delete(uri, null, null);
        }
        catch(Exception e)
        {
            try
            {
                File file = new File(Uri.decode(uri.getEncodedPath()));
                file.delete();
            }
            catch(Exception e2)
            {
                return false;
            }
        }
        return true;
    }
        
    
    public synchronized static void createEmptyDocument(Context context, Uri uri) throws java.io.IOException, java.io.FileNotFoundException {
        FileOutputStream fileOutputStream = null;
        try
        {
            String path = uri.getPath();
            File file = null;
            if(path != null)
                file = new File(path);
            if(file != null)
                fileOutputStream = new FileOutputStream(file);        

            if(fileOutputStream == null)
                throw new java.io.IOException("Unable to open output stream to given uri: "+uri.getPath());

            String newline = System.getProperty ("line.separator");
            String minimalPDF =
                "%PDF-1.1"+newline+
                "\u00a5\u00b1\u00eb"+newline+
                "1 0 obj "+newline+
                "<<"+newline+
                "/Type /Catalog"+newline+
                "/Pages 2 0 R"+newline+
                ">>"+newline+
                "endobj "+newline+
                "2 0 obj "+newline+
                "<<"+newline+
                "/Kids [3 0 R]"+newline+
                "/Type /Pages"+newline+
                "/MediaBox [0 0 595 841]"+newline+
                "/Count 1"+newline+
                ">>"+newline+
                "endobj "+newline+
                "3 0 obj "+newline+
                "<<"+newline+
                "/Resources "+newline+
                "<<"+newline+
                "/Font "+newline+
                "<<"+newline+
                "/F1 "+newline+
                "<<"+newline+
                "/Subtype /Type1"+newline+
                "/Type /Font"+newline+
                "/BaseFont /Times-Roman"+newline+
                ">>"+newline+
                ">>"+newline+
                ">>"+newline+
                "/Parent 2 0 R"+newline+
                "/Type /Page"+newline+
                "/MediaBox [0 0 595 841]"+newline+
                ">>"+newline+
                "endobj xref"+newline+
                "0 4"+newline+
                "0000000000 65535 f "+newline+
                "0000000015 00000 n "+newline+
                "0000000066 00000 n "+newline+
                "0000000149 00000 n "+newline+
                "trailer"+newline+
                ""+newline+
                "<<"+newline+
                "/Root 1 0 R"+newline+
                "/Size 4"+newline+
                ">>"+newline+
                "startxref"+newline+
                "314"+newline+
                "%%EOF"+newline;
            byte[] buffer = minimalPDF.getBytes();
            fileOutputStream.write(buffer, 0, buffer.length);
        }
        catch (java.io.FileNotFoundException e) 
        {
            throw e;
        }
        catch (java.io.IOException e)
        {
            throw e;
        }
        finally
        {
            if(fileOutputStream != null) fileOutputStream.close();
        }
    }

    @Override
    public synchronized boolean insertBlankPageBefore(int position) {
        setHasAdditionalChanges(true);
        return super.insertBlankPageBefore(position);
    }


    public static synchronized <T extends Context & TemporaryUriPermission.TemporaryUriPermissionProvider> boolean canReadFromUri(T context, Uri uri) {
        boolean haveReadPermissionToUri = false;
        try
        {
            if (android.os.Build.VERSION.SDK_INT >= 19)
            {
                for(UriPermission permission : (context).getContentResolver().getPersistedUriPermissions()) {
                    if(permission.isReadPermission() && permission.getUri().equals(uri))
                    {
                        haveReadPermissionToUri = true;
                        break;
                    }
                }
            }

            if(!haveReadPermissionToUri) {
                if(context.checkCallingOrSelfUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED)
                    haveReadPermissionToUri = true;
            }
        }
        catch(Exception e)
        {
            Log.i(context.getString(R.string.app_name), "exception while trying to figure out permissions: "+e);
            return false;
        }
        
        if(!haveReadPermissionToUri && uri.toString().startsWith("file://") )
        {
            File file = new File(Uri.decode(uri.getEncodedPath()));
            if(file.isFile() && file.isFile() && file.canRead())
                haveReadPermissionToUri = true;
        }
        return haveReadPermissionToUri;


            //Opening an input stream can lock for very long time if the Uri for exaple came from Google drive and there is no network 
        // boolean canRead = false;
        // InputStream is = null;
        // try{
        //     is = context.getContentResolver().openInputStream(uri);
        //     if(is != null)
        //     {
        //         is.close();
        //         canRead = true;
        //     }
        // }
        // catch(Exception e)
        // {
        //     if(is != null)
        //         try
        //         {
        //             is.close();
        //         }
        //         catch(Exception e2)
        //         {
        //             is = null;
        //         }
        // }
        
        // return canRead;
    }

    public synchronized String getFileName(Context context, Uri uri) {
        String displayName = null;
        if (uri.toString().startsWith("content://")) //Uri points to a content provider
        {
//            Log.i(context.getString(R.string.app_name), "uri "+uri.toString()+" points to content");
            Cursor cursor = context.getContentResolver().query(uri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null); //This should be done asynchonously!

            if (cursor != null && cursor.moveToFirst())
            {
//                    Log.i(context.getString(R.string.app_name), "got the cursor "+cursor);
                    
                    //Try to get the display name/title
                int displayNameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
                if(displayNameIndex >= 0) displayName = cursor.getString(displayNameIndex);
                if(displayName==null)
                {
                    int titleIndex = cursor.getColumnIndex(MediaStore.MediaColumns.TITLE);
                    if(titleIndex >= 0) displayName = cursor.getString(titleIndex);
                }       
                cursor.close();
            }
            
                    //Some programms encode parts of the filename in utf-8 base 64 encoding if the filename contains special charcters. This can look like this: '=?UTF-8?B?[text here]==?=' Here we decode such cases:
            if(displayName!=null)
            {
                Pattern utf8BPattern = Pattern.compile("=\\?UTF-8\\?B\\?(.+)\\?=");
                Matcher matcher = utf8BPattern.matcher(displayName);
                while (matcher.find()) {
                    String base64 = matcher.group(1);
                    byte[] data = Base64.decode(base64, Base64.DEFAULT);
                    String decodedText = "";
                    try
                    {
                        decodedText = new String(data, "UTF-8");
                    }
                    catch(Exception e)
                    {}
                    displayName = displayName.replace(matcher.group(),decodedText);
                }
            }
        } else {
            File file = new File(Uri.decode(uri.getEncodedPath()));
            if(file.isFile())
                displayName = file.getName();
        }
        
        if(displayName==null || displayName.equals(""))
            displayName=context.getString(R.string.unknown_file_name);
        
        return displayName;
    }
}

