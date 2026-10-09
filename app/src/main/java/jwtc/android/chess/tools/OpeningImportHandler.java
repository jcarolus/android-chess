package jwtc.android.chess.tools;

import android.util.Log;

import jwtc.chess.JNI;

/** Collects the final position of each opening under its name, skipping positions seen before. */
public class OpeningImportHandler implements ImportHandler {
    private static final String TAG = "OpeningImportHandler";

    private final ImportApi importApi;
    private final JNI jni = JNI.getInstance();

    public OpeningImportHandler(ImportApi importApi) {
        this.importApi = importApi;
        importApi.resetHashMap();
    }

    @Override
    public boolean handle(ImportItem item) {
        if (importApi.loadPGN(item.pgn) && jni.getNumBoard() > 0) {
            if (importApi.addToHashMap(jni.getHashKey(), item.name)) {
                return true;
            }
            Log.d(TAG, "Duplicate hash");
        }
        return false;
    }
}
