package com.kalab.chess.enginesupport;

/*
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


import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

public class ChessEngineProvider extends ContentProvider {

    private static final String MIME_TYPE = "application/x-chess-engine";
    private static final String UNSUPPORTED = "Not supported by this provider";
    private static final String TAG = ChessEngineProvider.class.getSimpleName();

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public AssetFileDescriptor openAssetFile(Uri uri, String mode)
            throws FileNotFoundException {
        AssetManager manager = getContext().getAssets();
        String fileName = uri.getLastPathSegment();
        if (fileName == null) {
            throw new FileNotFoundException();
        }
        AssetFileDescriptor descriptor = null;
        try {
            descriptor = manager.openFd(fileName);
        } catch (IOException e) {
            Log.d(TAG,
                    "Engine file <"
                            + fileName
                            + "> was not found in assets, trying to load from libraries.");
            String libFileName = getContext().getApplicationInfo().dataDir
                    + File.separator + "lib" + File.separator + fileName;
            try {
                descriptor = new AssetFileDescriptor(openLibFile(new File(
                        libFileName)), 0, AssetFileDescriptor.UNKNOWN_LENGTH);
            } catch (IOException ex) {
                String msg = "Error opening file <" + libFileName + ">.";
                Log.e(TAG, msg, ex);
                throw new FileNotFoundException(msg + "\n"
                        + ex.getLocalizedMessage());
            }
        }
        return descriptor;
    }

    public ParcelFileDescriptor openLibFile(File f)
            throws FileNotFoundException {
        return ParcelFileDescriptor
                .open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return MIME_TYPE;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }
}