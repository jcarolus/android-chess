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
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;

import jwtc.android.chess.GamesListActivity;
import jwtc.android.chess.helpers.Clipboard;
import jwtc.android.chess.helpers.MyPGNProvider;
import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.activities.GlobalPreferencesActivity;
import jwtc.android.chess.constants.ColorSchemes;
import jwtc.android.chess.constants.PieceSets;
import jwtc.android.chess.engine.EngineApi;
import jwtc.android.chess.engine.EngineListener;
import jwtc.android.chess.engine.LocalEngine;
import jwtc.android.chess.helpers.PGNHelper;
import jwtc.android.chess.helpers.ResultDialogListener;
import jwtc.android.chess.services.ClockListener;
import jwtc.android.chess.services.EcoService;
import jwtc.android.chess.services.GameApi;
import jwtc.android.chess.services.LocalClockApi;
import jwtc.android.chess.views.CapturedCountView;
import jwtc.android.chess.views.ChessPieceView;
import jwtc.android.chess.views.ChessPiecesStackView;
import jwtc.chess.Move;
import jwtc.chess.PGNColumns;
import jwtc.chess.board.BoardConstants;


public class PlayActivity extends ChessBoardActivity implements SeekBar.OnSeekBarChangeListener, EngineListener, ResultDialogListener, ClockListener {
    private static final String TAG = "PlayActivity";
    public static final int REQUEST_SETUP = 1;
    public static final int REQUEST_OPEN = 2;
    public static final int REQUEST_GAME_SETTINGS = 3;
    public static final int REQUEST_FROM_QR_CODE = 4;
    public static final int REQUEST_MENU = 5;
    public static final int REQUEST_CLOCK = 6;
    public static final int REQUEST_SAVE_GAME = 7;

    private LocalClockApi localClock = new LocalClockApi();
    private EngineApi myEngine;
    private EcoService ecoService = new EcoService();
    private long lGameID;
    private SeekBar seekBar;
    private ProgressBar progressBarEngine;
    private ImageButton playButton;
    private boolean vsCPU = true;
    private int myTurn = 1, requestMoveFrom = -1, requestMoveTo = -1;
    private ChessPiecesStackView topPieces;
    private ChessPiecesStackView bottomPieces;
    private ViewSwitcher switchTurnMe, switchTurnOpp;
    private TextView textViewOpponent, textViewMe, textViewOpponentClock, textViewMyClock, textViewEngineValue, textViewEcoValue;
    private TableLayout layoutBoardTop, layoutBoardBottom;
    private SwitchMaterial switchSound, switchBlindfold;

    @Override
    public boolean requestMove(final int from, final int to) {
        if (jni.getTurn() == myTurn || vsCPU == false) {

            if (super.requestMove(from, to)) {
                return true;
            }
        } else {
//            if (requestMoveFrom != -1 && requestMoveTo != -1) {
//                highlightedPositions.clear();
//            } else {
//                requestMoveFrom = from;
//                requestMoveTo = to;
//                highlightedPositions.add(from);
//                highlightedPositions.add(to);
//            }
        }
        rebuildBoard();
        return false;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.play);

        gameApi = new GameApi();

        localClock.addListener(this);

        afterCreate();

        playButton = findViewById(R.id.ButtonPlay);
        playButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                if (jni.isEnded() == 0) {
                    if (jni.getNumBoard() < gameApi.getPGNSize()) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(PlayActivity.this)
                            .setTitle(getString(R.string.title_create_new_line))
                            .setNegativeButton(R.string.alert_no, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                        builder.setPositiveButton(R.string.alert_yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                playIfEngineCanMove();
                            }
                        });
                        AlertDialog alert = builder.create();
                        alert.show();
                    } else {
                        playIfEngineCanMove();
                    }
                }
            }
        });

        progressBarEngine = findViewById(R.id.ProgressBarPlay);

        final MenuDialog menuDialog = new MenuDialog(this, this, REQUEST_MENU);

        ImageButton buttonMenu = findViewById(R.id.ButtonMenu);
        buttonMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                menuDialog.show();
            }
        });

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

        ImageButton butPgn = findViewById(R.id.ButtonPGN);
        butPgn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PGNDialog dialog = new PGNDialog(PlayActivity.this, gameApi);
                dialog.show();
            }
        });

        switchTurnMe = findViewById(R.id.ImageTurnMe);
        switchTurnOpp = findViewById(R.id.ImageTurnOpp);

        textViewOpponent = findViewById(R.id.TextViewOpponent);
        textViewMe = findViewById(R.id.TextViewMe);

        textViewOpponentClock = findViewById(R.id.TextViewClockTimeOpp);
        textViewMyClock = findViewById(R.id.TextViewClockTimeMe);

        layoutBoardTop = findViewById(R.id.LayoutBoardTop);
        layoutBoardBottom = findViewById(R.id.LayoutBoardBottom);

        textViewEngineValue = findViewById(R.id.TextViewEngineValue);
        textViewEcoValue = findViewById(R.id.TextViewEcoValue);

        switchSound = findViewById(R.id.SwitchSound);
        switchSound.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
           public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
               fVolume = switchSound.isChecked() ? 1.0f : 0.0f;
            }
        });

        switchBlindfold = findViewById(R.id.SwitchBlindfold);
        switchBlindfold.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
           public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (switchBlindfold.isChecked()) {
                    PieceSets.selectedBlindfoldMode = PieceSets.BLINDFOLD_HIDE_PIECES;
                    chessBoardView.invalidatePieces();
                    topPieces.setVisibility(View.INVISIBLE);
                    bottomPieces.setVisibility(View.INVISIBLE);
                } else {
                    PieceSets.selectedBlindfoldMode = PieceSets.BLINDFOLD_SHOW_PIECES;
                    chessBoardView.invalidatePieces();
                    topPieces.setVisibility(View.VISIBLE);
                    bottomPieces.setVisibility(View.VISIBLE);
                    topPieces.invalidatePieces();
                    bottomPieces.invalidatePieces();
                }
           }
       });

    }

    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences prefs = getPrefs();

        String sPGN = "";
        String sFEN = prefs.getString("FEN", null);
        final Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();
        Uri uri = intent.getData();

        myEngine = new LocalEngine();

        // opening database path
        String sOpeningDb = prefs.getString("OpeningDb", null);
        if (sOpeningDb == null) {
            try {
                ((LocalEngine) myEngine).installDb(getAssets().open("db.bin"), "/data/data/jwtc.android.chess/db.bin");
            } catch (Exception e) {
                Log.d(TAG, "Exception installing db " + e.getMessage());
            }
        } else {
            Uri uriDb = Uri.parse(sOpeningDb);
            ((LocalEngine) myEngine).setOpeningDb(uriDb.getPath());
        }

        myEngine.addListener(this);

        layoutBoardTop.setBackgroundColor(ColorSchemes.getDark());
        layoutBoardBottom.setBackgroundColor(ColorSchemes.getDark());

        textViewOpponent.setTextColor(ColorSchemes.getHightlightColor());
        textViewMe.setTextColor(ColorSchemes.getHightlightColor());
        textViewOpponentClock.setTextColor(ColorSchemes.getHightlightColor());
        textViewMyClock.setTextColor(ColorSchemes.getHightlightColor());

        updateClockByPrefs();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            lGameID = 0;
            Log.i("onResume", "action send with type " + type);
            if ("application/x-chess-pgn".equals(type)) {
                sPGN = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (sPGN != null) {
                    sPGN = sPGN.trim();
                    gameApi.loadPGN(sPGN);
                    updateForNewGame();
                }
            } else {
                sFEN = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (sFEN != null) {
                    sFEN = sFEN.trim();

                    gameApi.initFEN(sFEN, true);
                    updateForNewGame();
                }
            }
        } else if (uri != null) {
            lGameID = 0;
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
            lGameID = 0;
            gameApi.initFEN(sFEN, true);
            updateForNewGame();
        } else {
            lGameID = prefs.getLong("game_id", 0);
            if (lGameID > 0) {
                Log.i("onResume", "loading saved game " + lGameID);
                loadGame();
            } else {
                sPGN = prefs.getString("game_pgn", null);
                Log.i("onResume", "pgn: " + sPGN);
                if (sPGN != null) {
                    gameApi.loadPGN(sPGN);
                } else {
                    gameApi.newGame();
                    updateForNewGame();
                }
            }
        }

        updateGameSettingsByPrefs();

        if (prefs.getBoolean("showECO", true)) {
            ecoService.load(getAssets());
        }

        switchSound.setChecked(prefs.getBoolean("moveSounds", false));
        switchBlindfold.setChecked(false);
    }


    @Override
    protected void onPause() {
        //Debug.stopMethodTracing();

        myEngine.abort();
        myEngine.removeListener(this);

        localClock.stopClock();

        if (lGameID > 0) {
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
        editor.putLong("game_id", lGameID);
        editor.putString("game_pgn", gameApi.exportFullPGN());
        editor.putString("FEN", null); //


        editor.putLong("clockWhiteMillies", localClock.getWhiteRemaining());
        editor.putLong("clockBlackMillies", localClock.getBlackRemaining());
        editor.putLong("clockStartTime", localClock.getLastMeasureTime());

//         if (_uriNotification == null)
//            editor.putString("NotificationUri", null);
//        else
//            editor.putString("NotificationUri", _uriNotification.toString());

        editor.commit();

        super.onPause();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        Log.i(TAG, "onActivityResult");

        if (requestCode == REQUEST_OPEN) {
            if (resultCode == RESULT_OK) {

                Uri uri = data.getData();
                try {
                    lGameID = Long.parseLong(uri.getLastPathSegment());
                } catch (Exception ex) {
                    lGameID = 0;
                }
                SharedPreferences.Editor editor = this.getPrefs().edit();
                editor.putLong("game_id", lGameID);
                editor.putInt("boardNum", 0);
                editor.putString("FEN", null);
                editor.putBoolean("playAsBlack", false);
                editor.commit();
            }
        } else if (requestCode == REQUEST_FROM_QR_CODE) {
            if (resultCode == RESULT_OK) {
                String contents = data.getStringExtra("SCAN_RESULT");
                //String format = data.getStringExtra("SCAN_RESULT_FORMAT");

                SharedPreferences.Editor editor = this.getPrefs().edit();
                editor.putLong("game_id", 0);
                editor.putInt("boardNum", 0);
                editor.putString("FEN", contents);
                editor.commit();
            }
        }
    }


    @Override
    public void OnMove(int move) {
        super.OnMove(move);

        updateGUI();

        playIfEngineMove();
    }

    @Override
    public void OnDuckMove(int duckMove) {
        super.OnDuckMove(duckMove);

        updateGUI();

        playIfEngineMove();
    }

    @Override
    public void OnEngineMove(int move, int duckMove) {
        toggleEngineProgress(false);

        gameApi.move(move, duckMove);
        lastMoveFrom = Move.getFrom(move);
        lastMoveTo = Move.getTo(move);
        highlightedPositions.clear();

        updateGUI();
    }

    @Override
    public void OnEngineInfo(String message) {
        textViewEngineValue.setText(message);
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
        textViewEngineValue.setText("Engine error!");
    }

    @Override
    public void OnState() {
        super.OnState();

        updateGUI();
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {

            if (jni.getNumBoard() - 1 > progress) {
                progress++;
            }
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

    protected void updateGUI() {
        // only if on top of move stack
        if (this.gameApi.getPGNSize() == jni.getNumBoard() - 1) {
            localClock.switchTurn(jni.getTurn());
        }

        updateSelectedSquares();
        updateCapturedPieces();
        updateSeekBar();
        updateTurnSwitchers();
        updatePlayers();
        updateEco();
    }

    protected void updateSeekBar() {
        seekBar.setMax(this.gameApi.getPGNSize());
        seekBar.setProgress(jni.getNumBoard() - 1);
//        seekBar.invalidate();
        Log.d(TAG, "updateSeekBar " + seekBar.getMax() + " - " + seekBar.getProgress());
    }

    protected void updatePlayers() {
        textViewOpponent.setText(gameApi.getOpponentPlayerName(myTurn));
        textViewMe.setText(gameApi.getMyPlayerName(myTurn));
    }

    protected void updateEco() {
        String sEco = ecoService.getEco(gameApi.getPGNEntries(), jni.getNumBoard() - 1);
        textViewEcoValue.setText( sEco != null ? sEco : "");
    }

    protected void updateCapturedPieces() {
        topPieces.removeAllViews();
        bottomPieces.removeAllViews();

        int piece, turnAt;
        for (turnAt = 0; turnAt < 2; turnAt++) {
            for (piece = 0; piece < 5; piece++) {
                int numCaptured = jni.getNumCaptured(turnAt, piece);
//                Log.d(TAG, "numCaptured for " + turnAt + " " + piece + " " + numCaptured);
                if (numCaptured > 0) {
                    ChessPieceView capturedPiece = new ChessPieceView(this, turnAt, piece, piece);
                    CapturedCountView capturedCountView = new CapturedCountView(this, numCaptured, piece);
                    if (myTurn == BoardConstants.WHITE) {
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
    }

    protected void updateTurnSwitchers() {
        final int currentTurn = jni.getTurn();

        switchTurnOpp.setVisibility(currentTurn == BoardConstants.BLACK && myTurn == BoardConstants.WHITE || currentTurn == BoardConstants.WHITE && myTurn == BoardConstants.BLACK ?  View.VISIBLE : View.INVISIBLE);
        switchTurnOpp.setDisplayedChild(currentTurn == BoardConstants.BLACK ? 0 : 1);

        switchTurnMe.setVisibility(currentTurn == BoardConstants.WHITE && myTurn == BoardConstants.WHITE || currentTurn == BoardConstants.BLACK && myTurn == BoardConstants.BLACK ?  View.VISIBLE : View.INVISIBLE);
        switchTurnMe.setDisplayedChild(currentTurn == BoardConstants.BLACK ? 0 : 1);
    }


    protected void toggleEngineProgress(boolean showProgress) {
        Log.d(TAG, "toggleEngineProgress " + showProgress);
        if (showProgress) {
            playButton.setVisibility(View.GONE);
            progressBarEngine.setVisibility(View.VISIBLE);
        } else {
            progressBarEngine.setVisibility(View.GONE);
            playButton.setVisibility(View.VISIBLE);
        }
    }

    protected void showChess960Dialog() {
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
                    lGameID = 0;
                    updateForNewGame();
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
            lGameID = 0;
            updateForNewGame();
            }
        });

        AlertDialog alert = builder.create();
        alert.show();
    }

    @Override
    public void OnDialogResult(int requestCode, Bundle data) {
        switch (requestCode) {
            case REQUEST_MENU:
                Intent intent;
                String item = data.getString("item");

                if (item.equals(getString(R.string.menu_game_settings))) {
                    GameSettingsDialog settingsDialog = new GameSettingsDialog(this, this, REQUEST_GAME_SETTINGS, getPrefs());
                    settingsDialog.show();
                } else if (item.equals(getString(R.string.menu_new))) {
                    gameApi.newGame();
                    lGameID = 0;
                    updateForNewGame();
                } else if (item.equals(getString(R.string.menu_new_duck))) {
                    gameApi.newGame(BoardConstants.VARIANT_DUCK);
                    lGameID = 0;
                    updateForNewGame();
                } else if (item.equals(getString(R.string.menu_new_960))) {
                    showChess960Dialog();
                } else if (item.equals(getString(R.string.menu_setup))) {
                    intent = new Intent();
                    intent.setClass(PlayActivity.this, jwtc.android.chess.setup.SetupActivity.class);
                    startActivityForResult(intent, REQUEST_SETUP);
                } else if (item.equals(getString(R.string.menu_save_game))) {
                    saveGame();
                } else if (item.equals(getString(R.string.menu_load_game))) {
                    intent = new Intent();
                    intent.setClass(PlayActivity.this, GamesListActivity.class);
                    startActivityForResult(intent, PlayActivity.REQUEST_OPEN);
                } else if (item.equals(getString(R.string.menu_prefs))) {
                    intent = new Intent();
                    intent.setClass(PlayActivity.this, GlobalPreferencesActivity.class);
                    startActivity(intent);
                } else if (item.equals(getString(R.string.menu_set_clock))) {
                    final ClockDialog menuDialog = new ClockDialog(this, this, REQUEST_CLOCK, getPrefs());
                    menuDialog.show();
                } else if (item.equals(getString(R.string.menu_fromclip))) {
                    String s = Clipboard.getStringFromClipboard(this);
                    if (gameApi.loadPGN(s)) {
                        lGameID = 0;
                        updateForNewGame();
                    } else {
                        if (gameApi.initFEN(s, true)) {
                            lGameID = 0;
                            updateForNewGame();
                        }
                    }
                } else if (item.equals(getString(R.string.menu_clip_pgn))) {
                    Clipboard.stringToClipboard(this, gameApi.exportFullPGN(), getString(R.string.copied_clipboard_success));
                }

                break;
            case REQUEST_CLOCK:
                updateClockByPrefs();
                updateGUI();
                break;

            case REQUEST_GAME_SETTINGS:
                updateGameSettingsByPrefs();
                updateGUI();
                break;

            case REQUEST_SAVE_GAME:
                saveGameFromDialog(data);
                break;
        }
    }

    @Override
    public void OnClockTime() {
        textViewOpponentClock.setText(myTurn == BoardConstants.WHITE ? localClock.getBlackRemainingTime() : localClock.getWhiteRemainingTime());
        textViewMyClock.setText(myTurn == BoardConstants.BLACK ? localClock.getBlackRemainingTime() : localClock.getWhiteRemainingTime());

//        long remaining = myTurn == BoardConstants.WHITE ? localClock.getWhiteRemaining() : localClock.getBlackRemaining();
//        if (remaining < _TimeWarning * 1000) {
//            textViewMyClock.setBackgroundColor(0xCCFF0000);
//            // @TODO spSound.play(soundTickTock, fVolume, fVolume, 1, 0, 1);
//        } else {
//            textViewMyClock.setBackgroundColor(Color.TRANSPARENT);
//        }
    }

    protected void updateClockByPrefs() {
        SharedPreferences prefs = getPrefs();
        long increment = prefs.getLong("clockIncrement", 0);
        long whiteRemaining = prefs.getLong("clockWhiteMillies", 0);
        long blackRemaining = prefs.getLong("clockBlackMillies", 0);
        long startTime = prefs.getLong("clockStartTime", 0);
        localClock.startClock(increment, whiteRemaining, blackRemaining, jni.getTurn(), startTime);
    }

    protected void updateForNewGame() {
        SharedPreferences prefs = getPrefs();
        long increment = prefs.getLong("clockIncrement", 0);
        long whiteRemaining = prefs.getLong("clockWhiteMillies", 0);
        long blackRemaining = prefs.getLong("clockBlackMillies", 0);
        long startTime = prefs.getLong("clockStartTime", 0);
        if (startTime > 0) {
            startTime = System.currentTimeMillis();
        }
        localClock.startClock(increment, whiteRemaining, blackRemaining, jni.getTurn(), startTime);

        if (spSound != null) {
            spSound.play(soundNewGame, fVolume, fVolume, 1, 0, 1);
        }

        resetSelectedSquares();
    }

    protected void updateGameSettingsByPrefs() {
        SharedPreferences prefs = getPrefs();

        vsCPU = prefs.getBoolean("opponent", true);
        myTurn = prefs.getBoolean("myTurn", true) ? 1 : 0;

        int mode = prefs.getInt("levelMode", EngineApi.LEVEL_TIME);

        int levelTime = prefs.getInt("level", 2);
        int levelPly = prefs.getInt("levelPly", 2);
        int secs[] = {1, 1, 2, 4, 8, 10, 20, 30, 60, 300, 900, 1800}; // 1 offset, so 3 extra 1 unused secs

        if (mode == EngineApi.LEVEL_TIME) {
            myEngine.setMsecs(secs[levelTime] * 1000);
        } else {
            myEngine.setPly(levelPly);
        }

        myEngine.setQuiescentSearchOn(prefs.getBoolean("quiescentSearchOn", true));

        chessBoardView.setRotated(myTurn == BoardConstants.BLACK);

        playIfEngineMove();
    }


    public void saveGame() {
        String sEvent = gameApi.getPGNHeadProperty("Event");
        if (sEvent == null)
            sEvent = getString(R.string.savegame_event_question);
        String sWhite = gameApi.getWhite();
        if (sWhite == null)
            sWhite = getString(R.string.savegame_white_question);
        String sBlack = gameApi.getBlack();
        if (sBlack == null)
            sBlack = getString(R.string.savegame_black_question);

        Date dd = gameApi.getDate();
        if (dd == null)
            dd = Calendar.getInstance().getTime();

        Calendar cal = Calendar.getInstance();
        cal.setTime(dd);

        SaveGameDialog saveDialog = new SaveGameDialog(this, this, REQUEST_SAVE_GAME, sEvent, sWhite, sBlack, cal, gameApi.exportFullPGN(), lGameID > 0);
        saveDialog.show();
    }


    protected void saveGameFromDialog(Bundle data) {

        ContentValues values = new ContentValues();
        boolean bCopy = data.getBoolean("copy");

        values.put(PGNColumns.DATE, data.getLong(PGNColumns.DATE));
        values.put(PGNColumns.WHITE, data.getString(PGNColumns.WHITE));
        values.put(PGNColumns.BLACK, data.getString(PGNColumns.BLACK));
        values.put(PGNColumns.PGN, data.getString(PGNColumns.PGN));
        values.put(PGNColumns.RATING, data.getFloat(PGNColumns.RATING));
        values.put(PGNColumns.EVENT, data.getString(PGNColumns.EVENT));

        saveGame(values, bCopy);
    }

    protected void loadGame() {
        if (lGameID > 0) {
            Uri uri = ContentUris.withAppendedId(MyPGNProvider.CONTENT_URI, lGameID);
            Cursor c = managedQuery(uri, PGNColumns.COLUMNS, null, null, null);
            if (c != null && c.getCount() == 1) {

                c.moveToFirst();

                lGameID = c.getLong(c.getColumnIndex(PGNColumns._ID));
                String sPGN = c.getString(c.getColumnIndex(PGNColumns.PGN));
                gameApi.loadPGN(sPGN);

                gameApi.setPGNHeadProperty("Event", c.getString(c.getColumnIndex(PGNColumns.EVENT)));
                gameApi.setPGNHeadProperty("White", c.getString(c.getColumnIndex(PGNColumns.WHITE)));
                gameApi.setPGNHeadProperty("Black", c.getString(c.getColumnIndex(PGNColumns.BLACK)));
                gameApi.setDateLong(c.getLong(c.getColumnIndex(PGNColumns.DATE)));

            } else {
                lGameID = 0; // probably deleted
            }
        } else {
            lGameID = 0;
        }
    }

    protected void saveGame(ContentValues values, boolean bCopy) {

        SharedPreferences.Editor editor = this.getPrefs().edit();
        editor.putString("FEN", null);
        editor.commit();

        gameApi.setPGNHeadProperty("Event", (String) values.get(PGNColumns.EVENT));
        gameApi.setPGNHeadProperty("White", (String) values.get(PGNColumns.WHITE));
        gameApi.setPGNHeadProperty("Black", (String) values.get(PGNColumns.BLACK));
        gameApi.setDateLong((Long) values.get(PGNColumns.DATE));

        if (lGameID > 0 && (bCopy == false)) {
            Uri uri = ContentUris.withAppendedId(MyPGNProvider.CONTENT_URI, lGameID);
            getContentResolver().update(uri, values, null, null);
        } else {
            Uri uri = MyPGNProvider.CONTENT_URI;
            Uri uriInsert = getContentResolver().insert(uri, values);
            Cursor c = managedQuery(uriInsert, new String[]{PGNColumns._ID}, null, null, null);
            if (c != null && c.getCount() == 1) {
                c.moveToFirst();
                lGameID = c.getLong(c.getColumnIndex(PGNColumns._ID));
            }
        }
    }

    protected void playIfEngineMove() {
        Log.d(TAG, "playIfEngineMove " + myTurn + " vs " + jni.getTurn() + " vsCPU " + vsCPU);
        if (myTurn != jni.getTurn() && vsCPU) {
            playIfEngineCanMove();
        }
    }

    protected void playIfEngineCanMove() {
        Log.d(TAG, "playIfEngineCanMove t " + jni.getTurn() + " myt " + myTurn + " duck " + jni.getDuckPos() + " - " + jni.getMyDuckPos());
        if (myEngine.isReady() && jni.isEnded() == 0 && (jni.getDuckPos() == -1 || jni.getDuckPos() != -1 && jni.getMyDuckPos() != -1)) {
            myEngine.play();
        }
    }
}
