package jwtc.android.chess.puzzle;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.chess.Move;

public class PuzzleActivity extends ChessBoardActivity implements SeekBar.OnSeekBarChangeListener {
    private static final String TAG = "PuzzleActivity";
    private Cursor _cursor = null;
    private SeekBar _seekBar;
    private TextView _tvPuzzleText;
    private ImageView _imgTurn;
    private ImageButton _butPrev, _butNext;
    private ImageView _imgStatus;
    private int currentPosition, totalPuzzles;

    @Override
    public boolean requestMove(int from, int to) {

        if (gameApi.getPGNSize() <= jni.getNumBoard() - 1) {
//            setMessage(getString(R.string.puzzle_already_solved));
            return gameApi.requestMove(from, to);
        }
        int move = gameApi.getPGNEntries().get(jni.getNumBoard() - 1)._move;
        int theMove = Move.makeMove(from, to);

        if (Move.equalPositions(move, theMove)) {
            gameApi.jumptoMove(jni.getNumBoard());

//            updateState();

//            setMessage("Correct!");
            _imgStatus.setImageResource(R.drawable.indicator_ok);
			/*
			if(_arrPGN.size() == m_game.getBoard().getNumBoard()-1)
				play();
			else {
				jumptoMove(m_game.getBoard().getNumBoard());
				updateState();
			}*/

        return true;
        } else {
            // check for illegal move

//            setMessage(Move.toDbgString(theMove) + (gameApi.isLegalMove(from, to) ? getString(R.string.puzzle_not_correct_move) : getString(R.string.puzzle_invalid_move)));
            _imgStatus.setImageResource(R.drawable.indicator_error);
        }

        return false;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.puzzle);

        gameApi = new PuzzleApi();

        afterCreate();

        currentPosition = 0;
        totalPuzzles = 0;
        _seekBar = findViewById(R.id.SeekBarPuzzle);
        _seekBar.setOnSeekBarChangeListener(this);

        _tvPuzzleText = findViewById(R.id.TextViewPuzzleText);
        _imgTurn = findViewById(R.id.ImageTurn);

        _imgStatus = findViewById(R.id.ImageStatus);
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "onResume");

        SharedPreferences prefs = getPrefs();

        _cursor = managedQuery(MyPuzzleProvider.CONTENT_URI_PUZZLES, MyPuzzleProvider.COLUMNS, null, null, "");

        currentPosition = prefs.getInt("puzzlePos", 0);

        if (_cursor != null) {
            totalPuzzles= _cursor.getCount();

            if (totalPuzzles < currentPosition + 1) {
                currentPosition = 0;


            }

            startPuzzle();
        }

        super.onResume();
    }

    protected void startPuzzle() {
        _cursor.moveToPosition(currentPosition);
        String sPGN = _cursor.getString(_cursor.getColumnIndex(MyPuzzleProvider.COL_PGN));
        gameApi.loadPGN(sPGN);
        updateGUI();
    }

    protected void updateGUI() {

    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }
}
