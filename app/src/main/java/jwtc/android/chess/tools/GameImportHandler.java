package jwtc.android.chess.tools;

import android.content.ContentResolver;
import jwtc.android.chess.helpers.GameStore;
import jwtc.chess.GameRecord;
import jwtc.chess.PgnSyntaxError;

/** Stores syntactically valid PGN in the Game Database; move legality is checked on opening. */
public class GameImportHandler implements ImportHandler {
    private final GameStore store;

    public GameImportHandler(ContentResolver contentResolver) {
        this.store = new GameStore(contentResolver);
    }

    @Override
    public boolean handle(ImportItem item) {
        final GameRecord record;
        try {
            record = GameRecord.fromPgn(item.pgn).withRating(2.5F);
        } catch (PgnSyntaxError ex) {
            return false;
        }
        return store.insert(record) > 0;
    }
}
