package jwtc.android.chess.tools;

import android.content.ContentResolver;
import android.os.Handler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import jwtc.android.chess.services.GameApi;
import jwtc.chess.PGNEntry;

public class OpeningImportProcessor extends PGNProcessor {
    private GameApi gameApi;
    private ContentResolver contentResolver;
    private JSONArray _jArray;
    private ArrayList<PGNEntry> _arrMoves;
    private String _sECO;
    private String _sName;
    private String _sVariation;

    OpeningImportProcessor(int mode, Handler updateHandler, GameApi gameApi) {
        super(mode, updateHandler);

        this.gameApi = gameApi;

        _jArray = new JSONArray();
    }

    @Override
    public boolean processPGN(String sPGN) {
        if (gameApi.loadPGN(sPGN)) {
            _sECO = gameApi.getPGNHeadProperty("Event");
            _sName = gameApi.getPGNHeadProperty("White");
            _sVariation = gameApi.getPGNHeadProperty("Black");
            if (_sVariation.equals("black ?")) {
                _sVariation = "";
            }
            _arrMoves = gameApi.getPGNEntries();

            findOrInsertEntry(_jArray, _arrMoves.remove(0));

            return true;
        }
        return false;
    }


    // m = move
// a = array
// e = ECO
// v = variation
    protected void findOrInsertEntry(JSONArray curArray, PGNEntry entry) {
        boolean bFound = false;
        for (int i = 0; i < curArray.length(); i++) {
            try {
                JSONObject jObj = (JSONObject) curArray.get(i);

                if (jObj.getString("m").equals(entry._sMove)) {

                    bFound = true;
                    JSONArray newArray;
                    if (jObj.has("a")) {
                        newArray = (JSONArray) jObj.get("a");
                    } else {
                        newArray = new JSONArray();
                        jObj.put("a", newArray);
                    }

                    if (_arrMoves.size() == 0) {
                        jObj.put("e", _sECO);
                        jObj.put("n", _sName);
                        jObj.put("v", _sVariation);
                    } else {
                        findOrInsertEntry(newArray, _arrMoves.remove(0));
                    }

                }

            } catch (JSONException e) {

            }

        }
        if (false == bFound) {
            JSONObject newObject = new JSONObject();
            try {
                JSONArray newArray = new JSONArray();

                newObject.put("m", entry._sMove);
                newObject.put("a", newArray);

                if (_arrMoves.size() == 0) {
                    newObject.put("e", _sECO);
                    newObject.put("n", _sName);
                    newObject.put("v", _sVariation);
                }

                curArray.put(newObject);

                if (_arrMoves.size() > 0) {
                    findOrInsertEntry(newArray, _arrMoves.remove(0));
                }

            } catch (JSONException e) {
            }
        }
    }

    @Override
    public String getString() {
        return _jArray.toString();
    }
}
