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
import jwtc.chess.Move;
import jwtc.chess.PGNEntry;


public class EcoService {
    private static final String TAG = "EcoService";
    private HMap hashMap = null;
    JNI jni;

    public EcoService() {
         jni = JNI.getInstance();
    }
    public void load(final AssetManager assetManager) {
        if (hashMap == null) {
            (new Thread(new Runnable() {
                public void run() {
                    try {
                        Thread.sleep(1000);
                        long start = System.currentTimeMillis();

                        hashMap = HMap.read(assetManager, "hashmap.bin");
                        Log.i(TAG, "ECO size: " + hashMap.getSize() + " load ms:" + (System.currentTimeMillis() - start));
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


    public JSONArray getAvailable() {
        int size = jni.getMoveArraySize();
        ArrayList<Integer> moveToPositions = new ArrayList<Integer>();
        JSONArray jRet = new JSONArray();
        int move;
        for (int i = 0; i < size; i++) {
            move = jni.getMoveArrayAt(i);

            moveToPositions.add(move);
        }

        for (Integer m : moveToPositions) {
            if (jni.move(m) != 0) {
                Log.d(TAG, "testing move " + m);
                long hash = jni.getHashKey();
                String sEco = hashMap.get(hash);
                if (sEco != null) {
                    String sMove = jni.getMyMoveToString();

                    Log.d(TAG, "Got move " + sMove);
                    try {
                        JSONObject jObj = new JSONObject();
                        jObj.put("name", sEco);
                        jObj.put("move", sMove);

                        jRet.put(jObj);
                    } catch (Exception ignored) {}
                }

                jni.undo();
            }
        }

        return jRet;
    }

}
