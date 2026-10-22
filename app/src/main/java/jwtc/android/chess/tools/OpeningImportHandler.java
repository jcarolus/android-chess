package jwtc.android.chess.tools;

import android.util.Log;

/** Collects the final position of each opening under its name, skipping positions seen before. */
public class OpeningImportHandler implements ImportHandler {
    private static final String TAG = "OpeningImportHandler";

    private final ImportApi importApi;

    public OpeningImportHandler(ImportApi importApi) {
        this.importApi = importApi;
        importApi.resetHashMap();
    }

    @Override
    public boolean handle(ImportItem item) {
        if (importApi.loadPGN(item.pgn) && importApi.getNumBoard() > 0) {
            if (importApi.addToHashMap(importApi.getHashKey(), item.name)) {
                return true;
            }
            Log.d(TAG, "Duplicate hash");
        }
        return false;
    }
}
