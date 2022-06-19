package jwtc.android.chess.practice;

import android.content.ComponentName;
import android.content.ContentResolver;
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
import android.widget.TextView;
import android.widget.ViewSwitcher;

import java.util.Timer;
import java.util.TimerTask;

import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.puzzle.MyPuzzleProvider;
import jwtc.android.chess.puzzle.PuzzleActivity;
import jwtc.android.chess.tools.ImportListener;
import jwtc.android.chess.tools.ImportService;
import jwtc.chess.Move;
import jwtc.chess.board.BoardConstants;

public class PracticeActivity extends ChessBoardActivity implements ImportListener {
    private static final String TAG = "PracticeActivity";

    private TextView _tvPracticeMove, _tvPracticeTime, _tvPracticeAvgTime;
    private Button _butShow;
    private ImageButton _butPause, _butNext;
    private int _numTotal, _iPos;
    private Cursor _cursor;
    private Timer _timer;
    private int _ticks, _playTicks;
    private ViewSwitcher _switchTurn;
    private ImageView _imgStatus;
    protected ContentResolver _cr;
    private boolean _isPlaying;
    private ImportService importService;

    protected Handler m_timerHandler = new Handler() {
        /** Gets called on every message that is received */
        // @Override
        public void handleMessage(Message msg) {
            _tvPracticeTime.setText(formatTime(msg.getData().getInt("ticks")));
        }

    };

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            Log.i(TAG, "onServiceConnected");
            importService = ((ImportService.LocalBinder)service).getService();
            importService.addListener(PracticeActivity.this);
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
    public boolean requestMove(final int from, final int to) {
        if (gameApi.getPGNSize() <= jni.getNumBoard() - 1) {
            setMessage("Finished position");
            return false;
        }
        int move = gameApi.getPGNEntries().get(jni.getNumBoard() - 1)._move;
        int theMove = Move.makeMove(from, to);

        if (Move.equalPositions(move, theMove)) {
            gameApi.jumptoMove(jni.getNumBoard());

            if (gameApi.getPGNSize() == jni.getNumBoard() - 1) {
                //play();
                _imgStatus.setImageResource(R.drawable.indicator_ok);
                setMessage("Correct!");
                _isPlaying = false;
                _butNext.setEnabled(true);
                _butShow.setEnabled(false);
            } else {
                gameApi.jumptoMove(jni.getNumBoard());
            }

            return true;
        } else {
            _imgStatus.setImageResource(R.drawable.indicator_error);
            setMessage(Move.toDbgString(theMove) + (gameApi.isLegalMove(from, to) ? " is not expected" : " is an illegal move"));
        }
        return false;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.practice);

        gameApi = new PracticeApi();

        afterCreate();

        _isPlaying = false;

        _cr = getContentResolver();

        _tvPracticeMove = (TextView) findViewById(R.id.TextViewPracticeMove);
        _tvPracticeTime = (TextView) findViewById(R.id.TextViewPracticeTime);
        _tvPracticeAvgTime = (TextView) findViewById(R.id.TextViewPracticeAvgTime);

        _switchTurn = (ViewSwitcher) findViewById(R.id.ImageTurn);

        _imgStatus = (ImageView) findViewById(R.id.ImageStatus);

        _butShow = (Button) findViewById(R.id.ButtonPracticeShow);
        _butNext = (ImageButton) findViewById(R.id.ButtonPracticeNext);

        _butShow.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {

                gameApi.jumptoMove(jni.getNumBoard());

                if (gameApi.getPGNSize() == jni.getNumBoard() - 1) {
                    _butNext.setEnabled(true);
                    _butShow.setEnabled(false);
                }
            }
        });

        _butNext.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                _butNext.setEnabled(false);
                _butShow.setEnabled(true);
                if (_iPos + 1 < _numTotal) {
                    _iPos++;
                    startPuzzle();
                } else {
                    // completed
                    setMessage("You completed all puzzles!!!");
                }
            }
        });


    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.i(TAG, "onResume");

        _isPlaying = false;
        scheduleTimer();

        loadPuzzles();
    }

    @Override
    protected void onPause() {
        super.onPause();

        SharedPreferences.Editor editor = this.getPrefs().edit();
        if (_timer != null)
            _timer.cancel();
        _timer = null;
        _isPlaying = false;


        editor.putInt("practicePos", _iPos);
        editor.putInt("practiceTicks", _ticks);
    }

    protected void loadPuzzles() {
        SharedPreferences prefs = getPrefs();

        _ticks = prefs.getInt("practiceTicks", 0);
        _playTicks = 0;

        _iPos = prefs.getInt("practicePos", 0);

        _cursor = managedQuery(MyPuzzleProvider.CONTENT_URI_PRACTICES, MyPuzzleProvider.COLUMNS, null, null, "");

        if (_cursor != null) {
            _numTotal = _cursor.getCount();

            if (_numTotal > 0) {
                if (_iPos + 1 >= _numTotal) {
                    _iPos = 0;
                }
                startPuzzle();
            } else {
                Intent intent = new Intent(this, ImportService.class);
                intent.putExtra(ImportService.IMPORT_MODE, ImportService.IMPORT_PRACTICE);
                startService(intent);
            }
        }
    }

    protected void startPuzzle() {
        _cursor.moveToPosition(_iPos);
        _isPlaying = true;
        _playTicks = 0;

        String sPGN = _cursor.getString(_cursor.getColumnIndex(MyPuzzleProvider.COL_PGN));

        Log.i("ChessViewPractice", "init: " + sPGN);

        gameApi.loadPGN(sPGN);

        gameApi.jumptoMove(0);

        Log.i("ChessViewPractice", gameApi.getPGNSize() + " moves from " + sPGN + " turn " + jni.getTurn());

        final int turn = jni.getTurn();
        chessBoardView.setRotated(turn == BoardConstants.BLACK);

        _tvPracticeMove.setText("# " + (_iPos + 1));

        if (turn == BoardConstants.BLACK) {
            _switchTurn.setDisplayedChild(0);
        } else {
            _switchTurn.setDisplayedChild(1);
        }

        _imgStatus.setImageResource(R.drawable.indicator_none);

        Float f = (float) (_ticks) / (_iPos + 1);
        _tvPracticeAvgTime.setText(String.format("%.1f", f));
    }

    public void setMessage(String sMsg) {
        _tvPracticeMove.setText(sMsg);
    }

    public void setMessage(int res) {
        _tvPracticeMove.setText(res);
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

    protected void scheduleTimer() {
        _timer = new Timer(true);
        _timer.schedule(new TimerTask() {
            @Override
            public void run() {

                if (false == _isPlaying) {
                    return;
                }
                _ticks++;
                _playTicks++;

                Message msg = new Message();
                msg.what = 1;
                Bundle bun = new Bundle();
                bun.putInt("ticks", _playTicks);
                msg.setData(bun);
                m_timerHandler.sendMessage(msg);

            }
        }, 1000, 1000);
    }

    private String formatTime(int sec) {
        return String.format("%d:%02d", (int) (Math.floor(sec / 60)), sec % 60);
    }
}
