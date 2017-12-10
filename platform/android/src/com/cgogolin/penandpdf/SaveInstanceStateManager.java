package com.cgogolin.penandpdf;

// Partially taken from https://stackoverflow.com/questions/14256809/save-bundle-to-file

/* As the data returned by marshall can not be reliably read under a different
         * platform version we add the incremetal version to the file name. Restoring
         * then simply yields null.
         * see also: https://developer.android.com/reference/android/os/Parcel.html#marshall() */

import android.content.Context;
import android.os.Bundle;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import android.os.Parcel;
import java.lang.ClassLoader;

final class SaveInstanceStateManager
{
    private SaveInstanceStateManager() {}

    static public void saveBundleIfNecessary(Bundle bundle) {
        
        FileOutputStream fos = null;
        try {
            File bundleFile = File.createTempFile("instanceState", null);
            bundleFile.deleteOnExit();
            fos = new FileOutputStream(bundleFile);
            Parcel p = Parcel.obtain();
            bundle.writeToParcel(p, 0);
            fos.write(p.marshall());
            fos.flush();
            bundle.clear();
            bundle.putString(bundleFile.getAbsolutePath(), "bundleWasSavedToFileWithName");
        } catch (Exception e) {
        } finally {
            if(fos!=null)
                try {
                    fos.close();
                } catch (Exception e) {}
        }
    }
    
    static public Bundle recoverBundleIfNecessary(Bundle bundle, ClassLoader classLoader) {
        String path = bundle.getString("bundleWasSavedToFileWithName", null);
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
