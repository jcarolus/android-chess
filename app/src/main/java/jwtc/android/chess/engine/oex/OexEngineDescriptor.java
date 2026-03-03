package jwtc.android.chess.engine.oex;

import android.net.Uri;

import java.io.File;

public class OexEngineDescriptor {
    private final String name;
    private final String fileName;
    private final String authority;
    private final String packageName;
    private final int versionCode;

    public OexEngineDescriptor(String name, String fileName, String authority, String packageName, int versionCode) {
        this.name = name;
        this.fileName = fileName;
        this.authority = authority;
        this.packageName = packageName;
        this.versionCode = versionCode;
    }

    public String getName() {
        return name;
    }

    public String getFileName() {
        return fileName;
    }

    public String getAuthority() {
        return authority;
    }

    public String getPackageName() {
        return packageName;
    }

    public int getVersionCode() {
        return versionCode;
    }

    public String getId() {
        return packageName + ":" + fileName;
    }

    public Uri getContentUri() {
        return new Uri.Builder()
            .scheme("content")
            .authority(authority)
            .appendPath(fileName)
            .build();
    }

    public Uri getContentUriBasename() {
        String baseName = new File(fileName).getName();
        return new Uri.Builder()
            .scheme("content")
            .authority(authority)
            .appendPath(baseName)
            .build();
    }
}
