package jwtc.android.chess.tools;

import android.content.ContentResolver;
import android.content.ContentValues;

import jwtc.android.chess.puzzle.MyPuzzleProvider;
import jwtc.android.chess.services.GameApi;
import jwtc.chess.PGNColumns;

/** Stores each game of an import file as a Mate-in-Two Puzzle, if it loads on the board. */
public class PuzzleImportHandler implements ImportHandler {
    private final GameApi gameApi;
    private final ContentResolver contentResolver;

    public PuzzleImportHandler(GameApi gameApi, ContentResolver contentResolver) {
        this.gameApi = gameApi;
        this.contentResolver = contentResolver;
    }

    @Override
    public boolean handle(ImportItem item) {
        if (gameApi.loadPGN(item.pgn)) {
            ContentValues values = new ContentValues();
            values.put(PGNColumns.PGN, gameApi.exportFullPGN());

            contentResolver.insert(MyPuzzleProvider.CONTENT_URI_PUZZLES, values);
            return true;
        }
        return false;
    }
}
