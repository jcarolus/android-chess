package jwtc.android.chess.hotspotboard;

import android.util.Log;
import jwtc.android.chess.services.DisplayedPosition;
import jwtc.android.chess.services.GameApi;
import jwtc.chess.Variant;
import jwtc.chess.board.BoardConstants;
import jwtc.chess.board.BoardPosition;

public class HotspotBoardApi extends GameApi {
    private static final String TAG = "HotspotBoardApi";
    protected String myName = "", opponentName = "";
    protected boolean isPlayingAsWhite = true;

    public HotspotBoardApi() {
        this(DisplayedPosition.current());
    }

    private HotspotBoardApi(DisplayedPosition.Snapshot position) {
        super(position.fen, position.variant);
    }

    public void restorePlayers(String name, String opponent, boolean white) {
        myName = name;
        opponentName = opponent;
        isPlayingAsWhite = white;
    }

    public void setMyName(String myName) {
        this.myName = myName;
    }

    public String getOpponentName() {
        return opponentName;
    }

    public String getMyName() {
        return this.myName;
    }

    public void onGameUpdate(GameMessage message) {
        if (myName.isEmpty()) {
            Log.d(TAG, "GameUpdate without myName");
        }

        if (message.white.equals(myName) || message.white.isEmpty()) {
            isPlayingAsWhite = true;
            opponentName = message.black;
        } else {
            isPlayingAsWhite = false;
            opponentName = message.white;
        }

        if (message.FEN.isEmpty()) {
            Log.d(TAG, "GameUpdate without FEN");
            return;
        }
        if (message.type == GameMessage.TYPE_SHARE_SNAPSHOT) {
            initFEN(message.FEN, true);
            return;
        }
        Variant variant = message.variant == null ? Variant.STANDARD : message.variant;
        if (message.type != GameMessage.TYPE_MOVE
                && (message.variant != null
                        || message.FEN.equals(getFEN()) && variant == rustVariant())) {
            return;
        }
        if (message.variant != null && message.lastMove != 0 && variant == rustVariant()) {
            // Replay complete turns when possible so both players retain repetition history.
            int duck = variant == Variant.DUCK ? new BoardPosition(message.FEN).duck() : -1;
            if (applyMove(message.lastMove, duck) && message.FEN.equals(getFEN())) {
                dispatchState();
                return;
            }
        }
        initFEN(message.FEN, true, variant);
    }

    public void restorePosition(String fen, Variant variant) {
        initFEN(fen, true, variant);
    }

    public boolean isMyTurn() {
        int turn = getTurn();
        return turn == BoardConstants.WHITE && isPlayingAsWhite
                || turn == BoardConstants.BLACK && !isPlayingAsWhite;
    }

    public boolean isPlayingAsWhite() {
        return isPlayingAsWhite;
    }

    public void setPlayingAsWhite(boolean asWhite) {
        isPlayingAsWhite = asWhite;
    }

    public String getWhite() {
        return isPlayingAsWhite ? myName : opponentName;
    }

    public String getBlack() {
        return isPlayingAsWhite ? opponentName : myName;
    }
}
