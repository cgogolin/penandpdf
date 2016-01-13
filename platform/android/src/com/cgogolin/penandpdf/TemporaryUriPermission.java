package com.cgogolin.penandpdf;

import android.content.Intent;
import android.net.Uri;
import java.util.ArrayList;

public class TemporaryUriPermission {

    interface TemporaryUriPermissionProvider {
        public ArrayList<TemporaryUriPermission> getTemporaryUriPermissions();
    }
    
    Uri uri;
    int flags;
    
    public TemporaryUriPermission(Intent intent) {
        uri = intent.getData();
        flags = intent.getFlags();
    }
    
    public Uri	getUri() {
        return uri;
    }
        
    boolean	isReadPermission() {
        return ((flags & Intent.FLAG_GRANT_READ_URI_PERMISSION) == Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }
        
    boolean	isWritePermission() {
        return ((flags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
    }
}


