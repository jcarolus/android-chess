package jwtc.android.chess.practice;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import java.util.Timer;
import java.util.TimerTask;

import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.constants.ColorSchemes;
import jwtc.android.chess.puzzle.MyPuzzleProvider;
import jwtc.android.chess.tools.ImportActivity;
import jwtc.android.chess.tools.ImportListener;
import jwtc.android.chess.tools.ImportService;
import jwtc.chess.Move;
import jwtc.chess.board.BoardConstants;

public class PracticeActivity extends ChessBoardActivity {
    private static final String TAG = "PracticeActivity";

    private TextView tvPracticeMove, tvPracticeTime, tvPracticeAvgTime;
    private Button buttonShow;
    private ImageButton buttonNext;
    private int totalPuzzles, currentPos;
    private Cursor cursor;
    private Timer timer;
    private int ticks, playTicks;
    private ViewSwitcher switchTurn;
    private ImageView imgStatus;
    private boolean isPlaying;
    private TableLayout layoutTurn;

    protected Handler m_timerHandler = new Handler() {
        /** Gets called on every message that is received */
        // @Override
        public void handleMessage(Message msg) {
            tvPracticeTime.setText(formatTime(msg.getData().getInt("ticks")));
        }

    };



    @Override
    public boolean requestMove(final int from, final int to) {
        if (gameApi.getPGNSize() <= jni.getNumBoard() - 1) {
            setMessage("Finished position");
            rebuildBoard();
            return false;
        }
        int move = gameApi.getPGNEntries().get(jni.getNumBoard() - 1)._move;
        int theMove = Move.makeMove(from, to);

        if (Move.equalPositions(move, theMove)) {
            gameApi.jumptoMove(jni.getNumBoard());

            if (gameApi.getPGNSize() == jni.getNumBoard() - 1) {
                //play();
                imgStatus.setImageResource(R.drawable.ic_check);
                setMessage("Correct!");
                isPlaying = false;
                buttonNext.setEnabled(true);
            } else {
                gameApi.jumptoMove(jni.getNumBoard());
            }

            return true;
        } else {
            imgStatus.setImageResource(R.drawable.ic_exclamation_triangle);
            setMessage(Move.toDbgString(theMove) + (gameApi.isLegalMove(from, to) ? " is not expected" : " is an illegal move"));
            rebuildBoard();
        }
        return false;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.practice);

        gameApi = new PracticeApi();

        afterCreate();

        layoutTurn = findViewById(R.id.LayoutTurn);
        isPlaying = false;

        tvPracticeMove = (TextView) findViewById(R.id.TextViewPracticeMove);
        tvPracticeTime = (TextView) findViewById(R.id.TextViewPracticeTime);
        tvPracticeAvgTime = (TextView) findViewById(R.id.TextViewPracticeAvgTime);

        switchTurn = (ViewSwitcher) findViewById(R.id.ImageTurn);

        imgStatus = (ImageView) findViewById(R.id.ImageStatus);

        buttonShow = (Button) findViewById(R.id.ButtonPracticeShow);
        buttonNext = (ImageButton) findViewById(R.id.ButtonPracticeNext);

        buttonShow.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {

                gameApi.jumptoMove(jni.getNumBoard());

                if (gameApi.getPGNSize() == jni.getNumBoard() - 1) {
                    buttonNext.setEnabled(true);
                }
            }
        });

        buttonNext.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                buttonNext.setEnabled(false);
                if (currentPos + 1 < totalPuzzles) {
                    currentPos++;
                    startPuzzle();
                } else {
                    // completed
                    setMessage("You completed all puzzles!!!");
                }
            }
        });

        chessBoardView.setNextFocusRightId(R.id.ButtonPracticeShow);
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.i(TAG, "onResume");

        layoutTurn.setBackgroundColor(ColorSchemes.getDark());
        tvPracticeTime.setTextColor(ColorSchemes.getHightlightColor());
        tvPracticeMove.setTextColor(ColorSchemes.getHightlightColor());

        isPlaying = false;
        scheduleTimer();

        loadPuzzles();
    }

    @Override
    protected void onPause() {
        super.onPause();

        SharedPreferences.Editor editor = this.getPrefs().edit();
        if (timer != null)
            timer.cancel();
        timer = null;
        isPlaying = false;

        editor.putInt("practicePos", currentPos);
        editor.putInt("practiceTicks", ticks);
        editor.commit();
    }


    protected void loadPuzzles() {
        SharedPreferences prefs = getPrefs();

        ticks = prefs.getInt("practiceTicks", 0);
        playTicks = 0;

        currentPos = prefs.getInt("practicePos", 0);

        cursor = managedQuery(MyPuzzleProvider.CONTENT_URI_PRACTICES, MyPuzzleProvider.COLUMNS, null, null, "");

        if (cursor != null) {
            totalPuzzles = cursor.getCount();

            if (totalPuzzles > 0) {
                if (currentPos + 1 >= totalPuzzles) {
                    currentPos = 0;
                }
                startPuzzle();
            } else {
                Intent intent = new Intent();
                intent.setClass(PracticeActivity.this, ImportActivity.class);
                intent.putExtra("mode", ImportService.IMPORT_PRACTICE);
                startActivityForResult(intent, ImportService.IMPORT_PRACTICE);
            }
        }
    }

    protected void startPuzzle() {
        cursor.moveToPosition(currentPos);
        isPlaying = true;
        playTicks = 0;

        String sPGN = cursor.getString(cursor.getColumnIndex(MyPuzzleProvider.COL_PGN));

        Log.i(TAG, "init: " + sPGN);

        gameApi.loadPGN(sPGN);

        gameApi.jumptoMove(0);

        Log.i(TAG, gameApi.getPGNSize() + " moves from " + sPGN + " turn " + jni.getTurn());

        final int turn = jni.getTurn();
        chessBoardView.setRotated(turn == BoardConstants.BLACK);

        tvPracticeMove.setText("# " + (currentPos + 1));

        if (turn == BoardConstants.BLACK) {
            switchTurn.setDisplayedChild(0);
        } else {
            switchTurn.setDisplayedChild(1);
        }

        imgStatus.setImageResource(R.drawable.ic_check_none);

        Float f = (float) (ticks) / (currentPos + 1);
        tvPracticeAvgTime.setText(String.format("%.1f", f));
    }

    public void setMessage(String sMsg) {
        tvPracticeMove.setText(sMsg);
    }

    public void setMessage(int res) {
        tvPracticeMove.setText(res);
    }

    protected void scheduleTimer() {
        timer = new Timer(true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {

                if (false == isPlaying) {
                    return;
                }
                ticks++;
                playTicks++;

                Message msg = new Message();
                msg.what = 1;
                Bundle bun = new Bundle();
                bun.putInt("ticks", playTicks);
                msg.setData(bun);
                m_timerHandler.sendMessage(msg);

            }
        }, 1000, 1000);
    }

    private String formatTime(int sec) {
        return String.format("%d:%02d", (int) (Math.floor(sec / 60)), sec % 60);
    }
}
