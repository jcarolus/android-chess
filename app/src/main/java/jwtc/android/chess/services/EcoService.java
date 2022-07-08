package jwtc.android.chess.services;

import android.content.res.AssetManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import jwtc.chess.PGNEntry;

public class EcoService {
    private static final String TAG = "EcoService";
    private JSONArray _jArrayECO = null;
    private String _sPrevECO;

    public void load(final AssetManager assetManager) {
        _sPrevECO = null;

        if (_jArrayECO == null) {

            (new Thread(new Runnable() {
                public void run() {
                    try {
                        Thread.sleep(5000);

                        long start = System.currentTimeMillis();
                        InputStream in = assetManager.open("ECO.json");
                        BufferedReader br = new BufferedReader(new InputStreamReader(in));

                        StringBuffer sb = new StringBuffer("");
                        String line = "";

                        while ((line = br.readLine()) != null) {
                            sb.append(line + "\n");
                        }

                        in.close();

                        _jArrayECO = new JSONArray(sb.toString());
                        Log.i(TAG, "ECO jArray - size " + _jArrayECO.length() + " load " + (System.currentTimeMillis() - start));

                    } catch (Exception e) {

                    }
                }
            })).start();
        }
    }

    public String getEco(final ArrayList<PGNEntry> _arrPGN) {
        if (_jArrayECO != null) {
            String sECO = getECOInfo(0, _arrPGN, _jArrayECO);
            Log.i(TAG, sECO == null ? "No ECO" : sECO);
            if (sECO != null && (_sPrevECO != null && _sPrevECO.equals(sECO) == false) || _sPrevECO == null) {
                if (sECO != null && sECO.trim().length() > 0) {
                    _sPrevECO = sECO;
                    return sECO;
                }
            }
        }
        return null;
    }

    private String getECOInfo(int level, final ArrayList<PGNEntry> _arrPGN, final JSONArray jArray) {
        if (level < _arrPGN.size()) {
            PGNEntry entry = _arrPGN.get(level);
            try {
                for (int i = 0; i < jArray.length(); i++) {
                    JSONObject jObj = (JSONObject) jArray.get(i);
                    if (jObj.get("m").equals(entry._sMove)) {

                        String sCurrent = jObj.getString("e") + ": " + jObj.getString("n") + (jObj.getString("v").length() > 0 ? ", " + jObj.getString("v") : "");
                        String sNext = null;

                        if (jObj.has("a")) {
                            sNext = getECOInfo(level + 1, _arrPGN, jObj.getJSONArray("a"));
                        }
                        if (sNext == null) {
                            return sCurrent;
                        }
                        return sNext;
                    }
                }
            } catch (Exception ex) {

            }
        }
        return null;
    }

}
