package jwtc.android.chess.tools;

import android.content.ContentResolver;
import android.os.Handler;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;

import jwtc.android.chess.services.GameApi;
import jwtc.chess.JNI;

public class OpeningImportProcessor extends PGNProcessor {
    private static final String TAG = "OpeningImportProcessor";
    private ImportApi importApi;
    private ContentResolver contentResolver;
    private JNI jni;

    OpeningImportProcessor(int mode, Handler updateHandler, ImportApi gameApi) {
        super(mode, updateHandler);

        this.importApi = gameApi;
        this.successCount = 0;
        this.failCount = 0;

        jni = JNI.getInstance();
    }

    @Override
    public void processPGNFile(final InputStream is) {
        Log.d(TAG, "processPGNFile");

        new Thread(new Runnable() {
            public void run() {
                sendMessage(MSG_STARTED);
                try {

                    java.util.Scanner scanner = new java.util.Scanner(is).useDelimiter("\\A");
                    String jsonStr = scanner.hasNext() ? scanner.next() : "";
                    is.close();

                    JSONArray jArray = new JSONArray(jsonStr);
                    processJSONArray(jArray);

                    sendMessage(MSG_FINISHED);

                } catch (Exception e) {
                    sendMessage(MSG_FATAL_ERROR);

                    Log.e(TAG, e.toString());
                }
            }
        }).start();
    }

    @Override
    public boolean processPGN(String sPGN) {
        return true;
    }

    public boolean processPGN(String sPGN, String name) {
        if (importApi.loadPGN(sPGN)) {
            if (jni.getNumBoard() > 0) {
                long hash = jni.getHashKey();

                if (importApi.addToHashMap(hash, name)) {
                    // Log.d(TAG, "From pgn " + pgn + " :: " + name + " => " + hash);
                    return true;
                } else {
                    Log.d(TAG, "Duplicate hash");
                }
            }
        }
        return false;
    }

    public void processJSONArray(JSONArray jArray) {
        JNI jni = JNI.getInstance();

        importApi.resetHashMap();
        int numProcessed = 0;
        for (int i = 0; i < jArray.length(); i++) {
            try {
                JSONObject jObj = jArray.getJSONObject(i);
                String moves = jObj.getString("moves");
                String name = jObj.getString("name");

                StringBuilder PGN = new StringBuilder("");
                PGN.append("[Event \"Event\"]\n");
                PGN.append("[White \"white\"]\n");
                PGN.append("[Black \"black\"]\n");
                PGN.append(moves + "\n\n");

                String pgn = PGN.toString();

                if (processPGN(pgn, name)) {
                    successCount++;
                    sendMessage(MSG_PROCESSED_PGN);
                } else {
                    failCount++;
                    sendMessage(MSG_FAILED_PGN);
                }

            } catch (Exception ignored) {
            }
        }

        Log.d(TAG, "Done..." + numProcessed);
    }

    // old FEN method
    public void processJSONArrayFEN(JSONArray jArray) {
        JNI jni = JNI.getInstance();

        importApi.resetHashMap();
        int numProcessed = 0;
        for (int i = 0; i < jArray.length(); i++) {
            try {
                JSONObject jObj = jArray.getJSONObject(i);
                String fen = jObj.getString("fen");
                String name = jObj.getString("name");

                if (jni.initFEN(fen)) {
                    long hash = jni.getHashKey();

                    if (importApi.addToHashMap(hash, name)) {
                        Log.d(TAG, "From FEN " + fen + " :: " + name + " => " + hash);
                        numProcessed++;
                    } else {
                        Log.d(TAG, "Duplicate hash");
                    }
                }
            } catch (Exception ignored) {
            }
        }

        Log.d(TAG, "Done..." + numProcessed);
    }

}
