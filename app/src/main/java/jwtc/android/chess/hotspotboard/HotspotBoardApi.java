package jwtc.android.chess.hotspotboard;

import android.util.Log;

import jwtc.android.chess.services.GameApi;
import jwtc.chess.board.BoardConstants;

public class HotspotBoardApi extends GameApi {
    private static final String TAG = "HotspotBoardApi";
    protected String myName = "", opponentName = "";
    protected boolean isPlayingAsWhite = true;


    public void setMyName(String myName) {
        this.myName = myName;
    }

    public String getOpponentName() {
        return opponentName;
    }

    public void setOpponentName(String opponentName) {
        this.opponentName = opponentName;
    }

    public String getMyName() {
        return this.myName;
    }

    public void onGameUpdate(GameMessage message) {
        if (myName.equals("")) {
            Log.d(TAG, "GameUpdate without myName");
        }

        if (message.playerName != null && message.playerName.length() > 0) {
            opponentName = message.playerName;
        }

        if (message.FEN.length() > 0) {
            initFEN(message.FEN, true);
        }
    }

    public boolean isMyTurn() {
        int turn = jni.getTurn();
        return turn == BoardConstants.WHITE && isPlayingAsWhite || turn == BoardConstants.BLACK && !isPlayingAsWhite;
    }

    public boolean isPlayingAsWhite() {
        return isPlayingAsWhite;
    }

    public void setPlayingAsWhite(boolean asWhite) {
        isPlayingAsWhite = asWhite;
    }
}
