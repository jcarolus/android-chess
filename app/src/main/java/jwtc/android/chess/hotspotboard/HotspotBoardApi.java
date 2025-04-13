package jwtc.android.chess.hotspotboard;

import android.util.Log;

import jwtc.android.chess.services.GameApi;
import jwtc.chess.board.BoardConstants;

public class HotspotBoardApi extends GameApi {
    private static final String TAG = "HotspotBoardApi";
    protected String myName = "", opponentName = "";
    protected boolean isMyTurn = false;
    protected boolean isPlayingAsWhite = true;


    public void setMyName(String myName) {
        this.myName = myName;
    }


    public String getMyName() {
        return this.myName;
    }

    public void onGameUpdate(GameMessage message) {
        int turn = jni.getTurn();

        if (myName.equals("")) {
            Log.d(TAG, "GameUpdate without myName");
        }

        isMyTurn = turn == BoardConstants.WHITE && myName.equals(message.white) || turn == BoardConstants.BLACK && myName.equals(message.black);

        if (message.white.equals(myName)) {
            isPlayingAsWhite = true;
            opponentName = message.black;
        } else {
            opponentName = message.white;
            isPlayingAsWhite = false;
        }

        if (message.FEN.length() > 0) {
            initFEN(message.FEN, true);
        }
    }

    public boolean isMyTurn() {
        return isMyTurn;
    }

    public boolean isPlayingAsWhite() {
        return isPlayingAsWhite;
    }

    public void setPlayingAsWhite() {
        isPlayingAsWhite = true;
    }
}
