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

    @Override
    public boolean requestMove(int from, int to) {
        Log.i(TAG, "requestMove");

        if (super.requestMove(from, to)) {
            engine.play();
            return true;
        }
        return false;
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
