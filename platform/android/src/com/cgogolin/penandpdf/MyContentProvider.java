package com.cgogolin.penandpdf;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsProvider;

import java.io.FileNotFoundException;

public class MyContentProvider extends DocumentsProvider {

    private final static String[] rootColumns = new String[]{
            "_id", "root_id", "title", "icon"
    };
    private final static String[] docColumns = new String[]{
        "_id", "document_id", "_display_name", "mime_type", "icon"
    };

    MatrixCursor matrixCursor;
    MatrixCursor matrixRootCursor;

    @Override
    public boolean onCreate() {

        matrixRootCursor = new MatrixCursor(rootColumns);
        matrixRootCursor.addRow(new Object[]{1, 1, "TEST", R.mipmap.ic_launcher});

        matrixCursor = new MatrixCursor(docColumns);
        matrixCursor.addRow(new Object[]{1, 1, "a.pdf", "application/pdf", R.mipmap.ic_launcher});
        matrixCursor.addRow(new Object[]{2, 2, "b.pdf", "application/pdf", R.mipmap.ic_launcher});
        matrixCursor.addRow(new Object[]{3, 3, "c.pdf", "application/pdf", R.mipmap.ic_launcher});

        return true;
    }

    @Override
    public Cursor queryRoots(String[] projection) throws FileNotFoundException {
        return matrixRootCursor;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection)
            throws FileNotFoundException {

        return matrixCursor;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection,
                                      String sortOrder)
            throws FileNotFoundException {

        return matrixCursor;
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode,
                                             CancellationSignal signal)
            throws FileNotFoundException {

        return null;
    }
}
