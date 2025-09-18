package jwtc.android.chess.services;

import android.content.Context;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import jwtc.chess.JNI;
import jwtc.chess.PGNEntry;

/*
ECO.json

n = name
v = variant
m = move
a = array

Example:
[
  {
    "n": "Polish (Sokolsky) opening",
    "v": "",
    "e": "A00",
    "m": "b4",
    "a": [
      {
        "n": "Polish",
        "v": "Tuebingen Variation ",
        "e": "A00",
        "m": "Nh6",
        "a": []
      }
    ]
  }
]

 */
public class EcoService {
    private static final String TAG = "EcoService";
    private JSONArray _jArrayECO = null;
    private HMap hashMap;

    public void load(final AssetManager assetManager) {

        if (_jArrayECO == null) {

            (new Thread(new Runnable() {
                public void run() {
                    try {
                        Thread.sleep(1000);

                        long start = System.currentTimeMillis();

                        hashMap = HMap.read(assetManager, "hashmap.bin");
                        /*
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
                        */
                    } catch (Exception e) {
                        Log.d(TAG, "Could not read the opening database");
                    }
                }
            })).start();
        }
    }

    public String getEcoNameByHash(long hash) {
        if (hashMap != null) {
            return hashMap.get(hash);
        } else {
            Log.d(TAG, "getEcoNameByHash - no hash map ");
        }
        Log.d(TAG, "getEcoNameByHash - no hash for " + hash);
        return null;
    }

    public JSONObject getEco(final ArrayList<PGNEntry> _arrPGN, int maxLevel) {
        return getECOInfo(0, _arrPGN, _jArrayECO, maxLevel);
    }

    public String getMove(JSONObject jObj) {
        return getStringProperty(jObj, "m");
    }

    public String getName(JSONObject jObj) {
        String name = getStringProperty(jObj, "n");
        if (name.isEmpty()) {
            return getStringProperty(jObj,"v");
        }
        return name;
    }

    public JSONArray getArray(JSONObject jObj) {
        try {
            JSONArray jArray = jObj.getJSONArray("a");
            JSONArray jResult = new JSONArray();
            for (int i = 0; i < jArray.length(); i++) {
                JSONObject obj = jArray.getJSONObject(i);
                if (!getName(obj).isEmpty() && !getMove(jObj).isEmpty()) {
                    jResult.put(obj);
                }
            }
            return jResult;
        } catch (Exception ignored) {
            return null;
        }
    }

    public String getStringProperty(JSONObject jObj, String key) {
        try {
            return jObj.getString(key);
        } catch (Exception ignore) {
            return "";
        }
    }


    private JSONObject getECOInfo(int level, final ArrayList<PGNEntry> _arrPGN, final JSONArray jArray, int maxLevel) {
        if (level < _arrPGN.size() && level < maxLevel && jArray != null) {
            PGNEntry entry = _arrPGN.get(level);
            try {
                for (int i = 0; i < jArray.length(); i++) {
                    JSONObject jObj = (JSONObject) jArray.get(i);
                    if (getMove(jObj).equals(entry._sMove)) {
                        if (level + 1 < maxLevel) {
                            JSONObject jRet = getECOInfo(level + 1, _arrPGN, getArray(jObj), maxLevel);
                            if (jRet != null) {
                                String sMove = getMove(jRet);
                                String sName = getName(jRet);
                                if (sMove != null && !sMove.isEmpty() && !sName.isEmpty()) {
                                    return jRet;
                                } else {
                                    return null;
                                }
                            } else {
                                return null;
                            }
                        }
                        return jObj;
                    }
                }
            } catch (Exception ex) {}
        }
        return null;
    }

}
