package jwtc.android.chess.tools;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;
import jwtc.android.chess.puzzle.MyPuzzleProvider;
import jwtc.android.chess.services.GameApi;
import jwtc.chess.PGNColumns;
import jwtc.chess.Pgn;
import jwtc.chess.PgnDocument;
import jwtc.chess.PgnFingerprint;
import jwtc.chess.PgnSyntaxError;
import jwtc.chess.board.BoardConstants;

/** Imports complete games that end in checkmate. */
public class PracticeImportHandler implements ImportHandler {
    private static final String TAG = "PracticeImportHandler";

    private final GameApi gameApi;
    private final ContentResolver contentResolver;

    public PracticeImportHandler(GameApi gameApi, ContentResolver contentResolver) {
        this.gameApi = gameApi;
        this.contentResolver = contentResolver;
    }

    @Override
    public Result handle(ImportItem item) {
        if (item.pgn == null || item.pgn.length() > Pgn.MAX_GAME_SIZE) {
            return Result.FAILED;
        }

        final PgnDocument document;
        try {
            document = Pgn.parse(item.pgn);
        } catch (PgnSyntaxError error) {
            return Result.FAILED;
        }
        int plies = document.mainLine.size();
        if (plies == 0 || !document.mainLine.get(plies - 1).san.endsWith("#")) {
            return Result.FAILED;
        }

        try {
            try (Cursor matches =
                    contentResolver.query(
                            MyPuzzleProvider.CONTENT_URI_PRACTICES,
                            new String[] {MyPuzzleProvider.COL_ID},
                            PGNColumns.FINGERPRINT + " = ?",
                            new String[] {PgnFingerprint.of(document)},
                            null)) {
                if (matches != null && matches.moveToFirst()) {
                    return Result.DUPLICATE;
                }
            }
            if (!gameApi.loadPGN(item.pgn) || gameApi.getEngineState() != BoardConstants.MATE) {
                return Result.FAILED;
            }

            ContentValues values = new ContentValues();
            values.put(PGNColumns.PGN, item.pgn);
            values.put(MyPuzzleProvider.COL_FULL_GAME, 1);
            return contentResolver.insert(MyPuzzleProvider.CONTENT_URI_PRACTICES, values) == null
                    ? Result.DUPLICATE
                    : Result.INSERTED;
        } catch (Exception error) {
            Log.e(TAG, "Could not import Practice Position", error);
            return Result.FAILED;
        }
    }
}
