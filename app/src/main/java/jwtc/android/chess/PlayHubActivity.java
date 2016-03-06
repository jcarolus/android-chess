package jwtc.android.chess;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.widget.Toast;

import com.google.android.gms.analytics.HitBuilders;
import com.playhub.Consts;
import com.playhub.GameInfoFromPlayHub;
import java.io.UnsupportedEncodingException;

public class PlayHubActivity extends ChessActivity {

    /**
     * instances for the view and game of chess *
     */
    private PlayHubChessView _chessView;
    private GameInfoFromPlayHub gameInfoFromPlayHub;

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

        if (gameInfoFromPlayHub == null) {
            Toast.makeText(this, "Null received as data", Toast.LENGTH_LONG).show();
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

        _chessView = new PlayHubChessView(this);

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
        boolean isWhiteTurn = _chessView.getPGNEntries().size() % 2 == 0;
        boolean isViewerCurrentTurnPlayer = gameInfoFromPlayHub.currentTurnUserIndex == gameInfoFromPlayHub.viewingUserIndex;
        boolean canViewerPlay = isViewerCurrentTurnPlayer && !gameInfoFromPlayHub.isGameAlreadyFinished;

        _chessView.setCanViewerPlay(canViewerPlay);

        boolean isViewerWhite = isWhiteTurn == isViewerCurrentTurnPlayer;

        if (!isViewerWhite) {
            _chessView.flipBoard();
        }

        int whitePlayerIndex = isViewerWhite ? gameInfoFromPlayHub.viewingUserIndex : 1 - gameInfoFromPlayHub.viewingUserIndex;

        _chessView.setPGNHeadProperty("White", gameInfoFromPlayHub.users[whitePlayerIndex].nickname);
        _chessView.setPGNHeadProperty("Black", gameInfoFromPlayHub.users[1 - whitePlayerIndex].nickname);

        _chessView.updateState();
    }

    @Override
    protected void onDestroy() {
        if (_chessView != null) {
            _chessView.OnDestroy();
        }
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("numBoard", _chessView.getJNI().getNumBoard());
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        int numBoard = savedInstanceState.getInt("numBoard", -1);
        if (numBoard != -1) {
            _chessView.jumptoMove(numBoard);
            _chessView.updateEnablity();
        }
    }

    private void loadPGN(String sPGN) {
        if (sPGN != null) {
            if (!_chessView.loadPGN(sPGN)) {
                doToast(getString(jwtc.android.chess.R.string.err_load_pgn));
            }
            _chessView.updateState();
        }
    }

    public GameInfoFromPlayHub getGameInfoFromPlayHub() {
        return gameInfoFromPlayHub;
    }
}