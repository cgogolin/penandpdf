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
// import java.util.Map;

public class PenAndPDFContentProvider extends DocumentsProvider {
    
        //     //Read the recent files list from preferences
        // SharedPreferences prefs = getActivity().getSharedPreferences(SettingsActivity.SHARED_PREFERENCES_STRING, Context.MODE_MULTI_PROCESS);
        // RecentFilesList recentFilesList = new RecentFilesList(prefs);

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
        mNotesDir = getNotesDir(getContext());
        return true;
    }


    public static File getNotesDir(Context contex) {
        return contex.getDir("notes", Context.MODE_WORLD_READABLE);
    }

    private static String[] resolveRootProjection(String[] projection) {
        return projection != null ? projection : DEFAULT_ROOT_PROJECTION;
    }

    private static String[] resolveDocumentProjection(String[] projection) {
        return projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION;
    }
    
    
    private String getDocIdForFile(File file) {
        return file.getName();
    }

    private File getFileForDocId(String documentId) {
        return new File(mNotesDir.getPath(), documentId);
    }
    
    private String getChildMimeTypes(File file) {
        return "application/pdf";
    }

        // Adds the file's display name, MIME type, size, and so on.
    private void includeFile(MatrixCursor result, String documentId, File file) {
        if (documentId == null) {
            documentId = getDocIdForFile(file);
        } else {
            file = getFileForDocId(documentId);
        }
        int flags = 0;
        // if (file.isDirectory()) {
        //     flags |= Document.FLAG_SUPPORTS_SEARCH;
        // }
        // if (file.isDirectory() && file.canWrite()) {
        //     flags |= Document.FLAG_DIR_SUPPORTS_CREATE;
        // }
        if (file.canWrite()) {
            flags |= Document.FLAG_SUPPORTS_WRITE;
            flags |= Document.FLAG_SUPPORTS_DELETE;
        }
        final String displayName = file.getName();
        final String mimeType = getTypeForFile(file);
        // if (mimeType.startsWith("image/")) {
        //     flags |= Document.FLAG_SUPPORTS_THUMBNAIL;
        // }
        final RowBuilder row = result.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, documentId);
        row.add(Document.COLUMN_DISPLAY_NAME, displayName);
        row.add(Document.COLUMN_SIZE, file.length());
        row.add(Document.COLUMN_MIME_TYPE, mimeType);
        row.add(Document.COLUMN_LAST_MODIFIED, file.lastModified());
        row.add(Document.COLUMN_FLAGS, flags);
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
                      Root.FLAG_SUPPORTS_RECENTS |
                      Root.FLAG_SUPPORTS_SEARCH
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
        
        for (File file : parent.listFiles()) {
            includeFile(result, null, file);
        }
        return result;
    }


    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        
            // Create a cursor with the requested projection, or the default projection.
        final MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));
        includeFile(result, documentId, null);
        return result;
    }

    @Override
    public ParcelFileDescriptor openDocument(final String documentId, final String accessMode, CancellationSignal signal) throws FileNotFoundException {
//        Log.v(TAG, "openDocument, mode: " + accessMode);
            // It's OK to do long operatos in this method as long as you periodically
            // check the CancellationSignal.
        
        final File file = getFileForDocId(documentId);

            // final boolean isWrite = (mode.indexOf('w') != -1);
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
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(accessMode));
            //    }
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

 
