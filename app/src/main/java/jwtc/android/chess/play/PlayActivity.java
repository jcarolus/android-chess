package jwtc.android.chess.play;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.switchmaterial.SwitchMaterial;

import org.json.JSONArray;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import jwtc.android.chess.GamesListActivity;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.helpers.Clipboard;
import jwtc.android.chess.helpers.MoveRecyclerAdapter;
import jwtc.android.chess.helpers.MyPGNProvider;
import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.constants.PieceSets;
import jwtc.android.chess.engine.EngineApi;
import jwtc.android.chess.engine.EngineListener;
import jwtc.android.chess.engine.LocalEngine;
import jwtc.android.chess.helpers.PGNHelper;
import jwtc.android.chess.helpers.ResultDialogListener;
import jwtc.android.chess.helpers.Utils;
import jwtc.android.chess.services.ClockListener;
import jwtc.android.chess.services.EcoService;
import jwtc.android.chess.services.GameApi;
import jwtc.android.chess.services.LocalClockApi;
import jwtc.android.chess.views.CapturedCountView;
import jwtc.android.chess.views.ChessPieceView;
import jwtc.android.chess.views.ChessPiecesStackView;
import jwtc.android.chess.views.ChessSquareView;
import jwtc.chess.Move;
import jwtc.chess.PGNColumns;
import jwtc.chess.board.BoardConstants;


public class PlayActivity extends ChessBoardActivity implements
    EngineListener,
    ResultDialogListener<Bundle>,
    ClockListener, MoveRecyclerAdapter.OnItemClickListener {
    private static final String TAG = "PlayActivity";
    public static final int REQUEST_SETUP = 1;
    public static final int REQUEST_OPEN = 2;
    public static final int REQUEST_GAME_SETTINGS = 3;
    public static final int REQUEST_FROM_QR_CODE = 4;
    public static final int REQUEST_MENU = 5;
    public static final int REQUEST_CLOCK = 6;
    public static final int REQUEST_ECO = 7;
    public static final int REQUEST_RANDOM_FISCHER = 8;
    public static final int REQUEST_SAVE_GAME_TO_FILE = 9;
    public static final int REQUEST_SAVE_POSITION_TO_FILE = 10;
    public static final int REQUEST_OPEN_POSITION_FILE = 11;
    public static final int REQUEST_OPEN_GAME_FILE = 12;

    private LocalClockApi localClock;
    private EngineApi myEngine;
    private final EcoService ecoService = new EcoService();
    private long lGameID;
    private LinearProgressIndicator progressBarEngine;
    private MaterialButton playButton;
    private boolean vsCPU = true;
    private boolean flipBoard = false;
    private int myTurn = 1;
    private ChessPiecesStackView topPieces;
    private ChessPiecesStackView bottomPieces;
    private ImageView imageBottomTurn, imageTopTurn, imageTurnWhite, imageTurnBlack;
    private TextView textViewTopPlayer, textViewBottomPlayer, textViewTopClockTime, textViewBottomClockTime, textViewWhitePlayer, textViewBlackPlayer, textViewWhiteClockTIme, textViewBlackClockTime;
    private TextView textViewLastMove, textViewEco, textViewWhitePieces, textViewBlackPieces;
    private TextView textViewInfoBalloon, textViewEngineValue;
    private MaterialButton buttonEco;
    private SwitchMaterial switchSound, switchBlindfold, switchFlip, switchMoveToSpeech, switchAccessibilityDrag;
    private MoveRecyclerAdapter moveAdapter;
    private RecyclerView historyRecyclerView;
    private PopupWindow popupWindowInfoBalloon;

    @Override
    public boolean requestMove(final int from, final int to) {
        if (!gameApi.isEnded() && (jni.getTurn() == myTurn || vsCPU == false)) {
            // @TODO check if override new line

            /*
            openConfirmDialog(
                            getString(R.string.title_create_new_line),
                            getString(R.string.alert_yes),
                            getString(R.string.alert_no),
                            this::playIfEngineCanMove,
                            null
                    );
             */

            if (super.requestMove(from, to)) {
                return true;
            }
        }
        rebuildBoard();
        return false;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.play);

        ActivityHelper.fixPaddings(this, findViewById(R.id.root_layout));

        gameApi = new GameApi();
        localClock = new LocalClockApi(gameApi);

        localClock.addListener(this);

        afterCreate();

        playButton = findViewById(R.id.ButtonPlay);
        playButton.setOnClickListener(arg0 -> {
            if (!gameApi.isEnded()) {
                if (jni.getNumBoard() < gameApi.getPGNSize()) {
                    openConfirmDialog(
                        getString(R.string.title_create_new_line),
                        getString(R.string.alert_yes),
                        getString(R.string.alert_no),
                        this::playIfEngineCanMove,
                        null
                    );
                } else {
                    playIfEngineCanMove();
                }
            }
        });

        progressBarEngine = findViewById(R.id.ProgressBarPlay);

        final MenuDialog menuDialog = new MenuDialog(this, this, REQUEST_MENU);

        MaterialButton buttonMenu = findViewById(R.id.ButtonMenu);
        buttonMenu.setOnClickListener(v -> menuDialog.show());

        topPieces = findViewById(R.id.topPieces);
        bottomPieces = findViewById(R.id.bottomPieces);

        MaterialButton butNext = findViewById(R.id.ButtonNext);
        butNext.setOnClickListener(v -> gameApi.nextMove());
        butNext.setOnLongClickListener(v -> {
            gameApi.jumpToBoardNum(gameApi.getPGNSize());
            return true;
        });

        MaterialButton butPrev = findViewById(R.id.ButtonPrevious);
        butPrev.setOnClickListener(v -> gameApi.undoMove());

        butPrev.setOnLongClickListener(v -> {
            gameApi.jumpToBoardNum(1);
            return true;
        });

//        ImageButton butPgn = findViewById(R.id.ButtonPGN);
//        butPgn.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                PGNDialog dialog = new PGNDialog(PlayActivity.this, gameApi);
//                dialog.show();
//            }
//        });

        imageBottomTurn = findViewById(R.id.ImageBottomTurn);
        imageTopTurn = findViewById(R.id.ImageTopTurn);
        textViewTopPlayer = findViewById(R.id.TextViewTopPlayer);
        textViewBottomPlayer = findViewById(R.id.TextViewBottomPlayer);
        textViewTopClockTime = findViewById(R.id.TextViewTopClockTime);
        textViewBottomClockTime = findViewById(R.id.TextViewBottomClockTime);

        // default rotation
        imageTurnWhite = imageBottomTurn;
        imageTurnBlack = imageTopTurn;
        textViewWhitePlayer = textViewBottomPlayer;
        textViewBlackPlayer = textViewTopPlayer;
        textViewWhiteClockTIme = textViewBottomClockTime;
        textViewBlackClockTime = textViewTopClockTime;

        textViewLastMove = findViewById(R.id.TextViewLastMove);
        buttonEco = findViewById(R.id.ButtonEco);
        textViewEco = findViewById(R.id.TextViewEco);
        textViewWhitePieces = findViewById(R.id.TextViewWhitePieces);
        textViewBlackPieces = findViewById(R.id.TextViewBlackPieces);
        textViewEngineValue = findViewById(R.id.TextViewEngineValue);

        switchSound = findViewById(R.id.SwitchSound);
        switchSound.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (sounds != null) {
                sounds.setEnabled(switchSound.isChecked());
            }
        });

        switchBlindfold = findViewById(R.id.SwitchBlindfold);
        switchBlindfold.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (switchBlindfold.isChecked()) {
                PieceSets.selectedBlindfoldMode = PieceSets.BLINDFOLD_HIDE_PIECES;
                rebuildBoard();
                topPieces.setVisibility(View.INVISIBLE);
                bottomPieces.setVisibility(View.INVISIBLE);
            } else {
                PieceSets.selectedBlindfoldMode = PieceSets.BLINDFOLD_SHOW_PIECES;
                rebuildBoard();
                topPieces.setVisibility(View.VISIBLE);
                bottomPieces.setVisibility(View.VISIBLE);
                topPieces.invalidatePieces();
                bottomPieces.invalidatePieces();
            }
        });

        switchFlip = findViewById(R.id.SwitchFlip);
        switchFlip.setOnCheckedChangeListener((buttonView, isChecked) -> {
            flipBoard = switchFlip.isChecked();
            updateBoardRotation();
        });

        switchMoveToSpeech = findViewById(R.id.SwitchSpeech);
        switchMoveToSpeech.setOnCheckedChangeListener((buttonView, isChecked) -> moveToSpeech = switchMoveToSpeech.isChecked());

        switchAccessibilityDrag = findViewById(R.id.SwitchAccessibilityDrag);
        switchAccessibilityDrag.setOnCheckedChangeListener((buttonView, isChecked) -> {
            useAccessibilityDrag = switchAccessibilityDrag.isChecked();
            applySquareDragListeners();
        });

        historyRecyclerView = findViewById(R.id.HistoryRecyclerView);

        // Horizontal layout manager
        LinearLayoutManager layoutManager =
            new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        historyRecyclerView.setLayoutManager(layoutManager);

        // Set adapter
        moveAdapter = new MoveRecyclerAdapter(this, gameApi, this);
        historyRecyclerView.setAdapter(moveAdapter);
        historyRecyclerView.setHorizontalScrollBarEnabled(true);

        ecoService.load(getAssets());
    }

    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences prefs = getPrefs();

        String sPGN = prefs.getString("game_pgn", null);
        String sFEN = prefs.getString("FEN", null);
        final Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();
        Uri uri = intent.getData();

        myEngine = new LocalEngine(gameApi);
        myEngine.addListener(this);

        lGameID = prefs.getLong("game_id", 0);

        Log.d(TAG, "onResume => " + lGameID + " " + (sPGN != null ? "PGN " : " ") + (sFEN != null ? "FEN" : ""));

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            lGameID = 0;
            Log.i("onResume", "action send with type " + type);
            if ("application/x-chess-pgn".equals(type)) {
                sPGN = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (sPGN != null) {
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
        } else {
            if (lGameID > 0) {
                Log.i("onResume", "loading saved game " + lGameID);
                loadGame();
            } else if (sPGN != null) {
                Log.i("onResume", "pgn: " + sPGN);
                gameApi.loadPGN(sPGN);
            } else if (sFEN != null) {
                // default, from prefs
                Log.i("onResume", "FEN: " + sFEN);
                lGameID = 0;
                gameApi.initFEN(sFEN, true);
                updateForNewGame();
            } else {
                gameApi.newGame();
                updateForNewGame();
            }
        }

        updateClockByPrefs(false);
        updateGameSettingsByPrefs();

        switchSound.setChecked(prefs.getBoolean("moveSounds", false));
        switchBlindfold.setChecked(false);

        buttonEco.setEnabled(false);

        new Handler(Looper.getMainLooper()).postDelayed(
            this::updateGUI,
            1000
        );
    }


    @Override
    protected void onPause() {
        //Debug.stopMethodTracing();

        myEngine.abort();
        myEngine.removeListener(this);

        localClock.stopClock();

        SharedPreferences.Editor editor = this.getPrefs().edit();

        if (lGameID > 0) {
            ContentValues values = new ContentValues();

            // @TODO - generic solution; +RESULT?
            values.put(PGNColumns.DATE, gameApi.getDate().getTime());
            values.put(PGNColumns.WHITE, gameApi.getWhite());
            values.put(PGNColumns.BLACK, gameApi.getBlack());
            values.put(PGNColumns.PGN, gameApi.exportFullPGN());
            values.put(PGNColumns.EVENT, gameApi.getPGNHeadProperty("Event"));

            saveGame(values, false, lGameID);

            editor.putString("FEN", null);
        }

        editor.putLong("game_id", lGameID);
        editor.putString("game_pgn", gameApi.exportFullPGN());
        editor.putString("FEN", jni.toFEN());

        final long pauseTime = System.currentTimeMillis();
        editor.putLong("clockWhiteMillies", localClock.getWhiteRemaining());
        editor.putLong("clockBlackMillies", localClock.getBlackRemaining());
        editor.putLong("clockStartTime", gameApi.isEnded() ? 0 : pauseTime);

        editor.putBoolean("flipBoard", flipBoard);
        editor.putBoolean("moveToSpeech", moveToSpeech);
        editor.putBoolean("useAccessibilityDrag", useAccessibilityDrag);

        editor.putBoolean("moveSounds", sounds.getEnabled());


//         if (_uriNotification == null)
//            editor.putString("NotificationUri", null);
//        else
//            editor.putString("NotificationUri", _uriNotification.toString());

        editor.commit();

        super.onPause();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(TAG, "onActivityResult " + requestCode + ", " + resultCode);

        if (data != null) {
            final Uri uri = data.getData();
            if (requestCode == REQUEST_OPEN) {
                if (resultCode == RESULT_OK) {
                    if (uri != null) {
                        lGameID = 0;
                        String lastSegment = uri.getLastPathSegment();
                        if (lastSegment != null) {
                            try {
                                lGameID = Long.parseLong(lastSegment);
                            } catch (Exception ignored) {
                            }
                        }
                        SharedPreferences.Editor editor = this.getPrefs().edit();
                        editor.putLong("game_id", lGameID);
                        editor.putInt("boardNum", 0);
                        editor.putString("FEN", null);
                        editor.commit();
                    }
                }
            } else if (requestCode == REQUEST_FROM_QR_CODE) {
                if (resultCode == RESULT_OK) {
                    String contents = data.getStringExtra("SCAN_RESULT");
                    //String format = data.getStringExtra("SCAN_RESULT_FORMAT");

                    SharedPreferences.Editor editor = this.getPrefs().edit();
                    editor.putLong("game_id", 0);
                    editor.putInt("boardNum", 0);
                    editor.putString("FEN", contents);
                    editor.putString("game_pgn", null);
                    editor.commit();
                }
            } else if (requestCode == REQUEST_SAVE_GAME_TO_FILE) {
                if (uri != null) {
                    saveToFile(uri, gameApi.exportFullPGN());
                }
            } else if (requestCode == REQUEST_SAVE_POSITION_TO_FILE) {
                if (uri != null) {
                    saveToFile(uri, gameApi.getFEN());
                }
            } else if (requestCode == REQUEST_OPEN_POSITION_FILE) {
                if (uri != null) {
                    String sFEN = readInputStream(uri, 1000);
                    Log.d(TAG, "got FEN " + sFEN);

                    SharedPreferences.Editor editor = this.getPrefs().edit();
                    editor.putLong("game_id", 0);
                    editor.putInt("boardNum", 0);
                    editor.putString("FEN", sFEN);
                    editor.putString("game_pgn", null);
                    editor.commit();
                }
            } else if (requestCode == REQUEST_OPEN_GAME_FILE) {
                if (uri != null) {
                    String sPGN = readInputStream(uri, GameApi.MAX_PGN_SIZE);
                    Log.d(TAG, "got PGN " + sPGN);

                    SharedPreferences.Editor editor = this.getPrefs().edit();
                    editor.putLong("game_id", 0);
                    editor.putInt("boardNum", 0);
                    editor.putString("FEN", null);
                    editor.putString("game_pgn", sPGN);
                    editor.commit();
                }
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
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
    public void OnEngineMove(int move, int duckMove, int value) {
        toggleEngineProgress(false);
        hideInfoBalloon();

        gameApi.move(move, duckMove);
        lastMoveFrom = Move.getFrom(move);
        lastMoveTo = Move.getTo(move);
        highlightedPositions.clear();

        updateGUI();
    }

    @Override
    public void OnEngineInfo(String message, float value) {
        textViewEngineValue.setText(String.format("%.1f", value));
        if (textViewInfoBalloon != null && textViewInfoBalloon.getParent() != null) {
            textViewInfoBalloon.setText(message);
        }
    }

    @Override
    public void OnEngineStarted() {
        toggleEngineProgress(true);
        Log.d(TAG, "on engine started");
        // showInfoBalloon(textViewEngineValue);
    }

    @Override
    public void OnEngineAborted() {
        toggleEngineProgress(false);
        // hideInfoBalloon();
    }

    @Override
    public void OnEngineError() {
        toggleEngineProgress(false);
        // hideInfoBalloon();
        // textViewEngineValue.setText("Engine error!");
    }

    @Override
    public void OnState() {
        super.OnState();

        updateGUI();
    }

    @Override
    public void onMoveItemClick(int pos) {
        this.gameApi.jumpToBoardNum(pos + 1);
    }

    protected void updateGUI() {
        updateSelectedSquares();
        updateCapturedPieces();
        updateSeekBar();
        updateTurnSwitchers();
        updatePlayers();
        updateLastMove();
        updateEco();
    }

    protected void updateLastMove() {
        final int state = chessStateToR(gameApi.getState());
        String sState = "";
        // in play or mate are clear from last move.
        if (state != R.string.state_play && state != R.string.state_mate && state != R.string.state_check) {
            sState = ". " + getString(state);
        }
        textViewLastMove.setText(getLastMoveAndTurnDescription() + sState);
        textViewWhitePieces.setText(getPiecesDescription(BoardConstants.WHITE));
        textViewBlackPieces.setText(getPiecesDescription(BoardConstants.BLACK));
    }

    protected void updateSeekBar() {
        moveAdapter.update();
        historyRecyclerView.scrollToPosition(jni.getNumBoard() - 1);
    }

    protected void updatePlayers() {
        textViewWhitePlayer.setText(gameApi.getWhite());
        textViewBlackPlayer.setText(gameApi.getBlack());
    }

    protected void updateEco() {
        String ecoName = ecoService.getEcoNameByHash(jni.getHashKey());
        JSONArray jArray = ecoService.getAvailable();
        Log.d(TAG, "eco by hash " + ecoName + " :: " + jArray.length());

        if (ecoName == null && jArray.length() > 0) {
            ecoName = "ECO Openings";
        }

        if (ecoName == null) {
            buttonEco.setEnabled(false);
            textViewEco.setText("");
        } else {
            textViewEco.setText(ecoName);

            if (jArray != null && jArray.length() > 0) {
                final String title = ecoName;
                buttonEco.setEnabled(true);
                buttonEco.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        EcoDialog dialog = new EcoDialog(PlayActivity.this, PlayActivity.this, REQUEST_ECO, title, jArray);
                        dialog.show();
                    }
                });
            } else {
                buttonEco.setEnabled(false);
            }
        }
    }

    protected void updateCapturedPieces() {
        topPieces.removeAllViews();
        bottomPieces.removeAllViews();

        int piece, turnAt;
        for (turnAt = 0; turnAt < 2; turnAt++) {
            for (piece = 0; piece < 5; piece++) {
                ChessSquareView square = new ChessSquareView(this, piece);
                if (turnAt == BoardConstants.WHITE) {
                    topPieces.addView(square);
                } else {
                    bottomPieces.addView(square);
                }

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

        imageTurnWhite.setImageResource(currentTurn == BoardConstants.WHITE
            ? R.drawable.turnwhite
            : R.drawable.turnempty
        );

        imageTurnBlack.setImageResource(currentTurn == BoardConstants.BLACK
            ? R.drawable.turnblack
            : R.drawable.turnempty
        );
    }


    protected void toggleEngineProgress(boolean showProgress) {
        Log.d(TAG, "toggleEngineProgress " + showProgress);
        if (showProgress) {
            playButton.setIconResource(R.drawable.box_arrow_up_right);
            //playButton.setVisibility(View.GONE);
            progressBarEngine.setVisibility(View.VISIBLE);
        } else {
            playButton.setIconResource(R.drawable.ic_robot);
            progressBarEngine.setVisibility(View.INVISIBLE);
            //playButton.setVisibility(View.VISIBLE);
        }
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
                    intent = new Intent();
                    intent.setClass(PlayActivity.this, jwtc.android.chess.setup.SetupRandomFischerActivity.class);
                    startActivityForResult(intent, REQUEST_RANDOM_FISCHER);
                } else if (item.equals(getString(R.string.menu_setup))) {
                    intent = new Intent();
                    intent.setClass(PlayActivity.this, jwtc.android.chess.setup.SetupActivity.class);
                    startActivityForResult(intent, REQUEST_SETUP);
                } else if (item.equals(getString(R.string.menu_save_game))) {
                    saveGame();
                } else if (item.equals(getString(R.string.menu_load_game))) {
                    intent = new Intent();
                    intent.setClass(PlayActivity.this, GamesListActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
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
                } else if (item.equals(getString(R.string.menu_save_game_to_file))) {
                    startIntentForSaveDocument("application/x-chess-pgn", "game.pgn", REQUEST_SAVE_GAME_TO_FILE);
                } else if (item.equals(getString(R.string.menu_save_position_to_file))) {
                    startIntentForSaveDocument("application/x-chess-fen", "position.fen", REQUEST_SAVE_POSITION_TO_FILE);
                } else if (item.equals(getString(R.string.menu_open_position_file))) {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(i, REQUEST_OPEN_POSITION_FILE);
                } else if (item.equals(getString(R.string.menu_open_game))) {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(i, REQUEST_OPEN_GAME_FILE);
                }

                break;
            case REQUEST_CLOCK:
                updateClockByPrefs(false);
                gameApi.resetForfeitTime();
                break;

            case REQUEST_GAME_SETTINGS:
                updateGameSettingsByPrefs();
                updateGUI();
                break;

            case REQUEST_ECO:
                item = data.getString("item");
                gameApi.requestMove(item);
                break;
        }
    }

    @Override
    public void OnClockTime() {
        textViewWhiteClockTIme.setText(localClock.getWhiteRemainingTime());
        textViewBlackClockTime.setText(localClock.getBlackRemainingTime());

        if (localClock.isClockConfigured()) {
            long white = localClock.getWhiteRemaining();
            long black = localClock.getBlackRemaining();
            if (white <= 0 || black <= 0) {
                localClock.stopClock();
                if (white <= 0) {
                    gameApi.setFinalState(BoardConstants.WHITE_FORFEIT_TIME);
                } else {
                    gameApi.setFinalState(BoardConstants.BLACK_FORFEIT_TIME);
                }
            }
        }

//        long remaining = myTurn == BoardConstants.WHITE ? localClock.getWhiteRemaining() : localClock.getBlackRemaining();
//        if (remaining < _TimeWarning * 1000) {
//            textViewMyClock.setBackgroundColor(0xCCFF0000);
//            // @TODO spSound.play(soundTickTock, fVolume, fVolume, 1, 0, 1);
//        } else {
//            textViewMyClock.setBackgroundColor(Color.TRANSPARENT);
//        }
    }

    protected void updateClockByPrefs(boolean newGame) {
        SharedPreferences prefs = getPrefs();
        long increment = prefs.getLong("clockIncrement", 0);
        long whiteRemaining = prefs.getLong("clockWhiteMillies", 0);
        long blackRemaining = prefs.getLong("clockBlackMillies", 0);
        if (newGame) {
            long initialMillis = prefs.getLong("clockInitialMillies", 0);
            whiteRemaining = initialMillis;
            blackRemaining = initialMillis;
        }
        long startTime = prefs.getLong("clockStartTime", 0);
        if (startTime > 0 && newGame) {
            startTime = System.currentTimeMillis();
        }
        localClock.startClock(increment, whiteRemaining, blackRemaining, jni.getTurn(), startTime);
    }

    protected void updateForNewGame() {
        Log.d(TAG, "updateForNewGame");

        updateClockByPrefs(true);

        if (sounds != null) {
            sounds.playNewGame();
        }

        resetSelectedSquares();
        updateLastMove();
    }

    protected void updateBoardRotation() {
        boolean isRotated = flipBoard && myTurn == BoardConstants.WHITE || !flipBoard && myTurn == BoardConstants.BLACK;
        chessBoardView.setRotated(isRotated);

        imageTurnWhite = isRotated ? imageTopTurn : imageBottomTurn;
        imageTurnBlack = isRotated ? imageBottomTurn : imageTopTurn;
        textViewWhitePlayer = isRotated ? textViewTopPlayer : textViewBottomPlayer;
        textViewBlackPlayer = isRotated ? textViewBottomPlayer : textViewTopPlayer;
        textViewWhiteClockTIme = isRotated ? textViewTopClockTime : textViewBottomClockTime;
        textViewBlackClockTime = isRotated ? textViewBottomClockTime : textViewTopClockTime;
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

        flipBoard = prefs.getBoolean("flipBoard", false);

        switchFlip.setChecked(flipBoard);
        switchMoveToSpeech.setChecked(moveToSpeech);
        switchAccessibilityDrag.setChecked(useAccessibilityDrag);

        updateBoardRotation();
        updateLastMove();

        playIfEngineMove();
    }

    public void saveGame() {
        SaveGameDialog saveDialog = new SaveGameDialog(this, gameApi, lGameID, this::saveGameFromDialog);
        saveDialog.show();
    }

    @Override
    protected void saveGameFromDialog(SaveGameDialog.SaveGameResult result) {
        super.saveGameFromDialog(result);
        if (!result.createCopy) {
            loadGame();
        }
    }

    protected void loadGame() {
        if (lGameID > 0) {
            Uri uri = ContentUris.withAppendedId(MyPGNProvider.CONTENT_URI, lGameID);
            try {
                Cursor c = getContentResolver().query(uri, PGNColumns.COLUMNS, null, null, null);
                if (c != null && c.getCount() == 1) {

                    c.moveToFirst();

                    lGameID = Utils.getColumnLong(c, PGNColumns._ID);
                    String sPGN = Utils.getColumnString(c, PGNColumns.PGN);

                    gameApi.loadPGN(sPGN);

                    gameApi.setPGNTag("Event", Utils.getColumnString(c, PGNColumns.EVENT));
                    gameApi.setPGNTag("White", Utils.getColumnString(c, PGNColumns.WHITE));
                    gameApi.setPGNTag("Black", Utils.getColumnString(c, PGNColumns.BLACK));
                    gameApi.setDateLong(Utils.getColumnLong(c, PGNColumns.DATE));

                    c.close();

                } else {
                    Log.d(TAG, "Game not found: " + lGameID);
                    lGameID = 0; // probably deleted
                }
            } catch (Exception e) {
                Log.d(TAG, "Caught exception loading game: " + lGameID + " " + e.getMessage());
                lGameID = 0;
            }
        } else {
            lGameID = 0;
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
        if (myEngine.isReady() && !gameApi.isEnded() && (jni.getDuckPos() == -1 || jni.getDuckPos() != -1 && jni.getMyDuckPos() != -1)) {

            ArrayList<Integer> moves = ecoService.getAvailableMoves();

            if (jni.getVariant() != BoardConstants.VARIANT_DUCK && moves.size() > 0) {
                int r = (int) (Math.random() * moves.size());
                Log.d(TAG, "Eco moves " + moves.size() + ", " + r);
                gameApi.move(moves.get(r), -1);
                // textViewEngineValue.setText("From opening book");
            } else {
                myEngine.play();
            }
        }
    }

    protected void showInfoBalloon(View anchor) {
        View viewInfoBalloon = LayoutInflater.from(anchor.getContext()).inflate(R.layout.info_balloon, null);

        textViewInfoBalloon = viewInfoBalloon.findViewById(R.id.TextViewInfo);
        textViewInfoBalloon.setText("");

        popupWindowInfoBalloon = new PopupWindow(
            viewInfoBalloon,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        );

        popupWindowInfoBalloon.setOutsideTouchable(false);
        //popupWindowInfoBalloon.setElevation(10f);
        popupWindowInfoBalloon.setTouchable(false);
        popupWindowInfoBalloon.setClippingEnabled(false);
        popupWindowInfoBalloon.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);

        // Calculate anchor position
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);

        viewInfoBalloon.measure(
            View.MeasureSpec.UNSPECIFIED,
            View.MeasureSpec.UNSPECIFIED
        );

        int x = location[0] + anchor.getWidth();
        int y = location[1] /*+ (anchor.getHeight() / 2)
                - (viewInfoBalloon.getMeasuredHeight() / 2)*/;

        popupWindowInfoBalloon.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y);
    }

    protected void hideInfoBalloon() {
        if (popupWindowInfoBalloon != null && popupWindowInfoBalloon.isShowing()) {
            popupWindowInfoBalloon.dismiss();
        }
    }
}
