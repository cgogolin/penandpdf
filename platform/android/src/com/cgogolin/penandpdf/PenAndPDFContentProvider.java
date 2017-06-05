/*
 * Copyright (C) 2015 Christian Gogolin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// based on https://android.googlesource.com/platform/frameworks/base/+/4ec9739/packages/ExternalStorageProvider/src/com/android/externalstorage/ExternalStorageProvider.java

package com.cgogolin.penandpdf;

import android.content.SharedPreferences;
import android.content.Context;
import android.content.ContentProvider;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;

import android.provider.MediaStore.MediaColumns;
import android.provider.MediaStore;

//import android.os.Bundle;
import android.content.ContentResolver;
//import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MatrixCursor.RowBuilder;
//import android.graphics.Point;
//import android.media.ExifInterface;
import android.os.CancellationSignal;
//import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import android.webkit.MimeTypeMap;
//import com.google.android.collect.Lists;
//import com.google.android.collect.Maps;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
// import java.util.ArrayList;
// import java.util.HashMap;
import java.util.LinkedList;
import android.util.Log;
// import java.util.Map;

public class PenAndPDFContentProvider extends DocumentsProvider {
    
    private final String ROOT_NOTES = "PenAndPDFNotesProvider";
    private File mNotesDir;

    private static final String[] DEFAULT_ROOT_PROJECTION = new String[] {
        Root.COLUMN_AVAILABLE_BYTES, //Number of bytes available in this root.
        Root.COLUMN_DOCUMENT_ID, //Document which is a directory that represents the top directory of this root.
        Root.COLUMN_FLAGS, //Flags that apply to a root.
        Root.COLUMN_ICON, //Icon resource ID for a root.
        Root.COLUMN_MIME_TYPES, //MIME types supported by this root.
        Root.COLUMN_ROOT_ID, //Unique ID of a root.
        Root.COLUMN_SUMMARY, //Summary for this root, which may be shown to a user.
        Root.COLUMN_TITLE, //Title for a root, which will be shown to a user.
    };
    private static final String[] DEFAULT_DOCUMENT_PROJECTION = new String[] {
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_MIME_TYPE,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_LAST_MODIFIED,
        Document.COLUMN_FLAGS,
        Document.COLUMN_SIZE,
    };
    

    private static class RootInfo {
        public String rootId;
        public int rootType;
        public int flags;
        public int icon;
        public String title;
        public String docId;
    }
    
    @Override
    public boolean onCreate() {
        mNotesDir = PenAndPDFActivity.getNotesDir(getContext());

        for( UriPermission permission : getContext().getContentResolver().getOutgoingPersistedUriPermissions()) {
        }
        
        return true;
    }
    
	// public static File getNotesDir(Context contex) {
    //     return contex.getDir("notes", Context.MODE_WORLD_READABLE);
    // }

    private static String[] resolveRootProjection(String[] projection) {
        return projection != null ? projection : DEFAULT_ROOT_PROJECTION;
    }

    private static String[] resolveDocumentProjection(String[] projection) {
        return projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION;
    }
    
    
    private String getDocIdForFile(File file) {
//        return file.getName();
        return file.getAbsolutePath().substring(1);//remove the leading '/'
    }

    private File getFileForDocId(String documentId) {
//        Log.i("PenAndPDFContentProvider", "getFileForDocId for id "+documentId+" and mNotesDir.getName()="+mNotesDir.getName());
        if(documentId.equals(mNotesDir.getName())) 
            return mNotesDir;
        else
//            return new File(mNotesDir.getPath(), documentId);
            return new File("/"+documentId);
    }
    
    private String getChildMimeTypes(File file) {
        return "application/pdf";
    }

        // Adds the file's display name, MIME type, size, and so on to the
        // given MatrixCursor
    private void includeFile(MatrixCursor result, String documentId, File file) {
        if (documentId == null) {
            documentId = getDocIdForFile(file);
        } else {
            file = getFileForDocId(documentId);
        }
        int flags = 0;
        if (file.isDirectory() && file.canWrite()) {
            flags |= Document.FLAG_DIR_SUPPORTS_CREATE;
        }
        if (file.canWrite()) {
            flags |= Document.FLAG_SUPPORTS_WRITE;
            flags |= Document.FLAG_SUPPORTS_DELETE;
            flags |= Document.FLAG_SUPPORTS_RENAME;
            flags |= Document.FLAG_SUPPORTS_DELETE;
        }
        flags |= Document.FLAG_DIR_PREFERS_LAST_MODIFIED;
            
        final String displayName = file.getName();
        final String mimeType = getTypeForFile(file);
        long length = file.length();
        long lastModified = file.lastModified();
   
        if(!Document.MIME_TYPE_DIR.equals(mimeType) && !mimeType.endsWith("pdf"))
            return;
        // if (mimeType.startsWith("image/")) {
        //     flags |= Document.FLAG_SUPPORTS_THUMBNAIL;
        // }
        final RowBuilder row = result.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, documentId);
        row.add(Document.COLUMN_DISPLAY_NAME, displayName);
        row.add(Document.COLUMN_SIZE, length);
        row.add(Document.COLUMN_MIME_TYPE, mimeType);
        row.add(Document.COLUMN_LAST_MODIFIED, lastModified);
        row.add(Document.COLUMN_FLAGS, flags);

        Log.i("PenAndPDFContentProvider", "includeFile()       id="+documentId+" displayName="+displayName+" flags="+flags+ " length="+length+" lastModified="+lastModified);
    }

    private void includeForeignUri(MatrixCursor result, Uri uri, long lastModified) {
        return;//disable this as it doesn't work reliably
        
//         Cursor cursor = getContext().getContentResolver().query(uri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.TITLE}, null, null, null); //This should be done asynchonously!

//         if (cursor == null)
//             return;
//         cursor.moveToFirst();                  
            
//         String documentId = uri.toString();
//         int flags = 0;
// //        flags |= Document.FLAG_SUPPORTS_WRITE;
//         String displayName = "";
//         String mimeType = "";
//         long length = 0;
        
//         int index;
//         index = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
//         if(index>=0) displayName = cursor.getString(index);
//         if(displayName.equals("")) 
//         {
//             index = cursor.getColumnIndex(MediaStore.MediaColumns.TITLE);
//             if(index>=0) displayName = cursor.getString(index);
//         }
//         displayName += " - Pen&PDF";
//         index = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE);
//         if(index>=0) mimeType = cursor.getString(index);
//         index = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE);
//         if(index>=0) {
//             length = cursor.getLong(index);
//         }
//         index = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED);
//         if(lastModified==0l && index>=0) {
//             lastModified = cursor.getLong(index);
//         }
//         if(lastModified==0l) lastModified=1;
            
//         Log.i("PenAndPDFContentProvider", "includeForeignUri() id="+documentId+" displayName="+displayName+" flags="+flags+ " length="+length+" lastModified="+lastModified);
        
//         final RowBuilder row = result.newRow();
//         row.add(Document.COLUMN_DOCUMENT_ID, documentId);
//         row.add(Document.COLUMN_DISPLAY_NAME, displayName);
//         row.add(Document.COLUMN_SIZE, length);
//         row.add(Document.COLUMN_MIME_TYPE, mimeType);
//         row.add(Document.COLUMN_LAST_MODIFIED, lastModified);
//         row.add(Document.COLUMN_FLAGS, flags);
    }
    
    @Override
    public Cursor queryRoots(String[] projection) throws FileNotFoundException {

            // Create a cursor with either the requested fields, or the default
            // projection if "projection" is null.
        final MatrixCursor result = new MatrixCursor(resolveRootProjection(projection));
    
        final MatrixCursor.RowBuilder notesRoot = result.newRow();
        notesRoot.add(Root.COLUMN_ROOT_ID, ROOT_NOTES);
        notesRoot.add(Root.COLUMN_SUMMARY, getContext().getString(R.string.notes_provider_summary));

            // FLAG_SUPPORTS_CREATE means at least one directory under the root supports
            // creating documents. FLAG_SUPPORTS_RECENTS means your application's most
            // recently used documents will show up in the "Recents" category.
            // FLAG_SUPPORTS_SEARCH allows users to search all documents the application
            // shares.
        notesRoot.add(Root.COLUMN_FLAGS,
//                      Root.FLAG_SUPPORTS_CREATE |
                      Root.FLAG_SUPPORTS_RECENTS | //must implement queryRecentDocuments()
//                      Root.FLAG_SUPPORTS_SEARCH | //must implememt querySearchDocuments()
                      Root.FLAG_LOCAL_ONLY
                      );

            // COLUMN_TITLE is the root title (e.g. Gallery, Drive).
        notesRoot.add(Root.COLUMN_TITLE, getContext().getString(R.string.notes_provider_title));

            // This document id cannot change once it's shared.
        notesRoot.add(Root.COLUMN_DOCUMENT_ID, getDocIdForFile(mNotesDir));

            // The child MIME types are used to filter the roots and only present to the
            //  user roots that contain the desired type somewhere in their file hierarchy.
        notesRoot.add(Root.COLUMN_MIME_TYPES, getChildMimeTypes(mNotesDir));
        notesRoot.add(Root.COLUMN_AVAILABLE_BYTES, mNotesDir.getFreeSpace());
        notesRoot.add(Root.COLUMN_ICON, R.mipmap.ic_launcher);

        return result;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder) throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));
        final File parent = getFileForDocId(parentDocumentId);
        if(parent == null) return null;
//        Log.i("PenAndPDFContentProvider", "queryChildDocuments() for parent "+parent.getPath()+" with id "+parentDocumentId);
        for (File file : parent.listFiles()) {
            includeFile(result, null, file);
        }
        return result;
    }

    @Override
    public Cursor queryRecentDocuments(String rootId, String[] projection) {
//        Log.i("PenAndPDFContentProvider", "queryRecentDocuments() for rootId "+rootId);
        final MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));
            //Return recently modified documents under the requested root.
            //Here we cheat and not only return notes, but also other documents
            //that were recently opened in Pen&Pdf. These have ids that start
            //with content:// as we simply take their uri to the the id. This
            //allows us to open a ParcelFileDescriptor to them in openDocument().
        if(ROOT_NOTES.equals(rootId))
        {   
                //Read the recent files list from preferences
            SharedPreferences prefs = getContext().getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
            RecentFilesList recentFilesList = new RecentFilesList(getContext(), prefs);
            
            for(RecentFile recentFile: recentFilesList)
            {
                String uriString = recentFile.getFileString();
                File file = new File(Uri.parse(uriString).getPath());
                if(file.exists() && file.isFile() && file.canRead()) {
//                    Log.i("PenAndPDFContentProvider", "adding file to recent files list");
                    includeFile(result, null, file);                    
                }
                else {
//                    Log.i("PenAndPDFContentProvider", "adding uri to recent files list");
                    includeForeignUri(result, recentFile.getUri(), recentFile.getLastModified());
                }
                    
            }
        }
        
        return result;
    }

    // @Override
    // public Cursor querySearchDocuments(String rootId, String query, String[] projection) {
    //         //Return documents that match the given query under the requested root.
    // }
    
    
    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        Log.i("PenAndPDFContentProvider", "queryDocument() with id "+documentId);
            // Create a cursor with the requested projection, or the default projection.
        final MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));
        if(documentId.startsWith("content://"))
            includeForeignUri(result, Uri.parse(documentId), 0l);
        else
            includeFile(result, documentId, null);
        return result;
    }

    @Override
    public ParcelFileDescriptor openDocument(final String documentId, final String accessMode, CancellationSignal signal) throws FileNotFoundException {
        ParcelFileDescriptor pfd;
            // It's OK to do long operatos in this method as long as you periodically
            // check the CancellationSignal.

        if(documentId.startsWith("content://")) 
        {
            final Uri uri = Uri.parse(documentId);
//            pfd = getContext().getContentResolver().openFileDescriptor(uri, "rw");
//            if(pfd==null)
            pfd = getContext().getContentResolver().openFileDescriptor(uri, "r");
//            Log.i("PenAndPDFContentProvider", " openDocument() with accessMode="+accessMode+" parsed="+ParcelFileDescriptor.parseMode(accessMode)+" MODE_READ_WRITE="+ParcelFileDescriptor.MODE_READ_WRITE+" for uri "+uri);
        }    
        else 
        {
            final File file = getFileForDocId(documentId);
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(accessMode));
//            Log.i("PenAndPDFContentProvider", " openDocument() with accessMode="+accessMode+" parsed="+ParcelFileDescriptor.parseMode(accessMode)+" MODE_READ_WRITE="+ParcelFileDescriptor.MODE_READ_WRITE+" for file "+file);
        }

        
        // final boolean isWrite = (accessMode.indexOf('w') != -1);
        // if(isWrite) {
            
        // }
        
            // if(isWrite) {
            //         // Attach a close listener if the document is opened in write mode.
            //     try {
            //         Handler handler = new Handler(getContext().getMainLooper());
            //         return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(accessMode), handler, new ParcelFileDescriptor.OnCloseListener() {
            //                 @Override
            //                 public void onClose(IOException e) {
            //                         // Update the file with the cloud server. The client is done
            //                         // writing.
            //                     Log.i(TAG, "A file with id " + documentId + " has been closed! Time to " + "update the server.");
            //                 }
            //             });
            //     } catch (IOException e) {
            //         throw new FileNotFoundException("Failed to open document with id " + documentId + " and mode " + mode);
            //     }
            // } else {
        return pfd;
            //    }
    }

    @Override
    public void deleteDocument (String documentId) {
        final File file = getFileForDocId(documentId);
        file.delete();
    }
        
    @Override
    public String renameDocument(String documentId, String displayName) {
        final File file = getFileForDocId(documentId);
        file.delete();
        File newFile = new File(file.getParent(), displayName);
        file.renameTo(newFile);
        return getDocIdForFile(newFile);
    }
    
    @Override
    public Cursor querySearchDocuments(String parentDocumentId, String query, String[] projection) throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));
        final File parent = getFileForDocId(parentDocumentId);
        final LinkedList<File> pending = new LinkedList<File>();
        pending.add(parent);
        while (!pending.isEmpty() && result.getCount() < 24) {
            final File file = pending.removeFirst();
            if (file.isDirectory()) {
                for (File child : file.listFiles()) {
                    pending.add(child);
                }
            }
            if (file.getName().toLowerCase().contains(query)) {
                includeFile(result, null, file);
            }
        }
        return result;
    }

    @Override
    public String getDocumentType(String documentId) throws FileNotFoundException {
        final File file = getFileForDocId(documentId);
        return getTypeForFile(file);
    }

    private static String getTypeForFile(File file) {
        if (file.isDirectory()) {
            return Document.MIME_TYPE_DIR;
        } else {
            return getTypeForName(file.getName());
        }
    }

    private static String getTypeForName(String name) {
        final int lastDot = name.lastIndexOf('.');
        if (lastDot >= 0) {
            final String extension = name.substring(lastDot + 1);
            final String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mime != null) {
                return mime;
            }
        }   
        return "application/octet-stream";
    }
        
     private static String validateDisplayName(String mimeType, String displayName) {
        if (Document.MIME_TYPE_DIR.equals(mimeType)) {
            return displayName;
        } else {
            // Try appending meaningful extension if needed
            if (!mimeType.equals(getTypeForName(displayName))) {
                final String extension = MimeTypeMap.getSingleton()
                        .getExtensionFromMimeType(mimeType);
                if (extension != null) {
                    displayName += "." + extension;
                }
            }
            return displayName;
        }
    }
}

 
