package jwtc.android.chess.puzzle;

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
import jwtc.android.chess.tools.ImportActivity;
import jwtc.android.chess.tools.ImportService;
import jwtc.chess.Move;
import jwtc.chess.board.BoardConstants;

public class PuzzleActivity extends ChessBoardActivity implements SeekBar.OnSeekBarChangeListener {
    private static final String TAG = "PuzzleActivity";
    private Cursor cursor = null;
    private SeekBar seekBar;
    private TextView tvPuzzleText;
    private ViewSwitcher switchTurn;
    private ImageButton butPrev, butNext;
    private ImageView imgStatus;
    private int currentPosition, totalPuzzles;
    private TableLayout layoutTurn;
    private Button butShow;

    @Override
    public boolean requestMove(int from, int to) {

        if (gameApi.getPGNSize() <= jni.getNumBoard() - 1) {
            setMessage(getString(R.string.puzzle_already_solved));
            rebuildBoard();
            return false;
        }
        int move = gameApi.getPGNEntries().get(jni.getNumBoard() - 1)._move;
        int theMove = Move.makeMove(from, to);

        if (Move.equalPositions(move, theMove)) {
            gameApi.jumptoMove(jni.getNumBoard());
            imgStatus.setImageResource(R.drawable.ic_check);
            setMessage("");

            return true;
        } else {
            // check for illegal move
            setMessage(Move.toDbgString(theMove) + (gameApi.isLegalMove(from, to) ? getString(R.string.puzzle_not_correct_move) : getString(R.string.puzzle_invalid_move)));
            imgStatus.setImageResource(R.drawable.ic_exclamation_triangle);
            rebuildBoard();
        }

        return false;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.puzzle);

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

        butShow = findViewById(R.id.ButtonPuzzleShow);
        butShow.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                gameApi.jumptoMove(jni.getNumBoard());
            }
        });

        chessBoardView.setNextFocusRightId(R.id.ButtonPuzzlePrevious);
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "onResume");

        layoutTurn.setBackgroundColor(ColorSchemes.getDark());
        tvPuzzleText.setTextColor(ColorSchemes.getHightlightColor());

        loadPuzzles();

        super.onResume();
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
        String sPGN = cursor.getString(cursor.getColumnIndex(MyPuzzleProvider.COL_PGN));

        Log.d(TAG, "startPuzzle " + sPGN);

        gameApi.loadPGN(sPGN);
        gameApi.jumptoMove(0);

        final int turn = jni.getTurn();
        chessBoardView.setRotated(turn == BoardConstants.BLACK);

        if (turn == BoardConstants.BLACK) {
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

    public void setMessage(String sMsg) {
        tvPuzzleText.setText(sMsg);
    }

    public void setMessage(int res) {
        tvPuzzleText.setText(res);
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
}
