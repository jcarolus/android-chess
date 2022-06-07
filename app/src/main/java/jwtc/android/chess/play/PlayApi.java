package jwtc.android.chess.play;

import android.util.Log;

import jwtc.android.chess.controllers.GameApi;
import jwtc.android.chess.controllers.GameListener;
import jwtc.android.chess.engine.EngineListener;
import jwtc.android.chess.engine.LocalEngine;

public class PlayApi extends GameApi implements EngineListener {
    private static final String TAG = "PlayApi";
    public LocalEngine engine;

    public PlayApi() {
        super();

        engine = new LocalEngine();
        engine.setPly(3); // @TODO
        engine.addListener(this);
    }

    public boolean requestMove(int from, int to) {
        Log.i(TAG, "requestMove");
        if (jni.isEnded() != 0)
            return false;

        if (jni.requestMove(from, to) == 0) {
            return false;
        }

        final int move = jni.getMyMove();
        for (GameListener listener: listeners) {
            listener.OnMove(move);
        }

        engine.play();

        return true;
    }

    @Override
    public void OnStarted() {

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
