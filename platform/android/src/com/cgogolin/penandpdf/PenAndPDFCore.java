package com.cgogolin.penandpdf;
import java.util.ArrayList;

import android.util.Base64;
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


public class PenAndPDFCore extends MuPDFCore
{
    private Uri uri = null;
    private File tmpFile = null;

    public PenAndPDFCore(Context context, Uri uri) throws Exception
	{
            this.uri = uri;
            if(new File(Uri.decode(uri.getEncodedPath())).isFile()) //Uri points to a file
            {
                super.init(context, Uri.decode(uri.getEncodedPath()));
            }
            else if (uri.toString().startsWith("content://")) //Uri points to a content provider
            {
                String displayName = null;
                Cursor cursor = context.getContentResolver().query(uri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null); //This should be done asynchonously!            
                if (cursor != null && cursor.moveToFirst())
                {
                    int displayNameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
                    if(displayNameIndex >= 0) displayName = cursor.getString(displayNameIndex);
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
            this.uri = uri;
            
            if(new File(Uri.decode(uri.getEncodedPath())).isFile())
            {
                String path = Uri.decode(uri.getEncodedPath());
                if(saveAsInternal(path) != 0) throw new java.io.IOException("native code failed to save to "+uri.toString());
            }
            else //Not a file so we have to write to a tmp file from the native code first 
            {
                if(tmpFile == null) {
                    File cacheDir = context.getCacheDir();
                    tmpFile = File.createTempFile("prefix", "pdf", cacheDir);
                }    
                
                saveAsInternal(tmpFile.getPath());
                
                ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "w");
                FileOutputStream fileOutputStream = new FileOutputStream(pfd.getFileDescriptor());
                FileInputStream fileInputStream = new FileInputStream(tmpFile);
                copyStream(fileInputStream,fileOutputStream);             
                fileInputStream.close();
                fileOutputStream.close();
                pfd.close();
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

    public boolean canSaveToCurrentLocation() {
        return getFileName() != null && getPath() != null;
    }

    public Uri getUri(){
        return uri;
    }
    
    @Override
    public synchronized void onDestroy() {
        super.onDestroy();
        if(tmpFile != null) tmpFile.delete();
    }
}
