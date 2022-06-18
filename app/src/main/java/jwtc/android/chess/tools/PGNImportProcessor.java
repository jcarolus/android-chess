package jwtc.android.chess.tools;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.util.Log;

import java.util.Calendar;
import java.util.Date;

import jwtc.android.chess.puzzle.MyPuzzleProvider;
import jwtc.android.chess.services.GameApi;
import jwtc.chess.PGNColumns;

public class PGNImportProcessor extends PGNProcessor {
    private static final String TAG = "PGNImportProcessor";

    private GameApi gameApi;
    private ContentResolver contentResolver;

    public PGNImportProcessor(GameApi gameApi, ContentResolver contentResolver) {
        this.gameApi = gameApi;
        this.contentResolver = contentResolver;
    }

    @Override
    public synchronized boolean processPGN(final String sPGN) {

        if (gameApi.loadPGN(sPGN)) {

            Log.d(TAG, "processPGN success");

            ContentValues values = new ContentValues();
            values.put(PGNColumns.PGN, gameApi.exportFullPGN());

            Uri uriInsert = contentResolver.insert(MyPuzzleProvider.CONTENT_URI_PUZZLES, values);

            Log.d(TAG, "inserted " + (uriInsert != null ? uriInsert.toString() : "null"));
            return true;
        }
        return false;
    }

    @Override
    public String getString() {
        // TODO Auto-generated method stub
        return null;
    }

}
