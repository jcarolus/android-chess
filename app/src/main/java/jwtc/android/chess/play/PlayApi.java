package jwtc.android.chess.play;

import android.util.Log;

import jwtc.android.chess.services.GameApi;
import jwtc.android.chess.engine.EngineListener;
import jwtc.android.chess.engine.LocalEngine;

public class PlayApi extends GameApi implements EngineListener {
    private static final String TAG = "PlayApi";
    public LocalEngine engine;

    public PlayApi() {
        super();

        engine = new LocalEngine();
        //engine.setPly(3); // @TODO
        engine.setMsecs(3000);
        engine.addListener(this);
    }

    @Override
    public String getBlackPlayer() {
        return null;
    }

    @Override
    public String getWhitePlayer() {
        return null;
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
    public void OnEngineStarted() {

    }

    @Override
    public void OnEngineMove(int move) {
        move(move);
    }

    @Override
    public void OnInfo(String message) {

    }

    @Override
    public void OnEngineAborted() {

    }

    @Override
    public void OnEngineError() {

    }
}
