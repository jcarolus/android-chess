package jwtc.android.chess.play;

import android.os.Bundle;

import jwtc.android.chess.ui.ChessBoardActivity;

public class PlayActivity extends ChessBoardActivity {


    @Override
    public void handleClick(int index) {
        setSelectedPosition(index);
        paintBoard();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
}
