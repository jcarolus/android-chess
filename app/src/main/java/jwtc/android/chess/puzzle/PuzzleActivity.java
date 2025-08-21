package jwtc.android.chess.puzzle;

import static jwtc.android.chess.helpers.ActivityHelper.pulseAnimation;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.constants.ColorSchemes;
import jwtc.android.chess.engine.EngineApi;
import jwtc.android.chess.engine.EngineListener;
import jwtc.android.chess.engine.LocalEngine;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.tools.ImportActivity;
import jwtc.android.chess.tools.ImportService;
import jwtc.chess.Move;
import jwtc.chess.board.BoardConstants;

public class PuzzleActivity extends ChessBoardActivity implements SeekBar.OnSeekBarChangeListener, EngineListener {
    private static final String TAG = "PuzzleActivity";
    private EngineApi myEngine;
    private Cursor cursor = null;
    private SeekBar seekBar;
    private TextView tvPuzzleText;
    private ViewSwitcher switchTurn;
    private ImageButton butPrev, butNext, butRetry;
    private ImageView imgStatus;
    private int currentPosition, totalPuzzles, myTurn, numMoved = 0;
    private TableLayout layoutTurn;

    @Override
    public boolean requestMove(int from, int to) {
        if (jni.isEnded() != 0) {
            setMessage(getString(R.string.puzzle_already_solved));
            rebuildBoard();
            return false;
        }

        if (jni.getTurn() != myTurn) {
            rebuildBoard();
            return false;
        }

        if (super.requestMove(from, to)) {
            numMoved++;
            return true;
        }
        rebuildBoard();
        return false;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.puzzle);

        ActivityHelper.fixPaddings(this, findViewById(R.id.LayoutMain));

        gameApi = new PuzzleApi();

        afterCreate();

        layoutTurn = findViewById(R.id.LayoutTurn);

        currentPosition = 0;
        totalPuzzles = 0;
        seekBar = findViewById(R.id.SeekBarPuzzle);
        seekBar.setOnSeekBarChangeListener(this);

        tvPuzzleText = findViewById(R.id.TextViewPuzzleText);
        switchTurn = findViewById(R.id.ImageTurn);

        imgStatus = findViewById(R.id.ImageStatus);

        butPrev = (ImageButton) findViewById(R.id.ButtonPuzzlePrevious);
        butPrev.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                if (currentPosition > 0) {
                    currentPosition--;
                }
                startPuzzle();
            }
        });

        butNext = (ImageButton) findViewById(R.id.ButtonPuzzleNext);
        butNext.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                if (currentPosition + 1 < totalPuzzles) {
                    currentPosition++;
                    startPuzzle();
                }
            }
        });

        butRetry = findViewById(R.id.ButtonPuzzleRetry);
        butRetry.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                startPuzzle();
            }
        });

        chessBoardView.setNextFocusRightId(R.id.ButtonPuzzlePrevious);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");

        myEngine = new LocalEngine();
        myEngine.setPly(3);
        myEngine.addListener(this);

        layoutTurn.setBackgroundColor(ColorSchemes.getDark());
        tvPuzzleText.setTextColor(ColorSchemes.getHightlightColor());

        loadPuzzles();
    }

    @Override
    protected void onPause() {
        super.onPause();

        SharedPreferences.Editor editor = this.getPrefs().edit();

        editor.putInt("puzzlePos", currentPosition);
        editor.commit();
    }

    protected void loadPuzzles() {
        Log.i(TAG, "loadPuzzles");

        SharedPreferences prefs = getPrefs();

        cursor = managedQuery(MyPuzzleProvider.CONTENT_URI_PUZZLES, MyPuzzleProvider.COLUMNS, null, null, "");

        currentPosition = prefs.getInt("puzzlePos", 0);

        Log.d(TAG, "currentPosition " + currentPosition);

        if (cursor != null) {
            totalPuzzles= cursor.getCount();

            Log.d(TAG, "totalPuzzles " + totalPuzzles);

            if (totalPuzzles > 0) {
                if (totalPuzzles < currentPosition + 1) {
                    currentPosition = 0;
                }

                seekBar.setMax(totalPuzzles - 1);
                startPuzzle();
            } else {
                Intent intent = new Intent();
                intent.setClass(PuzzleActivity.this, ImportActivity.class);
                intent.putExtra("mode", ImportService.IMPORT_PUZZLES);
                startActivityForResult(intent, ImportService.IMPORT_PUZZLES);
            }
        } else {
            Log.d(TAG, "Cursor is null");
        }
    }

    protected void startPuzzle() {
        Log.d(TAG, "startPuzzle " + currentPosition);
        cursor.moveToPosition(currentPosition);
        int index = cursor.getColumnIndex(MyPuzzleProvider.COL_PGN);
        if (index < 0) {
            Log.d(TAG, "Could not start puzzle without COL_PGN index " + index);
            return;
        }
        String sPGN = cursor.getString(index);

        Log.d(TAG, "startPuzzle " + sPGN);

        lastMoveFrom = -1;
        lastMoveTo = -1;

        gameApi.loadPGN(sPGN);
        numMoved = 0;
        gameApi.jumptoMove(0);

        myTurn = jni.getTurn();
        chessBoardView.setRotated(myTurn == BoardConstants.BLACK);
        imgStatus.setImageResource(R.drawable.ic_check_none);
        butRetry.setEnabled(false);

        if (myTurn == BoardConstants.BLACK) {
            switchTurn.setDisplayedChild(0);
        } else {
            switchTurn.setDisplayedChild(1);
        }

        if (seekBar != null) {
            seekBar.setProgress(currentPosition);
        }

        String sWhite = gameApi.getWhite();
        if (sWhite == null) {
            sWhite = "";
        } else {
            sWhite = sWhite.replace("?", "");
        }
//        String sDate = gameApi.getDate();
//        if (sDate == null) {
//            sDate = "";
//        } else {
//            sDate = sDate.replace("????", "");
//            sDate = sDate.replace(".??.??", "");
//        }

//        if (sWhite.length() > 0 && sDate.length() > 0) {
//            sWhite += ", ";
//        }

        tvPuzzleText.setText("# " + (currentPosition + 1) + " - " + sWhite /*+ sDate*/); // + "\n\n" + _mapPGNHead.get("Event") + ", " + _mapPGNHead.get("Date").replace(".??.??", ""));

    }

    protected void updateGUI() {

    }

    public void animateCorrect() {
        imgStatus.setImageResource(R.drawable.ic_check);
        pulseAnimation(imgStatus);
    }

    public void setMessage(String sMsg) {
        tvPuzzleText.setText(sMsg);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
            currentPosition = progress;
            startPuzzle();
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void OnMove(int move) {
        super.OnMove(move);

        lastMoveFrom = Move.getFrom(move);
        lastMoveTo = Move.getTo(move);

        updateSelectedSquares();

        if (jni.isEnded() == 0 && jni.getTurn() != myTurn) {
            myEngine.play();
        }
        if (jni.isEnded() != 0) {
            animateCorrect();
        }
    }

    @Override
    public void OnEngineMove(int move, int duckMove, int value) {
        if (value == -BoardConstants.VALUATION_MATE) {
            gameApi.move(move, duckMove);
            animateCorrect();
        } else {
            int moveIndex = gameApi.getPGNSize() - 1;
            String sMove = "";
            if (moveIndex >= 0) {
                sMove = gameApi.getPGNEntries().get(moveIndex)._sMove + " ";
            }
            setMessage(sMove + getString(R.string.puzzle_not_correct_move));
            imgStatus.setImageResource(R.drawable.ic_exclamation_triangle);
            butRetry.setEnabled(true);
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
