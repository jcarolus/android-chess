package jwtc.android.chess.tools;

import android.content.ContentResolver;
import java.util.logging.Level;
import java.util.logging.Logger;
import jwtc.android.chess.helpers.GameStore;
import jwtc.chess.GameRecord;
import jwtc.chess.PgnSyntaxError;

/** Stores syntactically valid PGN in the Game Database; move legality is checked on opening. */
public class GameImportHandler implements ImportHandler {
    private static final Logger LOG = Logger.getLogger(GameImportHandler.class.getName());
    private final GameStore store;

    public GameImportHandler(ContentResolver contentResolver) {
        this.store = new GameStore(contentResolver);
    }

    @Override
    public Result handle(ImportItem item) {
        final GameRecord record;
        try {
            record = GameRecord.fromPgn(item.pgn).withRating(2.5F);
        } catch (PgnSyntaxError ex) {
            LOG.log(
                    Level.WARNING,
                    "Skipping invalid PGN game "
                            + item.sourcePosition
                            + " from "
                            + (item.sourceName == null ? "selected file" : item.sourceName),
                    ex);
            return Result.FAILED;
        }
        return store.importGame(record) ? Result.INSERTED : Result.DUPLICATE;
    }
}
