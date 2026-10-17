package jwtc.android.chess.tools;

import android.content.ContentResolver;

import jwtc.android.chess.helpers.GameStore;
import jwtc.android.chess.services.GameApi;
import jwtc.chess.GameRecord;

/** Stores each game of an import file in the Game Database, if it loads on the board. */
public class GameImportHandler implements ImportHandler {
    private final GameApi gameApi;
    private final GameStore store;

    public GameImportHandler(GameApi gameApi, ContentResolver contentResolver) {
        this.gameApi = gameApi;
        this.store = new GameStore(contentResolver);
    }

    @Override
    public boolean handle(ImportItem item) {
        if (gameApi.loadPGN(item.pgn)) {
            store.insert(GameRecord.of(gameApi.exportFullPGN(), gameApi.pgnTags).withRating(2.5F));
            return true;
        }
        return false;
    }
}
