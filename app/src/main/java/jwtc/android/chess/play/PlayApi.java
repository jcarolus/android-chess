package jwtc.android.chess.play;

import android.util.Log;

import jwtc.android.chess.controllers.GameApi;
import jwtc.android.chess.engine.EngineListener;
import jwtc.android.chess.engine.LocalEngine;

public class PlayApi extends GameApi implements EngineListener {
    private static final String TAG = "PlayApi";
    private LocalEngine engine;

    public PlayApi() {
        super();

        engine = new LocalEngine();
        engine.addListener(this);
    }

    public boolean requestMove(int from, int to) {
        Log.i(TAG, "requestMove");
        if (jni.isEnded() != 0)
            return false;

        if (jni.requestMove(from, to) == 0) {
            return false;
        }

        // onMove =>
        //addPGNEntry(jni.getNumBoard() - 1, jni.getMyMoveToString(), "", jni.getMyMove(), true);

        engine.play(1000, 0);

        return true;
    }

    @Override
    public void OnMove(int move) {
        move(move);
    }

    @Override
    public void OnInfo(String message) {

    }

    @Override
    public void OnAborted() {

    }

    @Override
    public void OnError() {

    }
}
