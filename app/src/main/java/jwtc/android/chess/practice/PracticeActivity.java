package jwtc.android.chess.practice;

import static jwtc.android.chess.helpers.ActivityHelper.pulseAnimation;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.Timer;

import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.engine.EngineApi;
import jwtc.android.chess.engine.EngineListener;
import jwtc.android.chess.engine.LocalEngine;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.puzzle.MyPuzzleProvider;
import jwtc.android.chess.tools.ImportActivity;
import jwtc.android.chess.tools.ImportService;
import jwtc.chess.Move;
import jwtc.chess.board.BoardConstants;

public class PracticeActivity extends ChessBoardActivity implements EngineListener {
    private static final String TAG = "PracticeActivity";
    private EngineApi myEngine;
    private TextView tvPracticeMove, tvPercentage, textViewSolution;
    private ImageButton buttonNext, buttonRetry;
    private int totalPuzzles, currentPos;
    private Cursor cursor;
    private Timer timer;

    private ImageView imageTurn;
    private ImageView imgStatus;

    private int myTurn, numMoved, numPlayed, numSolved;

    private LinearProgressIndicator percentBar;

    @Override
    public boolean requestMove(final int from, final int to) {
        if (jni.isEnded() != 0) {
            setMessage("Finished position");
            rebuildBoard();
            return false;
        }

        if (jni.getTurn() != myTurn) {
            rebuildBoard();
            return false;
        }

        if (super.requestMove(from, to)) {
            return true;
        }
        rebuildBoard();
        return false;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.practice);

        ActivityHelper.fixPaddings(this, findViewById(R.id.root_layout));

        gameApi = new PracticeApi();

        afterCreate();

        tvPracticeMove = (TextView) findViewById(R.id.TextViewPracticeMove);
        tvPercentage = findViewById(R.id.TextViewPercentage);
        textViewSolution = findViewById(R.id.TextViewSolution);
        imageTurn = (ImageView) findViewById(R.id.ImageTurn);
        imgStatus = (ImageView) findViewById(R.id.ImageStatus);
        buttonNext = (ImageButton) findViewById(R.id.ButtonPracticeNext);

        buttonNext.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                if (currentPos + 1 < totalPuzzles) {
                    currentPos++;
                    buttonRetry.setEnabled(false);
                    startPuzzle();
                } else {
                    // completed
                    setMessage("You completed all puzzles!!!");
                }
            }
        });

        buttonRetry = findViewById(R.id.ButtonPracticeRetry);
        buttonRetry.setOnClickListener(new View.OnClickListener() {
              public void onClick(View arg0) {
                  buttonRetry.setEnabled(false);
                  startPuzzle();
              }
          });
        buttonRetry.setEnabled(false);

        percentBar = findViewById(R.id.percentBar);

        chessBoardView.setNextFocusRightId(R.id.ButtonPracticeNext);
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.i(TAG, "onResume");

        myEngine = new LocalEngine();
        myEngine.setQuiescentSearchOn(false);
        myEngine.addListener(this);

        loadPuzzles();
    }

    @Override
    protected void onPause() {
        super.onPause();

        SharedPreferences.Editor editor = this.getPrefs().edit();
        if (timer != null)
            timer.cancel();
        timer = null;

        // ended with correct solution, advance position
        if (jni.isEnded() != 0) {
            currentPos++;
        }

        editor.putInt("practicePos", currentPos);
        editor.putInt("practiceNumPlayed", numPlayed);
        editor.putInt("practiceSolved", numSolved);

        editor.commit();
    }

    protected void loadPuzzles() {
        SharedPreferences prefs = getPrefs();

        currentPos = prefs.getInt("practicePos", 0);
        numPlayed = prefs.getInt("practiceNumPlayed", 0);
        numSolved = prefs.getInt("practiceSolved", 0);
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

        int pgnIndex = cursor.getColumnIndex(MyPuzzleProvider.COL_PGN);
        String sPGN = pgnIndex >= 0 ? cursor.getString(pgnIndex) : "";

        Log.i(TAG, "Start puzzle init: " + sPGN);

        lastMoveFrom = -1;
        lastMoveTo = -1;
        gameApi.loadPGN(sPGN);

        gameApi.jumpToBoardNum(0);
        myTurn = jni.getTurn();
        numMoved = 0;

        updateScore();

        Log.i(TAG, " turn " + jni.getTurn() + ": " + numPlayed + ", " + numSolved);

        chessBoardView.setRotated(myTurn == BoardConstants.BLACK);

        tvPracticeMove.setText("# " + (currentPos + 1));
        textViewSolution.setText("");

        imageTurn.setImageResource(myTurn == BoardConstants.BLACK ? R.drawable.turnblack : R.drawable.turnwhite);

        imgStatus.setImageResource(R.drawable.ic_check_none);
    }

    public void setMessage(String sMsg) {
        textViewSolution.setText(sMsg);
    }

    public void setMessage(int res) {
        tvPracticeMove.setText(res);
    }

    protected void startEngine() {
        myEngine.setPly(4 - numMoved);
        myEngine.play();
    }

    public void animateCorrect() {
        setMessage(getString(R.string.puzzle_correct_move));
        imgStatus.setImageResource(R.drawable.ic_check);
        pulseAnimation(imgStatus);
        updateScore();
    }

    public void animateWrong(String message) {
        setMessage(message);
        imgStatus.setImageResource(R.drawable.ic_exclamation_triangle);
        pulseAnimation(imgStatus);
        updateScore();
    }

    public void updateScore() {
        tvPercentage.setText(formatPercentage());
        int percentage = numPlayed > 0 ? (int)((float)numSolved / numPlayed * 100) : 0;
        Log.d(TAG, "Set per " + percentage);
        percentBar.setProgressCompat(percentage, /*animated=*/true);
    }

    private String formatPercentage() {
        return String.format("%d / %d = %.1f %%", numSolved, numPlayed, numPlayed > 0 ? ((float)numSolved / numPlayed) * 100 : 0.0f);
    }

    @Override
    public void OnMove(int move) {
        super.OnMove(move);

        lastMoveFrom = Move.getFrom(move);
        lastMoveTo = Move.getTo(move);

        numMoved++;

        updateSelectedSquares();

        if (jni.isEnded() == 0 && jni.getTurn() != myTurn) {
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    startEngine();
                }
            }, 1000);
        }
        if (jni.isEnded() != 0) {
            Log.d(TAG, "Solved ");

            numPlayed++;
            numSolved++;
            buttonRetry.setEnabled(false);
            animateCorrect();
        }
    }

    @Override
    public void OnEngineMove(int move, int duckMove, int value) {
        Log.d(TAG, "OnEngineMove " + value);
        boolean isMyTurn = myTurn == jni.getTurn();
        if (value == BoardConstants.VALUATION_MATE * (isMyTurn ? 1 : -1)) {
            gameApi.move(move, duckMove);
            animateCorrect();
        } else {
            int moveIndex = gameApi.getPGNSize() - 1;
            String sMove = "";
            if (moveIndex >= 0) {
                sMove = gameApi.getPGNEntries().get(moveIndex)._sMove + " ";
            }

            buttonRetry.setEnabled(true);
            numMoved--;
            numPlayed++;

            animateWrong(sMove + getString(R.string.puzzle_not_correct_move));
        }
    }
    @Override
    public void OnEngineInfo(String message) {}
    @Override
    public void OnEngineStarted() {}
    @Override
    public void OnEngineAborted() {}
    @Override
    public void OnEngineError() {}
}
