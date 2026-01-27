package jwtc.android.chess.setup;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.views.ChessSquareView;
import jwtc.chess.JNI;


public class SetupRandomFischerActivity extends ChessBoardActivity {
    private static final String TAG = "SetupRandomFischer";
    private SeekBar seekBar;
    private TextView textViewSeed;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.random_fischer);

        ActivityHelper.fixPaddings(this, findViewById(R.id.LayoutMain));

        gameApi = new SetupApi();

        afterCreate();

        textViewSeed = findViewById(R.id.TextViewSeed);
        seekBar = findViewById(R.id.seekbar);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                onUpdateProgress(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        MaterialButton butNext = findViewById(R.id.ButtonNext);
        butNext.setOnClickListener(v -> {
            int progress = seekBar.getProgress();
            if (progress < 959) {
                seekBar.setProgress(progress + 1);
            }
        });

        MaterialButton butPrev = findViewById(R.id.ButtonPrevious);
        butPrev.setOnClickListener(v -> {
            int progress = seekBar.getProgress();
            if (progress > 0) {
                seekBar.setProgress(progress - 1);
            }
        });

        MaterialButton buttonOk = findViewById(R.id.ButtonSetupOptionsOk);
        buttonOk.setOnClickListener(v -> commitFEN());

        MaterialButton buttonCancel = findViewById(R.id.ButtonSetupOptionsCancel);
        buttonCancel.setOnClickListener(v -> finish());

        MaterialButton buttonRandom = findViewById(R.id.ButtonSetupOptionsRandom);
        buttonRandom.setOnClickListener(v -> {
            int seed = (int) (Math.random() * 960);
            seekBar.setProgress(seed);
            commitFEN();
        });
    }

    @Override
    protected void onResume() {
        SharedPreferences prefs = getPrefs();

        final int seed = prefs.getInt("randomFischerSeed", 0);

        if (seekBar.getProgress() == seed) {
            onUpdateProgress(seed);
        } else {
            seekBar.setProgress(seed);
        }
        super.onResume();
    }

    @Override
    public void afterCreate() {
        Log.d(TAG, " afterCreate");

        jni = JNI.getInstance();
        chessBoardView = findViewById(R.id.includeboard);
        chessBoardView.setFocusable(false);

        for (int i = 0; i < 64; i++) {
            ChessSquareView csv = new ChessSquareView(this, i);
            chessBoardView.addView(csv);
        }

        gameApi.addListener(this);
    }

    @Override
    public boolean requestMove(int from, int to) {
        return false;
    }

    protected void commitFEN() {
        Log.d(TAG, "commitFEN");

        SharedPreferences.Editor editor = this.getPrefs().edit();
        editor.putString("FEN", jni.toFEN());
        editor.putString("game_pgn", null);
        editor.putInt("boardNum", 0);
        editor.putLong("game_id", 0);

        editor.putInt("randomFischerSeed", seekBar.getProgress());

        editor.commit();

        finish();
    }

    protected void onUpdateProgress(int progress) {
        gameApi.newGameRandomFischer(progress);
        textViewSeed.setText("" + (progress + 1));
    }
}
