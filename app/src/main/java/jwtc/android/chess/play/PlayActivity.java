package jwtc.android.chess.play;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;

import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;

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

        ImageButton butPlay = findViewById(R.id.ButtonPlay);
        butPlay.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                ((PlayApi)gameApi).engine.play();
            }
        });
    }
}
