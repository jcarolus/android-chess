package jwtc.android.chess;

import android.os.Bundle;
import android.widget.Toast;

import com.playhub.Consts;
import com.playhub.GameInfoFromPlayHub;
import java.io.UnsupportedEncodingException;

import jwtc.chess.GameControl;

public class PlayHub extends ChessActivity {

    /**
     * instances for the view and game of chess *
     */
    private ChessView _chessView;
    public GameInfoFromPlayHub gameInfoFromPlayHub;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();
        try{
            if ((extras != null) && (extras.containsKey(Consts.GAME_INFO_FROM_PLAYHUB_KEY))){
                gameInfoFromPlayHub = (GameInfoFromPlayHub) extras.getSerializable(Consts.GAME_INFO_FROM_PLAYHUB_KEY);
            } else {
                Toast.makeText(this, "No data was received", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
        }catch(Exception e){
            Toast.makeText(this, "An exception was thrown while reading the extras", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (gameInfoFromPlayHub.users.length < 2) {
            Toast.makeText(this, "Not enough players", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setContentView(R.layout.playhub);

        this.makeActionOverflowMenuShown();

        _chessView = new ChessView(this);
        _chessView.setAutoFlip(false);

        if (gameInfoFromPlayHub.innerGameState != null) {
            try {
                loadPGN(new String(gameInfoFromPlayHub.innerGameState, "US-ASCII"));
            } catch (UnsupportedEncodingException e) {
                Toast.makeText(this, "Error in parsing inner data", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
        } else {
            loadPGN("");
        }
        _chessView.getPGNEntries();
        boolean isWhiteTurn = _chessView.getPGNEntries().size() % 2 == 0;
        boolean isViewerCurrentTurnPlayer = gameInfoFromPlayHub.currentTurnUserIndex == gameInfoFromPlayHub.viewingUserIndex;
        if (gameInfoFromPlayHub.isGameAlreadyFinished || !isViewerCurrentTurnPlayer) {
            _chessView.disableControl();
            _chessView.isPlayhubViewerCurrentTurnPlayer = false;
        }

        boolean isViewerWhite = isWhiteTurn == isViewerCurrentTurnPlayer;

        if (!isViewerWhite) {
            _chessView.flipBoard();
        }

        int whitePlayerIndex = isViewerWhite ? gameInfoFromPlayHub.viewingUserIndex : 1 - gameInfoFromPlayHub.viewingUserIndex;

        _chessView.setPGNHeadProperty("White", gameInfoFromPlayHub.users[whitePlayerIndex].nickname);
        _chessView.setPGNHeadProperty("Black", gameInfoFromPlayHub.users[1 - whitePlayerIndex].nickname);

        _chessView.setShowMoves(true);
        _chessView.setPlayMode(GameControl.HUMAN_HUMAN);

        _chessView.updateState();
    }

    @Override
    protected void onDestroy() {
        if (_chessView != null) {
            _chessView.OnDestroy();
        }
        super.onDestroy();
    }

    private void loadPGN(String sPGN) {
        if (sPGN != null) {
            if (false == _chessView.loadPGN(sPGN)) {
                doToast(getString(jwtc.android.chess.R.string.err_load_pgn));
            }
            _chessView.updateState();
        }
    }
}