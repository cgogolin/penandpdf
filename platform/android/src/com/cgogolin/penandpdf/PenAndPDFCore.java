package com.cgogolin.penandpdf;
import java.util.ArrayList;

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
import android.os.ParcelFileDescriptor;
import android.content.pm.PackageManager;
import android.content.Intent;

public class PenAndPDFCore extends MuPDFCore
{
    private Uri uri = null;
    private File tmpFile = null;

    public PenAndPDFCore(Context context, Uri uri) throws Exception
	{
//            Log.e("Core", "creating with uri="+uri);
            
            this.uri = uri;

            if(new File(Uri.decode(uri.getEncodedPath())).isFile()) //Uri points to a file
            {
//                Log.e("Core", "uri points to file");
                super.init(context, Uri.decode(uri.getEncodedPath()));
            }
            else if (uri.toString().startsWith("content://")) //Uri points to a content provider
            {
//                Log.e("Core", "uri points to content");
                String displayName = null;
                Cursor cursor = context.getContentResolver().query(uri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null); //This should be done asynchonously!

                if (cursor != null && cursor.moveToFirst())
                {

                        //Try to get the display name/title
                    int displayNameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
                    if(displayNameIndex >= 0) displayName = cursor.getString(displayNameIndex);
                    if(displayName==null)
                    {
                        int titleIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
                        if(titleIndex >= 0) displayName = cursor.getString(titleIndex);             
                        if(displayName==null)
                            displayName="NoName.pdf";
                    }       
                    cursor.close();
                    
                        //Some programms encode parts of the filename in utf-8 base 64 encoding if the filename contains special charcters. This can look like this: '=?UTF-8?B?[text here]==?=' Here we decode such cases:
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
                
                byte buffer[] = null;    
                InputStream is = context.getContentResolver().openInputStream(uri);
                if(is != null)
                {
                    int len = is.available();
                    buffer = new byte[len];
                    is.read(buffer, 0, len);
                    is.close();
                }
                super.init(context, buffer, displayName);
            }
        }
            
    public synchronized void save(Context context) throws java.io.IOException, java.io.FileNotFoundException
        {
            saveAs(context, this.uri);
        }

    
    public synchronized void saveAs(Context context, Uri uri) throws java.io.IOException, java.io.FileNotFoundException
        {
            if(canSaveToUriViaContentResolver(context, uri))
            {
                if(tmpFile == null) {
                    File cacheDir = context.getCacheDir();
                    tmpFile = File.createTempFile("prefix", "pdf", cacheDir);
                }
                if(saveAsInternal(tmpFile.getPath()) != 0) throw new java.io.IOException("native code failed to save to "+tmpFile.getPath());

                ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "w");
                FileInputStream fileInputStream = new FileInputStream(tmpFile);
                FileOutputStream fileOutputStream = new FileOutputStream(pfd.getFileDescriptor());
                copyStream(fileInputStream,fileOutputStream);
                fileInputStream.close();
                fileOutputStream.close();
                pfd.close();
                this.uri = uri;
            }
            else
            {
                File file = new File(Uri.decode(uri.getEncodedPath()));
                if(!file.exists())
                    file.createNewFile(); //Try to create the file first so that we can then check if we can write to it.
                if(canSaveToUriAsFile(context, uri))
                {
                    String path = Uri.decode(uri.getEncodedPath());
                    if(saveAsInternal(path) != 0)
                        throw new java.io.IOException("native code failed to save to "+uri.toString());
                    this.uri = uri;
//                    Log.e("Core", "saving done!");                
                }
                else
                    throw new java.io.IOException("no way to save to "+uri.toString());
            }
        }
    
    private static void copyStream(InputStream input, OutputStream output)
        throws java.io.IOException
        {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while((bytesRead = input.read(buffer)) != -1)
            {
                output.write(buffer, 0, bytesRead);
            }
        }

    public boolean canSaveToUriViaContentResolver(Context context, Uri uri) {
        try
        {
//            Log.e("PenAndPDF", "check permissions returns "+context.checkCallingUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)+" where granted="+PackageManager.PERMISSION_GRANTED+" and denied="+PackageManager.PERMISSION_DENIED+" for uri="+uri.toString());
            boolean haveWritePermissionToUri = false;
            if (android.os.Build.VERSION.SDK_INT >= 19)
            {
                for( UriPermission permission : context.getContentResolver().getPersistedUriPermissions()) {
                    if(permission.isWritePermission() && permission.getUri().equals(uri))
                    {
//                        Log.e("PenAndPDF", "Have taken permissions");
                        haveWritePermissionToUri = true;
                    }
                }
            }
            else
            {
                if(context.checkCallingUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED)
                    haveWritePermissionToUri = true;
            }
            
            if(haveWritePermissionToUri)
            {
                ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "wa");
                if(pfd != null) {
                    pfd.close();
                    return true;
                }
                else
                    return false;
            }
            else
                return false;
        }
        catch(Exception e)
        {
                return false;
        }
    }

    public boolean canSaveToUriAsFile(Context context, Uri uri) {
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

    public boolean canSaveToCurrentUri(Context context) {
        return canSaveToUriViaContentResolver(context, getUri()) || canSaveToUriAsFile(context, getUri());
    }
    
//     public boolean canSaveToCurrentLocation(Context context) {
// //        Log.e("PenAndPDF", "cheching if we can save to "+uri+" canWrite()="+new File(Uri.decode(uri.getEncodedPath())).canWrite());
//         try
//         {
//                 //Try if Uri can be resolved with the content resolver. If not, then try to directly open a file from the Uri
//             ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "wa");
//             if(pfd != null) 
//             {
//                 pfd.close();
//                 return true;
//             }
//             else
//                 throw new Exception();
//         }
//         catch(Exception e)
//         {
//             if(new File(Uri.decode(uri.getEncodedPath())).canWrite() )
//                 return true;
//             else
//                 return false;
//         }
//     }

    public Uri getUri(){
        return uri;
    }
    
    @Override
    public synchronized void onDestroy() {
        super.onDestroy();
        if(tmpFile != null) tmpFile.delete();
    }
}
