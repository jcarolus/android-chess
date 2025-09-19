package jwtc.android.chess.tools;

import android.content.ContentResolver;
import android.os.Handler;

import jwtc.android.chess.services.GameApi;

public class OpeningImportProcessor extends PGNProcessor {
    private ImportApi importApi;
    private ContentResolver contentResolver;
    private String _sECO;
    private String _sName;
    private String _sVariation;

    OpeningImportProcessor(int mode, Handler updateHandler, ImportApi gameApi) {
        super(mode, updateHandler);

        this.importApi = gameApi;


    }

    @Override
    public boolean processPGN(String sPGN) {
        if (importApi.loadPGN(sPGN)) {
            _sECO = importApi.getPGNHeadProperty("Event");
            _sName = importApi.getPGNHeadProperty("White");





            return true;
        }
        return false;
    }



}
