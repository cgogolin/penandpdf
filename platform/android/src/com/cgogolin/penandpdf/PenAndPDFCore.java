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
            init(context, uri);
        }
    
    
    public void init(Context context, Uri uri) throws Exception
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
                        {
                            displayName="NoName.pdf";
                        }
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
                    //Write to a temporyry file
                if(tmpFile == null) {
                    File cacheDir = context.getCacheDir();
                    tmpFile = File.createTempFile("prefix", "pdf", cacheDir);
                }
                if(saveAsInternal(tmpFile.getPath()) != 0)
                    throw new java.io.IOException("native code failed to save to tmp file: "+tmpFile.getPath());
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
                        throw new java.io.IOException("Unable to open output stream to given uri: "+uri.getPath());
                }
                copyStream(fileInputStream,fileOutputStream);
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
                if(fileInputStream != null) fileInputStream.close();
                if(fileOutputStream != null) fileOutputStream.close();
                if(pfd != null) pfd.close();
            }
//            this.uri = uri;
            init(context, uri); //reinit because the MuPDFCore core gets useless after saveInterl()
        }

    
    // public synchronized void saveAs(Context context, Uri uri) throws java.io.IOException, java.io.FileNotFoundException
    //     {
    //         if(canSaveToUriViaContentResolver(context, uri))
    //         {
    //             if(tmpFile == null) {
    //                 File cacheDir = context.getCacheDir();
    //                 tmpFile = File.createTempFile("prefix", "pdf", cacheDir);
    //             }
    //             if(saveAsInternal(tmpFile.getPath()) != 0)
    //                 throw new java.io.IOException("native code failed to save to "+tmpFile.getPath());
                
    //             ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "w");
    //             FileInputStream fileInputStream = new FileInputStream(tmpFile);
    //             FileOutputStream fileOutputStream = new FileOutputStream(pfd.getFileDescriptor());
    //             copyStream(fileInputStream,fileOutputStream);
    //             fileInputStream.close();
    //             fileOutputStream.close();
    //             pfd.close();
    //             this.uri = uri;
    //         }
    //         else
    //         {
    //             String path = Uri.decode(uri.getEncodedPath());
    //             if(saveAsInternal(path) != 0)
    //                 throw new java.io.IOException("native code failed to save to "+uri.toString());
    //             this.uri = uri;
    //         }
    //     }
    
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
            boolean haveWritePermissionToUri = false;
            if (android.os.Build.VERSION.SDK_INT >= 19)
            {
                for( UriPermission permission : context.getContentResolver().getPersistedUriPermissions()) {
                    if(permission.isWritePermission() && permission.getUri().equals(uri))
                    {
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

    public Uri getUri(){
        return uri;
    }
    
    @Override
    public synchronized void onDestroy() {
        super.onDestroy();
        if(tmpFile != null) tmpFile.delete();
    }

    public static void createEmptyDocument(Context context, Uri uri) throws java.io.IOException, java.io.FileNotFoundException {
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
//                "%¥±ë"+newline+
//                "%\342\343\317\323"+newline+
//                "Â¥Â±Ã«"+newline+
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
}
