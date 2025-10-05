package jwtc.android.chess.setup;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.helpers.ActivityHelper;

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
                gameApi.newGameRandomFischer(progress);
                textViewSeed.setText("" + (progress + 1));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Called when the user starts touching the SeekBar
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Called when the user stops touching the SeekBar
            }
        });

        ImageButton butNext = findViewById(R.id.ButtonNext);
        butNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int progress = seekBar.getProgress();
                if (progress < 959) {
                    seekBar.setProgress(progress + 1);
                }
            }
        });

        ImageButton butPrev = findViewById(R.id.ButtonPrevious);
        butPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int progress = seekBar.getProgress();
                if (progress > 0) {
                    seekBar.setProgress(progress - 1);
                }
            }
        });

        Button buttonOk = findViewById(R.id.ButtonSetupOptionsOk);
        buttonOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                commitFEN();
            }
        });

        Button buttonCancel = findViewById(R.id.ButtonSetupOptionsCancel);
        buttonCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        Button buttonRandom = findViewById(R.id.ButtonSetupOptionsRandom);
        buttonRandom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int seed = (int) (Math.random() * 960);
                seekBar.setProgress(seed);
                commitFEN();
            }
        });
    }

    @Override
    protected void onResume() {
        SharedPreferences prefs = getPrefs();

        final int seed = prefs.getInt("randomFischerSeed", 0);
        seekBar.setProgress(seed);

        super.onResume();
    }

    @Override
    public boolean requestMove(int from, int to) {
        return false;
    }

    protected void commitFEN() {
        Log.d(TAG, "commitFEN");

        SharedPreferences.Editor editor = this.getPrefs().edit();
        editor.putString("FEN", jni.toFEN());
        editor.putString("game_pgn", "");
        editor.putInt("boardNum", 0);
        editor.putLong("game_id", 0);

        editor.putInt("randomFischerSeed", seekBar.getProgress());

        editor.commit();

        finish();
    }
}
