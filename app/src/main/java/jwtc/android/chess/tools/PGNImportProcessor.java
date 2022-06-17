package jwtc.android.chess.tools;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;

import java.util.Calendar;
import java.util.Date;

import jwtc.android.chess.services.GameApi;
import jwtc.chess.PGNColumns;

public class PGNImportProcessor extends PGNProcessor {

    private GameApi gameApi;
    private ContentResolver contentResolver;

    public PGNImportProcessor(GameApi gameApi, ContentResolver contentResolver) {
        this.gameApi = gameApi;
        this.contentResolver = contentResolver;
    }

    @Override
    public synchronized boolean processPGN(final String sPGN) {

        //Log.i("processPGN", sPGN);
        if (gameApi.loadPGN(sPGN)) {

            ContentValues values = new ContentValues();
            values.put(PGNColumns.EVENT, gameApi.getPGNHeadProperty("Event"));
            values.put(PGNColumns.WHITE, gameApi.getWhite());
            values.put(PGNColumns.BLACK, gameApi.getBlack());
            values.put(PGNColumns.PGN, gameApi.exportFullPGN());
            values.put(PGNColumns.RATING, 2.5F);

            // todo date goes wrong #################################
            Date dd = gameApi.getDate();
            if (dd == null) {
                dd = Calendar.getInstance().getTime();
            }
            values.put(PGNColumns.DATE, dd.getTime());

            Uri uri = Uri.parse("content://jwtc.android.chess.MyPGNProvider/games");
            Uri uriInsert = contentResolver.insert(uri, values);
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
