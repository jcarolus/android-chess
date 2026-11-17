package jwtc.android.chess.tools;

import android.util.Log;
import jwtc.android.chess.engine.PgnReplay;
import jwtc.chess.Pgn;
import jwtc.chess.PgnDocument;

/** Collects the final position of each opening under its name, skipping positions seen before. */
public class OpeningImportHandler implements ImportHandler {
    private static final String TAG = "OpeningImportHandler";

    private final ImportApi importApi;

    public OpeningImportHandler(ImportApi importApi) {
        this.importApi = importApi;
        importApi.resetHashMap();
    }

    @Override
    public Result handle(ImportItem item) {
        if (item.pgn == null || item.pgn.length() > Pgn.MAX_GAME_SIZE) {
            return Result.FAILED;
        }
        try {
            PgnDocument document = Pgn.parse(item.pgn);
            if (document.mainLine.isEmpty()) {
                return Result.FAILED;
            }
            if (importApi.addToHashMap(PgnReplay.validate(document).hashKey, item.name)) {
                return Result.INSERTED;
            }
            Log.d(TAG, "Duplicate hash");
        } catch (IllegalArgumentException error) {
            return Result.FAILED;
        }
        return Result.FAILED;
    }
}
