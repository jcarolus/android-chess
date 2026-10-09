package jwtc.android.chess.tools;

import android.content.ContentResolver;
import android.os.Handler;


import jwtc.android.chess.helpers.GameStore;
import jwtc.android.chess.services.GameApi;
import jwtc.chess.GameRecord;

public class GameImportProcessor extends PGNProcessor {
    private static final String TAG = "GameImportProcessor";

    private GameApi gameApi;
    private GameStore store;

    public GameImportProcessor(int mode, Handler updateHandler, GameApi gameApi, ContentResolver contentResolver) {
        super(mode, updateHandler);
        this.gameApi = gameApi;
        this.store = new GameStore(contentResolver);
    }

    @Override
    public synchronized boolean processPGN(final String sPGN) {

        //Log.i("processPGN", sPGN);
        if (gameApi.loadPGN(sPGN)) {

            store.insert(GameRecord.of(gameApi.exportFullPGN(), gameApi.pgnTags).withRating(2.5F));
            return true;
        }
        return false;
    }
}
