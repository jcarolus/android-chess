package jwtc.android.chess.play;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;

import jwtc.android.chess.HtmlActivity;
import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.activities.GamePreferenceActivity;
import jwtc.android.chess.activities.GlobalPreferencesActivity;


public class PlayActivity extends ChessBoardActivity implements SeekBar.OnSeekBarChangeListener {
    private static final String TAG = "PlayActivity";

    private SeekBar seekBar;

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


        // findViewById(R.id.TextViewClockTimeOpp)

        seekBar = findViewById(R.id.SeekBarMain);
        seekBar.setOnSeekBarChangeListener(this);
        seekBar.setMax(1);

        ImageButton butNext = findViewById(R.id.ButtonNext);
        butNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gameApi.nextMove();
            }
        });

        ImageButton butPrev = findViewById(R.id.ButtonPrevious);
        butPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gameApi.undoMove();
            }
        });
    }

    @Override
    public void OnMove(int move) {
        super.OnMove(move);

        updateSeekBar();
    }

    @Override
    public void OnState() {
        super.OnState();

        updateSeekBar();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.play_topmenu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        Intent intent;
        String s;
        switch (item.getItemId()) {
            case R.id.action_game_settings:
                intent = new Intent();
                intent.setClass(PlayActivity.this, GamePreferenceActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_new:

                return true;
            case R.id.action_save:

                return true;
            case R.id.action_open:

                return true;
            case R.id.action_prefs:
                intent = new Intent();
                intent.setClass(PlayActivity.this, GlobalPreferencesActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_flip:

                return true;
            case R.id.action_options:

                return true;
            case R.id.action_email:

                return true;
            case R.id.action_clock:

                return true;
            case R.id.action_clip_pgn:

                return true;
            case R.id.action_fromclip:

                return true;
            case R.id.action_help:
                intent = new Intent();
                intent.setClass(PlayActivity.this, HtmlActivity.class);
                intent.putExtra(HtmlActivity.HELP_MODE, "help_play");
                startActivity(intent);
                return true;
            case R.id.action_from_qrcode:

                return true;
            case R.id.action_to_qrcode:
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {

            if (jni.getNumBoard() - 1 > progress)
                progress++;

            this.gameApi.jumptoMove(progress);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        // TODO Auto-generated method stub
    }

    protected void updateSeekBar() {
        seekBar.setMax(this.gameApi.getPGNSize());
        seekBar.setProgress(jni.getNumBoard() - 1);
//        seekBar.invalidate();
        Log.d(TAG, "updateSeekBar " + seekBar.getMax() + " - " + seekBar.getProgress());
    }
}
