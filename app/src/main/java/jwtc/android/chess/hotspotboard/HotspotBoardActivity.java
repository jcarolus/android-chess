package jwtc.android.chess.hotspotboard;

import android.os.Bundle;

import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;

public class HotspotBoardActivity extends ChessBoardActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        gameApi = new HotspotBoardApi();
        setContentView(R.layout.hotspotboard);

        afterCreate();
    }
}
