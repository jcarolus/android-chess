package jwtc.android.chess.controllers;

public class PlayApi extends GameApi {


    public boolean requestMove(int from, int to) {
        //Log.i("requestMove debug", m_game.getBoard().getPGNMoves(new ChessBoard()));
        if (jni.isEnded() != 0)
            return false;

        if (jni.requestMove(from, to) == 0) {
            return false;
        }

        addPGNEntry(jni.getNumBoard() - 1, jni.getMyMoveToString(), "", jni.getMyMove(), true);

        return true;
    }

}
