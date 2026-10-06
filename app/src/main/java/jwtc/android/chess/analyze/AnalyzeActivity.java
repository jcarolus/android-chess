package jwtc.android.chess.analyze;

import android.content.ContentUris;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.flexbox.FlexboxLayout;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.engine.EngineApi;
import jwtc.android.chess.engine.EngineListener;
import jwtc.android.chess.engine.LocalEngine;
import jwtc.android.chess.engine.OexEngine;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.helpers.EinkMode;
import jwtc.android.chess.helpers.MoveRecyclerAdapter;
import jwtc.android.chess.helpers.MyPGNProvider;
import jwtc.android.chess.helpers.Utils;
import jwtc.android.chess.services.EcoService;
import jwtc.android.chess.services.GameApi;
import jwtc.chess.PGNColumns;
import jwtc.chess.PGNEntry;
import jwtc.chess.Pos;
import jwtc.chess.Move;
import jwtc.chess.GameTree.Node;
import jwtc.chess.board.BoardConstants;
import jwtc.android.chess.views.EngineEvaluationView;

public class AnalyzeActivity extends ChessBoardActivity implements EngineListener {
    private static final String TAG = "AnalyzeActivity";
    private EngineApi myEngine;
    private EngineApi localEngine;
    private OexEngine oexEngine;
    private final EcoService ecoService = new EcoService();
    private long lGameID;
    private EngineEvaluationView evaluationView;
    private MoveRecyclerAdapter moveAdapter;
    private RecyclerView historyRecyclerView;
    private TextView textViewAnalysisLine;
    private ImageView imageAnalysisTurn;
    private TextView textViewLastMove;
    private TextView textViewEnginePreference;
    private final LruCache<String, EngineRecommendation> engineRecommendations = new LruCache<>(128);
    private final Map<Node, String> positionFens = new WeakHashMap<>();

    private static class EngineRecommendation {
        final int move;
        final String san;

        EngineRecommendation(int move, String san) {
            this.move = move;
            this.san = san;
        }
    }
    private View buttonBackToMain;
    private FlexboxLayout layoutVariations;
    private boolean analysisActive;
    private String analysisFen;
    private int analysisTurn;
    private int analysisGeneration;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.analyze);
        ActivityHelper.fixPaddings(this, findViewById(R.id.root_layout));

        evaluationView = findViewById(R.id.engine_evaluation);
        gameApi = new GameApi();
        layoutVariations = findViewById(R.id.LayoutVariations);
        imageAnalysisTurn = findViewById(R.id.ImageAnalysisTurn);
        textViewLastMove = findViewById(R.id.TextViewLastMove);
        textViewEnginePreference = findViewById(R.id.TextViewEnginePreference);
        textViewAnalysisLine = findViewById(R.id.TextViewAnalysisLine);
        buttonBackToMain = findViewById(R.id.ButtonBackToMain);
        buttonBackToMain.setOnClickListener(v -> gameApi.returnToMainLine());
        historyRecyclerView = findViewById(R.id.HistoryRecyclerView);
        historyRecyclerView.setLayoutManager(
            new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        moveAdapter = new MoveRecyclerAdapter(this, gameApi, position -> {
            java.util.List<jwtc.chess.GameTree.Node> line = gameApi.getCurrentLineNodes();
            if (position >= 0 && position < line.size()) {
                gameApi.goTo(line.get(position));
            }
        });
        historyRecyclerView.setAdapter(moveAdapter);
        historyRecyclerView.setHorizontalScrollBarEnabled(true);
        EinkMode.applyTo(historyRecyclerView);
        switchSound = findViewById(R.id.SwitchSound);
        switchMoveToSpeech = findViewById(R.id.SwitchSpeech);
        switchAccessibilityDrag = findViewById(R.id.SwitchAccessibilityDrag);
        textViewWhitePieces = findViewById(R.id.TextViewWhitePieces);
        textViewBlackPieces = findViewById(R.id.TextViewBlackPieces);
        afterCreate();
        chessBoardView.setNextFocusRightId(R.id.ButtonPrevious);

        initBoardLayoutSizing(findViewById(R.id.root_layout), findViewById(R.id.board_area),
            findViewById(R.id.analyze_controls), null, null, 24);
        findViewById(R.id.ButtonPrevious).setOnClickListener(v -> gameApi.undoMove());
        findViewById(R.id.ButtonNext).setOnClickListener(v -> gameApi.nextMove());

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

        localEngine = new LocalEngine(gameApi);
        oexEngine = new OexEngine(this, gameApi, prefs.getString("oexEngineId", null));
        oexEngine.setPreferredEngineId(getPrefs().getString("oexEngineId", null));

        myEngine = oexEngine; // @TODO
        myEngine.addListener(this);

        lGameID = prefs.getLong("game_id", 0);

        Log.d(TAG, "onResume => " + lGameID + " " + (sPGN != null ? "PGN " : " ") + (sFEN != null ? "FEN" : ""));
        if (lGameID > 0 && loadGame()) {
            Log.d(TAG, "Loaded game " + lGameID);
        } else if (sPGN != null) {
            gameApi.loadPGN(sPGN);
        } else if (sFEN != null) {
            gameApi.initFEN(sFEN, true);
        } else {
            gameApi.newGame();
        }

        // Start at the game's root, including custom FEN starting positions.
        gameApi.goTo(gameApi.getRootNode());
        analysisActive = true;
        analysisFen = null;
        engineRecommendations.evictAll();
        positionFens.clear();
        rebuildBoard();
        updatePieceDescriptions();
    }

    @Override
    public void rebuildBoard() {
        super.rebuildBoard();
        boolean whiteToMove = jni.getTurn() == BoardConstants.WHITE;
        imageAnalysisTurn.setImageResource(whiteToMove ? R.drawable.turnwhite : R.drawable.turnblack);
        imageAnalysisTurn.setContentDescription(getString(whiteToMove
            ? R.string.analysis_white_to_move : R.string.analysis_black_to_move));
        final int state = chessStateToR(gameApi.getState());
        String stateDescription = "";
        if (state != R.string.state_play && state != R.string.state_mate && state != R.string.state_check) {
            stateDescription = ". " + getString(state);
        }
        updateTextViewOrSpeech(textViewLastMove,
            getLastMoveAndTurnDescription(false) + stateDescription, protectLastMoveSpeech);
        boolean onMainLine = gameApi.isOnMainLine();
        textViewAnalysisLine.setText(onMainLine
            ? R.string.analysis_main_line : R.string.analysis_variation);
        buttonBackToMain.setVisibility(onMainLine ? View.INVISIBLE : View.VISIBLE);
        moveAdapter.update();
        historyRecyclerView.scrollToPosition(jni.getNumBoard() - 1);
        updateVariations();
        if (!analysisActive || myEngine == null || !myEngine.supportsAnalysis()) {
            return;
        }
        final String fen = jni.toFEN();
        positionFens.put(gameApi.getCurrentNode(), fen);
        if (fen.equals(analysisFen)) {
            return;
        }
        analysisFen = fen;
        textViewEnginePreference.setText("");
        textViewEnginePreference.setContentDescription(null);
        String parentFen = positionFens.get(gameApi.getCurrentNode().getParent());
        if (parentFen != null && jni.getVariant() == BoardConstants.VARIANT_DEFAULT) {
            showEnginePreference(engineRecommendations.get(parentFen));
        }
        final int turn = jni.getTurn();
        final int generation = ++analysisGeneration;
        evaluationView.clearEvaluation();
        myEngine.abort(() -> {
            if (analysisActive && generation == analysisGeneration) {
                analysisTurn = turn;
                myEngine.analyze(fen, 1000);
            }
        });
    }

    private void updateVariations() {
        layoutVariations.removeAllViews();
        List<Node> continuations = gameApi.getContinuations(gameApi.getCurrentNode());
        layoutVariations.setVisibility(continuations.size() > 1 ? View.VISIBLE : View.GONE);
        if (continuations.size() < 2) {
            return;
        }
        // Child zero is the preferred continuation already shown in the history.
        for (int i = 1; i < continuations.size(); i++) {
            Node continuation = continuations.get(i);
            PGNEntry entry = continuation.getEntry();
            String move = gameApi.getMoveNumber(continuation) + " " + entry.sMove;
            if (entry.duckMove != -1) {
                move += "@" + Pos.toString(entry.duckMove);
            }
            MaterialButton button = new MaterialButton(this);
            FlexboxLayout.LayoutParams params = new FlexboxLayout.LayoutParams(
                FlexboxLayout.LayoutParams.WRAP_CONTENT, FlexboxLayout.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(Math.round(4 * getResources().getDisplayMetrics().density));
            button.setLayoutParams(params);
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setText(move);
            button.setAllCaps(false);
            button.setOnClickListener(v -> gameApi.goTo(continuation));
            layoutVariations.addView(button);
        }
    }

    @Override
    public void OnEngineInfo(String message, float value) {
        // UCI scores are relative to the side to move in the searched position.
        if (analysisActive && message != null && message.contains(" score ")) {
            evaluationView.setEvaluation(analysisTurn == BoardConstants.WHITE ? value : -value);
        }
    }

    @Override
    public void OnEngineError() {
        evaluationView.clearEvaluation();
    }

    @Override
    public void onAnalysisComplete(String fen, String bestMove) {
        if (!analysisActive || !fen.equals(analysisFen) || !fen.equals(jni.toFEN())
            || jni.getVariant() != BoardConstants.VARIANT_DEFAULT
            || bestMove == null || !bestMove.matches("[a-h][1-8][a-h][1-8][qrbn]?")) return;
        try {
            int from = Pos.fromString(bestMove.substring(0, 2));
            int to = Pos.fromString(bestMove.substring(2, 4));
            int promotion = bestMove.length() == 5 ? "pnbrqk".indexOf(bestMove.charAt(4)) : -1;
            for (int i = 0; i < jni.getMoveArraySize(); i++) {
                int move = jni.getMoveArrayAt(i);
                if (Move.getFrom(move) != from || Move.getTo(move) != to
                    || (Move.isPromotionMove(move) ? Move.getPromotionPiece(move) : -1) != promotion) continue;
                jni.scratchSyncFromCurrent();
                if (jni.scratchMove(move) != 0) {
                    try {
                        engineRecommendations.put(fen,
                            new EngineRecommendation(move, jni.scratchGetMyMoveToString()));
                    } finally {
                        jni.scratchUndo();
                    }
                }
                return;
            }
        } catch (Exception ex) {
            Log.w(TAG, "Unable to cache engine recommendation", ex);
        }
    }

    private void showEnginePreference(EngineRecommendation recommendation) {
        if (recommendation == null) return;
        int lastMove = jni.getMyMove();
        boolean matches = Move.equalPositions(lastMove, recommendation.move)
            && Move.isPromotionMove(lastMove) == Move.isPromotionMove(recommendation.move)
            && (!Move.isPromotionMove(lastMove)
                || Move.getPromotionPiece(lastMove) == Move.getPromotionPiece(recommendation.move));
        textViewEnginePreference.setText(matches ? "★"
            : getString(R.string.analysis_engine_prefers, recommendation.san));
        textViewEnginePreference.setContentDescription(matches
            ? getString(R.string.analysis_engine_matches) : null);
    }

    @Override
    public void OnEngineMove(int move, int duckMove, int value) {
        // Analysis must never play the engine's suggested move.
    }

    @Override
    public void OnEngineStarted() {}

    @Override
    public void OnEngineAborted() {}

    @Override
    protected void onPause() {
        analysisActive = false;
        analysisGeneration++;
        if (myEngine != null) {
            myEngine.removeListener(this);
        }
        if (oexEngine != null) {
            oexEngine.destroy();
        }
        if (localEngine != null) {
            localEngine.destroy();
        }
        super.onPause();
    }

    // @TODO duplicate from PlayActivity
    protected boolean loadGame() {
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

                    return true;
                }
                Log.d(TAG, "Game not found: " + lGameID);
            } catch (Exception e) {
                Log.d(TAG, "Caught exception loading game: " + lGameID + " " + e.getMessage());
            }
        }
        lGameID = 0;
        return false;
    }
}
