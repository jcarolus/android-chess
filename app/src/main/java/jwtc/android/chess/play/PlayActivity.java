package jwtc.android.chess.play;

import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;

import java.io.InputStream;

import jwtc.android.chess.HtmlActivity;
import jwtc.android.chess.MyPGNProvider;
import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.activities.GamePreferenceActivity;
import jwtc.android.chess.activities.GlobalPreferencesActivity;
import jwtc.android.chess.engine.EngineListener;
import jwtc.android.chess.helpers.PGNHelper;
import jwtc.android.chess.options;
import jwtc.android.chess.views.CapturedCountView;
import jwtc.android.chess.views.ChessPieceView;
import jwtc.android.chess.views.ChessPiecesStackView;
import jwtc.chess.PGNColumns;
import jwtc.chess.board.BoardConstants;


public class PlayActivity extends ChessBoardActivity implements SeekBar.OnSeekBarChangeListener, EngineListener {
    private static final String TAG = "PlayActivity";
    public static final int REQUEST_SETUP = 1;
    public static final int REQUEST_OPEN = 2;
    public static final int REQUEST_OPTIONS = 3;
    public static final int REQUEST_NEWGAME = 4;
    public static final int REQUEST_FROM_QR_CODE = 5;

    private long _lGameID;
    private SeekBar seekBar;
    private ProgressBar progressBarEngine;
    ImageButton playButton;
    private boolean vsCPU = true;
    private int turn = 1;
    private ChessPiecesStackView topPieces;
    private ChessPiecesStackView bottomPieces;

    @Override
    public boolean requestMove(int from, int to) {
        if (jni.getTurn() == turn || vsCPU == false) {
            return gameApi.requestMove(from, to);
        }
        return false;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.play);

        gameApi = new PlayApi();
        ((PlayApi)gameApi).engine.addListener(this);

        gameApi.newGame();

        afterCreate();

        playButton = findViewById(R.id.ButtonPlay);
        playButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                ((PlayApi)gameApi).engine.play();
            }
        });

        progressBarEngine = findViewById(R.id.ProgressBarPlay);

        // findViewById(R.id.TextViewClockTimeOpp)

        seekBar = findViewById(R.id.SeekBarMain);
        seekBar.setOnSeekBarChangeListener(this);
        seekBar.setMax(1);

        topPieces = findViewById(R.id.topPieces);
        bottomPieces = findViewById(R.id.bottomPieces);

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
    protected void onResume() {
        SharedPreferences prefs = getPrefs();

        vsCPU = prefs.getBoolean("opponent", true);
        turn = prefs.getBoolean("turn", true) ? 1 : 0;

        chessBoardView.setRotated(turn == BoardConstants.BLACK);

        String sPGN = "";
        String sFEN = prefs.getString("FEN", null);
        final Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();
        Uri uri = intent.getData();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            _lGameID = 0;
            Log.i("onResume", "action send with type " + type);
            if ("application/x-chess-pgn".equals(type)) {
                sPGN = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (sPGN != null) {
                    sPGN = sPGN.trim();
                    gameApi.loadPGN(sPGN);
                }
            } else {
                sFEN = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (sFEN != null) {
                    sFEN = sFEN.trim();

                    gameApi.initFEN(sFEN, true);
                }
            }
        } else if (uri != null) {
            _lGameID = 0;
            Log.i(TAG, "onResume opening " + uri.toString());

            try {
                InputStream is = getContentResolver().openInputStream(uri);
                sPGN = PGNHelper.getPGNFromInputStream(is);
                gameApi.loadPGN(sPGN);

            } catch (Exception e) {
                Log.e("onResume", "Failed " + e.toString());
            }
        } else if (sFEN != null) {
            // default, from prefs
            Log.i("onResume", "Loading FEN " + sFEN);
            _lGameID = 0;
            gameApi.initFEN(sFEN, true);
        } else {
            _lGameID = prefs.getLong("game_id", 0);
            if (_lGameID > 0) {
                Log.i("onResume", "loading saved game " + _lGameID);
                loadGame();
            } else {
                sPGN = prefs.getString("game_pgn", null);
                Log.i("onResume", "pgn: " + sPGN);
                gameApi.loadPGN(sPGN);
            }
        }


        super.onResume();
    }


    @Override
    protected void onPause() {
        //Debug.stopMethodTracing();

        if (_lGameID > 0) {
            ContentValues values = new ContentValues();

            values.put(PGNColumns.DATE, gameApi.getDate().getTime());
            values.put(PGNColumns.WHITE, gameApi.getWhite());
            values.put(PGNColumns.BLACK, gameApi.getBlack());
            values.put(PGNColumns.PGN, gameApi.exportFullPGN());
//            values.put(PGNColumns.RATING, _fGameRating);
            values.put(PGNColumns.EVENT, gameApi.getPGNHeadProperty("Event"));

            saveGame(values, false);
        }
        SharedPreferences.Editor editor = this.getPrefs().edit();
        editor.putLong("game_id", _lGameID);
        editor.putString("game_pgn", gameApi.exportFullPGN());
        editor.putString("FEN", null); //

//         if (_uriNotification == null)
//            editor.putString("NotificationUri", null);
//        else
//            editor.putString("NotificationUri", _uriNotification.toString());

        editor.commit();

        super.onPause();
    }


    @Override
    public void OnMove(int move) {
        super.OnMove(move);

        updateCapturedPieces();
        updateSeekBar();
    }

    @Override
    public void OnEngineMove(int move) {
        toggleEngineProgress(false);
    }

    @Override
    public void OnInfo(String message) {

    }

    @Override
    public void OnEngineStarted() {
        toggleEngineProgress(true);
    }

    @Override
    public void OnEngineAborted() {
        toggleEngineProgress(false);
    }

    @Override
    public void OnEngineError() {
        toggleEngineProgress(false);
    }

    @Override
    public void OnState() {
        super.OnState();

        updateCapturedPieces();
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
                gameApi.newGame();
                return true;
            case R.id.action_new_960:
                showChess960Dialog();
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
            case R.id.action_setup:
                intent = new Intent();
                intent.setClass(PlayActivity.this, jwtc.android.chess.setup.SetupActivity.class);
                startActivityForResult(intent, REQUEST_SETUP);
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

    protected void updateCapturedPieces() {
        topPieces.removeAllViews();
        bottomPieces.removeAllViews();

        int piece, turnAt;
        // turn
        for (turnAt = 0; turnAt < 2; turnAt++) {
            for (piece = 0; piece < 5; piece++) {
                int numCaptured = jni.getNumCaptured(turnAt, piece);
//                Log.d(TAG, "numCaptured for " + turnAt + " " + piece + " " + numCaptured);
                if (numCaptured > 0) {
                    ChessPieceView capturedPiece = new ChessPieceView(this, turnAt, piece, piece);
                    CapturedCountView capturedCountView = new CapturedCountView(this, numCaptured, piece);
                    if (turn == BoardConstants.WHITE) {
                        if (turnAt == BoardConstants.BLACK) {
                            bottomPieces.addView(capturedPiece);
                            bottomPieces.addView(capturedCountView);
                        } else {
                            topPieces.addView(capturedPiece);
                            topPieces.addView(capturedCountView);
                        }
                    } else {
                        if (turnAt == BoardConstants.WHITE) {
                            bottomPieces.addView(capturedPiece);
                            bottomPieces.addView(capturedCountView);
                        } else {
                            topPieces.addView(capturedPiece);
                            topPieces.addView(capturedCountView);
                        }
                    }
                }
            }
        }

        // topPieces.addView();
    }


    private void loadGame() {
        if (_lGameID > 0) {
            Uri uri = ContentUris.withAppendedId(MyPGNProvider.CONTENT_URI, _lGameID);
            Cursor c = managedQuery(uri, PGNColumns.COLUMNS, null, null, null);
            if (c != null && c.getCount() == 1) {

                c.moveToFirst();

                _lGameID = c.getLong(c.getColumnIndex(PGNColumns._ID));
                String sPGN = c.getString(c.getColumnIndex(PGNColumns.PGN));
                gameApi.loadPGN(sPGN);

                gameApi.setPGNHeadProperty("Event", c.getString(c.getColumnIndex(PGNColumns.EVENT)));
                gameApi.setPGNHeadProperty("White", c.getString(c.getColumnIndex(PGNColumns.WHITE)));
                gameApi.setPGNHeadProperty("Black", c.getString(c.getColumnIndex(PGNColumns.BLACK)));
                gameApi.setDateLong(c.getLong(c.getColumnIndex(PGNColumns.DATE)));

            } else {
                _lGameID = 0; // probably deleted
            }
        } else {
            _lGameID = 0;
        }
    }

    public void saveGame(ContentValues values, boolean bCopy) {

        SharedPreferences.Editor editor = this.getPrefs().edit();
        editor.putString("FEN", null);
        editor.commit();

        gameApi.setPGNHeadProperty("Event", (String) values.get(PGNColumns.EVENT));
        gameApi.setPGNHeadProperty("White", (String) values.get(PGNColumns.WHITE));
        gameApi.setPGNHeadProperty("Black", (String) values.get(PGNColumns.BLACK));
        gameApi.setDateLong((Long) values.get(PGNColumns.DATE));

//        _fGameRating = (Float) values.get(PGNColumns.RATING);
        //

        if (_lGameID > 0 && (bCopy == false)) {
            Uri uri = ContentUris.withAppendedId(MyPGNProvider.CONTENT_URI, _lGameID);
            getContentResolver().update(uri, values, null, null);
        } else {
            Uri uri = MyPGNProvider.CONTENT_URI;
            Uri uriInsert = getContentResolver().insert(uri, values);
            Cursor c = managedQuery(uriInsert, new String[]{PGNColumns._ID}, null, null, null);
            if (c != null && c.getCount() == 1) {
                c.moveToFirst();
                _lGameID = c.getLong(c.getColumnIndex(PGNColumns._ID));
            }
        }
    }

    private void toggleEngineProgress(boolean showProgress) {
        Log.d(TAG, "toggleEngineProgress " + showProgress);
        if (showProgress) {
            playButton.setVisibility(View.GONE);
            progressBarEngine.setVisibility(View.VISIBLE);
        } else {
            progressBarEngine.setVisibility(View.GONE);
            playButton.setVisibility(View.VISIBLE);
        }
    }

    private void showChess960Dialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(PlayActivity.this);
        builder.setTitle(getString(R.string.title_chess960_manual_random));
        final EditText input = new EditText(PlayActivity.this);
        input.setInputType(InputType.TYPE_CLASS_PHONE);
        builder.setView(input);
        builder.setPositiveButton(getString(R.string.choice_manually), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
            try {
                int seed = Integer.parseInt(input.getText().toString());

                if (seed >= 0 && seed <= 960) {
                    gameApi.newGameRandomFischer(seed);

                } else {
                    doToast(getString(R.string.err_chess960_position_range));
                }
            } catch (Exception ex) {
                doToast(getString(R.string.err_chess960_position_format));
            }
            }
        });
        builder.setNegativeButton(getString(R.string.choice_random), new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
            int seed = -1;
            gameApi.newGameRandomFischer(seed);
            }
        });

        AlertDialog alert = builder.create();
        alert.show();
    }
}
