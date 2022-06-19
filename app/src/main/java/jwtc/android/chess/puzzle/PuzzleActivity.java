package jwtc.android.chess.puzzle;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.tools.ImportListener;
import jwtc.android.chess.tools.ImportService;
import jwtc.chess.Move;
import jwtc.chess.board.BoardConstants;

public class PuzzleActivity extends ChessBoardActivity implements SeekBar.OnSeekBarChangeListener, ImportListener {
    private static final String TAG = "PuzzleActivity";
    private Cursor _cursor = null;
    private SeekBar _seekBar;
    private TextView _tvPuzzleText;
    private ImageView _imgTurn;
    private ImageButton _butPrev, _butNext;
    private ImageView _imgStatus;
    private int currentPosition, totalPuzzles;
    private ImportService importService;

    @Override
    public boolean requestMove(int from, int to) {

        if (gameApi.getPGNSize() <= jni.getNumBoard() - 1) {
            setMessage(getString(R.string.puzzle_already_solved));
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

            setMessage(Move.toDbgString(theMove) + (gameApi.isLegalMove(from, to) ? getString(R.string.puzzle_not_correct_move) : getString(R.string.puzzle_invalid_move)));
            _imgStatus.setImageResource(R.drawable.indicator_error);
        }

        return false;
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            Log.i(TAG, "onServiceConnected");
            importService = ((ImportService.LocalBinder)service).getService();
            importService.addListener(PuzzleActivity.this);
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
//            OnSessionEnded();
            importService = null;
            Log.i(TAG, "onServiceDisconnected");
        }
    };

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

        _butPrev = (ImageButton) findViewById(R.id.ButtonPuzzlePrevious);
        _butPrev.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                if (currentPosition > 0) {
                    currentPosition--;
                }
                startPuzzle();
            }
        });

        _butNext = (ImageButton) findViewById(R.id.ButtonPuzzleNext);
        _butNext.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                if (currentPosition + 1 < totalPuzzles) {
                    currentPosition++;
                    startPuzzle();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "onResume");

        loadPuzzles();

        super.onResume();
    }

    protected void loadPuzzles() {
        Log.i(TAG, "loadPuzzles");

        SharedPreferences prefs = getPrefs();

        _cursor = managedQuery(MyPuzzleProvider.CONTENT_URI_PUZZLES, MyPuzzleProvider.COLUMNS, null, null, "");

        currentPosition = prefs.getInt("puzzlePos", 0);

        Log.d(TAG, "currentPosition " + currentPosition);

        if (_cursor != null) {
            totalPuzzles= _cursor.getCount();

            Log.d(TAG, "totalPuzzles " + totalPuzzles);

            if (totalPuzzles == 0) {

                Intent intent = new Intent(this, ImportService.class);
                intent.putExtra(ImportService.IMPORT_MODE, ImportService.IMPORT_PUZZLES);
                startService(intent);

            } else {

                if (totalPuzzles < currentPosition + 1) {
                    currentPosition = 0;
                }


                _seekBar.setMax(totalPuzzles - 1);
                startPuzzle();
            }
        } else {
            Log.d(TAG, "Cursor is null");
        }
    }

    protected void startPuzzle() {
        Log.d(TAG, "startPuzzle " + currentPosition);
        _cursor.moveToPosition(currentPosition);
        String sPGN = _cursor.getString(_cursor.getColumnIndex(MyPuzzleProvider.COL_PGN));

        Log.d(TAG, "startPuzzle " + sPGN);

        gameApi.loadPGN(sPGN);
        gameApi.jumptoMove(0);

        final int turn = jni.getTurn();
        chessBoardView.setRotated(turn == BoardConstants.BLACK);

        _imgTurn.setImageResource((turn == BoardConstants.WHITE ? R.drawable.turnwhite : R.drawable.turnblack));

        if (_seekBar != null) {
            _seekBar.setProgress(currentPosition);
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

        _tvPuzzleText.setText("# " + (currentPosition + 1) + " - " + sWhite /*+ sDate*/); // + "\n\n" + _mapPGNHead.get("Event") + ", " + _mapPGNHead.get("Date").replace(".??.??", ""));

    }

    protected void updateGUI() {

    }

    public void setMessage(String sMsg) {
        _tvPuzzleText.setText(sMsg);
    }

    public void setMessage(int res) {
        _tvPuzzleText.setText(res);
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
    public void OnImportStarted(int mode) {
        Log.d(TAG, "OnImportStarted");
    }

    @Override
    public void OnImportSuccess(int mode) {
        Log.d(TAG, "OnImportSuccess");

        loadPuzzles();
    }

    @Override
    public void OnImportFail(int mode) {
        Log.d(TAG, "OnImportFail");
    }

    @Override
    public void OnImportFinished(int mode) {
        Log.d(TAG, "OnImportFinished");
    }

    @Override
    public void OnImportFatalError(int mode) {
        Log.d(TAG, "OnImportFatalError");
    }
}
