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
package com.kalab.chess.enginesupport;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

public class ChessEngine {

    private static final String TAG = ChessEngine.class.getSimpleName();

    private final String name;
    private final String fileName;
    private final String authority;
    private final String packageName;
    private final int versionCode;
    private final String licenseCheckActivity;

    public ChessEngine(String name, String fileName, String authority,
                       String packageName, int versionCode, String licenseCheckActivity) {
        this.name = name;
        this.fileName = fileName;
        this.authority = authority;
        this.packageName = packageName;
        this.versionCode = versionCode;
        this.licenseCheckActivity = licenseCheckActivity;
    }

    public String getName() {
        return this.name;
    }

    public String getFileName() {
        return this.fileName;
    }

    public Uri getUri() {
        return Uri.parse("content://" + authority + "/" + fileName);
    }

    public File copyToFiles(ContentResolver contentResolver, File destination)
            throws FileNotFoundException, IOException {
        Uri uri = getUri();
        File output = new File(destination, uri.getPath().toString());
        copyUri(contentResolver, uri, output.getAbsolutePath());
        return output;
    }

    public void copyUri(final ContentResolver contentResolver,
                        final Uri source, String targetFilePath) throws IOException,
            FileNotFoundException {
        InputStream istream = contentResolver.openInputStream(source);
        copyFile(istream, targetFilePath);
        setExecutablePermission(targetFilePath);
    }

    private void copyFile(InputStream istream, String targetFilePath)
            throws FileNotFoundException, IOException {
        FileOutputStream fout = new FileOutputStream(targetFilePath);
        byte[] b = new byte[1024];
        int numBytes = 0;
        while ((numBytes = istream.read(b)) != -1) {
            fout.write(b, 0, numBytes);
        }
        istream.close();
        fout.close();
    }

    private void setExecutablePermission(String engineFileName)
            throws IOException {
        String cmd[] = { "chmod", "744", engineFileName };
        Process process = Runtime.getRuntime().exec(cmd);
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    public String getPackageName() {
        return packageName;
    }

    public int getVersionCode() {
        return versionCode;
    }

    public String getAuthority() {
        return authority;
    }

    /**
     * Check the license of the engine.
     *
     * @param caller
     *            the activity which makes the license check
     * @param requestCode
     *            if >= 0, this code will be returned in onActivityResult() when the license check exits
     * @return true if a license check is performed, false if there is no need for a license check.
     *            If a license check is performed the caller must check the result in onActivityResult()
     */
    public boolean checkLicense(Activity caller, int requestCode) {
        return checkLicense(caller, requestCode, null);
    }

    public boolean checkLicense(Activity caller, int requestCode, Bundle extras) {
        boolean needsCheck = false;
        if (licenseCheckActivity != null) {
            needsCheck = true;
            Intent intent = new Intent();
            if (extras != null) {
                intent.putExtras(extras);
            }
            intent.setComponent(new ComponentName(packageName,
                    licenseCheckActivity));
            caller.startActivityForResult(intent, requestCode);
        }
        return needsCheck;
    }
}