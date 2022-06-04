package jwtc.android.chess.play;

import jwtc.android.chess.controllers.GameApi;

public class PlayController extends GameApi {
    @Override
    public boolean requestMove(int from, int to) {

        if (jni.requestMove(from, to) == 0) {
            return false;
        }

        return true;
    }
}
