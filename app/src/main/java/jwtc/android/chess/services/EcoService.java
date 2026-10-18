package jwtc.android.chess.services;

import android.content.res.AssetManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;


public class EcoService {
    private static final String TAG = "EcoService";
    private HMap hashMap = null;
    private final GameApi gameApi;

    public EcoService(GameApi gameApi) {
        this.gameApi = gameApi;
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
        Log.d(TAG, "getEcoNameByHash - no hashMap for " + hash);
        return null;
    }


    public ArrayList<Integer> getAvailableMoves() {
        ArrayList<Integer> moveToPositions = getAllMoves();
        ArrayList<Integer> retPositions = new ArrayList<Integer>();
        if (hashMap != null) {
            gameApi.scratchSyncFromCurrent();
            for (Integer m : moveToPositions) {
                if (gameApi.scratchMove(m) != 0) {
                    long hash = gameApi.scratchGetHashKey();
                    String sEco = hashMap.get(hash);
                    if (sEco != null) {
                        retPositions.add(m);
                    }

                    gameApi.scratchUndo();
                }
            }
        }
        return retPositions;
    }

    public JSONArray getAvailable() {
        ArrayList<Integer> moveToPositions = getAllMoves();
        JSONArray jRet = new JSONArray();

        if (hashMap != null) {
            gameApi.scratchSyncFromCurrent();
            for (Integer m : moveToPositions) {
                if (gameApi.scratchMove(m) != 0) {
                    long hash = gameApi.scratchGetHashKey();
                    String sEco = hashMap.get(hash);
                    if (sEco != null) {
                        String sMove = gameApi.scratchGetMyMoveToString();
                        try {
                            JSONObject jObj = new JSONObject();
                            jObj.put("name", sEco);
                            jObj.put("move", sMove);

                            jRet.put(jObj);
                        } catch (Exception ignored) {
                        }
                    }

                    gameApi.scratchUndo();
                }
            }
        }

        return jRet;
    }

    private ArrayList<Integer> getAllMoves() {
        int size = gameApi.getMoveArraySize();
        ArrayList<Integer> moveToPositions = new ArrayList<Integer>();
        int move;
        for (int i = 0; i < size; i++) {
            move = gameApi.getMoveArrayAt(i);

            moveToPositions.add(move);
        }
        return moveToPositions;
    }
}
