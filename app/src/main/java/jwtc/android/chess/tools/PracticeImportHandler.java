package jwtc.android.chess.tools;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.util.Log;
import jwtc.android.chess.puzzle.MyPuzzleProvider;
import jwtc.android.chess.services.GameApi;
import jwtc.chess.PGNColumns;

/** Persists each selected Practice Position in the local collection. */
public class PracticeImportHandler implements ImportHandler {
    private static final String TAG = "PracticeImportHandler";

    private final PracticePositionSelector selector;
    private final ContentResolver contentResolver;

    public PracticeImportHandler(GameApi gameApi, ContentResolver contentResolver) {
        selector = new PracticePositionSelector(gameApi);
        this.contentResolver = contentResolver;
    }

    @Override
    public boolean handle(ImportItem item) {
        String pgn = selector.select(item.pgn);
        if (pgn == null) {
            return false;
        }
        try {
            ContentValues values = new ContentValues();
            values.put(PGNColumns.PGN, pgn);
            contentResolver.insert(MyPuzzleProvider.CONTENT_URI_PRACTICES, values);
            return true;
        } catch (Exception error) {
            Log.e(TAG, error.toString());
            return false;
        }
    }
}
