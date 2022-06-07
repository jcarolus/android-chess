package jwtc.android.chess.play;

import android.os.Bundle;

import jwtc.android.chess.R;
import jwtc.android.chess.ui.ChessBoardActivity;

public class PlayActivity extends ChessBoardActivity {

    @Override
    public boolean requestMove(int from, int to) {
        return gameApi.requestMove(from, to);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.play);

        gameApi = new PlayApi();
        gameApi.newGame();

        afterCreate();

    }
}
