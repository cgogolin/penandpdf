package com.cgogolin.penandpdf;

// Partially taken from https://stackoverflow.com/questions/14256809/save-bundle-to-file

/* If the bundle passed to onSaveInstanceState() is too large, which can happen because here it contains the drawing and drawing history,
 * the data is lost and under Android N the app hard crashes with a android.os.TransactionTooLargeException,
 * therefore through this handler we instead save the bundle to a file and restore from there in onResume()
 * A warning: As the data returned by marshall can not be reliably read under a different
 * platforms one shouldn't save a marshaled bundle to permanent storage. Here we are only saving to a temporary file wich is deleted when the VM exits and this should be OK.
 * See also: https://developer.android.com/reference/android/os/Parcel.html#marshall(). */

import android.content.Context;
import android.os.Bundle;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import android.os.Parcel;
import java.lang.ClassLoader;

final class SaveInstanceStateManager
{
    private static String bundleFileNamePrefix = "instanceState";
    private static String bundleFileNameMarkerString = "bundleWasSavedToFileWithName";
    
    private SaveInstanceStateManager() {}

    static public Bundle saveBundleIfNecessary(Bundle bundle) {
        
        FileOutputStream fos = null;
        try {
            File bundleFile = File.createTempFile(bundleFileNamePrefix, null);
            bundleFile.deleteOnExit();
            fos = new FileOutputStream(bundleFile);
            Parcel p = Parcel.obtain();
            bundle.writeToParcel(p, 0);
            fos.write(p.marshall());
            fos.flush();
            bundle.clear();
            bundle.putString(bundleFileNameMarkerString, bundleFile.getAbsolutePath());
        } catch (Exception e) {
        } finally {
            if(fos!=null)
                try {
                    fos.close();
                } catch (Exception e) {}
        }
        return bundle;
    }
    
    static public Bundle recoverBundleIfNecessary(Bundle bundle, ClassLoader classLoader) {
        if(bundle == null)
            return bundle;
        String path = bundle.getString(bundleFileNameMarkerString, null);
        if(path == null)
            return bundle; //The Bundle was apparently not saved so we simply return it unchanged

        Bundle out = null;
        Parcel parcel = Parcel.obtain();
        FileInputStream fis = null;
        File bundleFile = null;
        try {
            bundleFile = new File(path);
            fis = new FileInputStream(bundleFile);
            byte[] array = new byte[(int) fis.getChannel().size()];
            fis.read(array, 0, array.length);
            parcel.unmarshall(array, 0, array.length);
            parcel.setDataPosition(0);
            out = parcel.readBundle(classLoader);
        } catch (Exception e) {
        } finally {
            if(parcel!=null)
                parcel.recycle();
            if(fis!=null)
                try 
                {
                    fis.close();
                } catch (Exception e) {}
        }
        try
        {
            bundleFile.delete();
        } catch(Exception e) {}

        return out;
    }
}
