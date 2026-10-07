package jwtc.android.chess.analyze;

import android.content.ContentUris;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.flexbox.FlexboxLayout;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.engine.EngineApi;
import jwtc.android.chess.engine.EngineListener;
import jwtc.android.chess.engine.EngineEvaluation;
import jwtc.android.chess.engine.OexEngine;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.helpers.MyPGNProvider;
import jwtc.android.chess.helpers.Utils;
import jwtc.android.chess.services.GameApi;
import jwtc.android.chess.services.GameApi.PositionSnapshot;
import jwtc.android.chess.services.GameApi.PositionMove;
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
    private OexEngine oexEngine;
    private long lGameID;
    private EngineEvaluationView evaluationView;
    private TextView textViewAnalysisLine;
    private ImageView imageAnalysisTurn;
    private TextView textViewLastMove;
    private TextView textViewAnalysisMove, textViewEngineLikes;
    private TextView textViewMoveFeedback;
    private MaterialButton buttonEngineMove;
    private View buttonNext;
    private final LruCache<String, PositionAnalysis> positionAnalyses = new LruCache<>(128);
    private final Map<Node, PositionSnapshot> positions = new WeakHashMap<>();
    private Node displayedNode;
    private PositionSnapshot requestedPosition;

    private static class PositionAnalysis {
        final PositionMove preferredMove;
        final EngineEvaluation evaluation;

        PositionAnalysis(PositionMove preferredMove, EngineEvaluation evaluation) {
            this.preferredMove = preferredMove;
            this.evaluation = evaluation;
        }
    }
    private View buttonBackToMain;
    private FlexboxLayout layoutVariations;
    private boolean analysisActive;
    private String analysisFen;
    private int analysisTurn;
    private int analysisGeneration;
    private boolean analysisSwitchPending;

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
        textViewAnalysisMove = findViewById(R.id.TextViewAnalysisMove);
        textViewEngineLikes = findViewById(R.id.TextViewEngineLikes);
        textViewMoveFeedback = findViewById(R.id.TextViewMoveFeedback);
        buttonEngineMove = findViewById(R.id.ButtonEngineMove);
        buttonNext = findViewById(R.id.ButtonNext);
        buttonNext.setEnabled(false);
        textViewAnalysisLine = findViewById(R.id.TextViewAnalysisLine);
        buttonBackToMain = findViewById(R.id.ButtonBackToMain);
        buttonBackToMain.setOnClickListener(v -> gameApi.returnToMainLine());
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
        buttonNext.setOnClickListener(v -> gameApi.nextMove());

    }

    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences prefs = getPrefs();

        String sPGN = prefs.getString("game_pgn", null);
        String sFEN = prefs.getString("FEN", null);
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
        positionAnalyses.evictAll();
        positions.clear();
        displayedNode = null;
        requestedPosition = null;
        analysisSwitchPending = false;
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
        boolean changed = displayedNode != gameApi.getCurrentNode();
        displayedNode = gameApi.getCurrentNode();
        if (jni.getVariant() == BoardConstants.VARIANT_DEFAULT) {
            rememberPosition(displayedNode);
            if (displayedNode.getParent() != null) rememberPosition(displayedNode.getParent());
        }
        updateMoveSummary();
        updateVariations();
        updateNextButton();
        if (!analysisActive || myEngine == null || !myEngine.supportsAnalysis()) return;
        if (changed) {
            analysisFen = null;
            requestedPosition = null;
            analysisSwitchPending = true;
            final int generation = ++analysisGeneration;
            evaluationView.clearEvaluation();
            myEngine.abort(() -> {
                if (analysisActive && generation == analysisGeneration) {
                    analysisSwitchPending = false;
                    startNextAnalysis();
                }
            });
        } else if (analysisFen == null) {
            startNextAnalysis();
        }
    }

    private void rememberPosition(Node node) {
        if (positions.containsKey(node)) return;
        PositionSnapshot position = gameApi.getPositionSnapshot(node);
        if (position != null) positions.put(node, position);
    }

    private static boolean isDraw(int state) {
        return state == BoardConstants.STALEMATE || state == BoardConstants.DRAW_MATERIAL
            || state == BoardConstants.DRAW_50 || state == BoardConstants.DRAW_REPEAT;
    }

    private PositionAnalysis getAnalysis(PositionSnapshot position) {
        if (position == null) return null;
        // Terminal outcomes come from the board, including repetition history absent from FEN.
        if (isDraw(position.state)) return new PositionAnalysis(null, new EngineEvaluation(false, 0, 0));
        if (position.state == BoardConstants.MATE) {
            return new PositionAnalysis(null, new EngineEvaluation(true, 0, 0));
        }
        return positionAnalyses.get(position.fen);
    }

    private void startNextAnalysis() {
        if (!analysisActive || analysisSwitchPending || analysisFen != null
            || displayedNode != gameApi.getCurrentNode()) return;
        PositionSnapshot current = positions.get(displayedNode);
        if (current == null) return;
        PositionSnapshot parent = positions.get(displayedNode.getParent());
        // Fill a missing parent first when jumping into a variation or after cache eviction.
        PositionSnapshot next = parent != null && getAnalysis(parent) == null ? parent
            : getAnalysis(current) == null ? current : null;
        if (next == null) {
            PositionAnalysis result = getAnalysis(current);
            if (result != null && result.evaluation != null) {
                EngineEvaluation evaluation = result.evaluation;
                float value = evaluation.mate
                    ? (current.state == BoardConstants.MATE
                        ? (current.turn == BoardConstants.WHITE ? -1000 : 1000)
                        : evaluation.value > 0 ? 1000 : -1000)
                    : evaluation.value / 100.0f;
                evaluationView.setEvaluation(value);
            }
            updateMoveSummary();
            updateNextButton();
            return;
        }
        requestedPosition = next;
        analysisFen = next.fen;
        analysisTurn = next.turn;
        myEngine.analyze(next.fen, 1000);
    }

    private void updateNextButton() {
        PositionAnalysis result = getAnalysis(positions.get(gameApi.getCurrentNode()));
        buttonNext.setEnabled(result != null && result.preferredMove != null
            && !gameApi.getContinuations(gameApi.getCurrentNode()).isEmpty());
    }

    private void updateVariations() {
        layoutVariations.removeAllViews();
        List<Node> continuations = gameApi.getContinuations(gameApi.getCurrentNode());
        layoutVariations.setVisibility(continuations.size() > 1 ? View.VISIBLE : View.GONE);
        if (continuations.size() < 2) {
            return;
        }
        // Child zero is the preferred continuation reached with Next.
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
        if (analysisActive && analysisFen != null && analysisFen.equals(jni.toFEN())
            && message != null && message.contains(" score ")) {
            evaluationView.setEvaluation(analysisTurn == BoardConstants.WHITE ? value : -value);
        }
    }

    @Override
    public void OnEngineError() {
        evaluationView.clearEvaluation();
        if (!analysisActive || analysisFen == null) return;
        positionAnalyses.put(analysisFen, new PositionAnalysis(null, null));
        analysisFen = null;
        requestedPosition = null;
        updateMoveSummary();
        updateNextButton();
        // Continue after the engine's error callback has unwound and released its search.
        textViewMoveFeedback.post(this::startNextAnalysis);
    }

    @Override
    public void onAnalysisComplete(String fen, String bestMove, EngineEvaluation evaluation) {
        if (!analysisActive || !fen.equals(analysisFen) || requestedPosition == null
            || displayedNode != gameApi.getCurrentNode()) return;
        PositionMove preferredMove = requestedPosition.findMove(bestMove);
        EngineEvaluation whiteEvaluation = evaluation == null ? null
            : evaluation.forWhite(requestedPosition.turn == BoardConstants.WHITE);
        positionAnalyses.put(fen, new PositionAnalysis(preferredMove, whiteEvaluation));
        analysisFen = null;
        requestedPosition = null;
        updateNextButton();
        updateMoveSummary();
        startNextAnalysis();
    }

    private void updateMoveSummary() {
        Node current = gameApi.getCurrentNode();
        PGNEntry entry = current.getEntry();
        String moveText = entry == null ? "" : gameApi.getMoveNumber(current) + " " + entry.sMove;
        if (entry != null && entry.duckMove != -1) moveText += "@" + Pos.toString(entry.duckMove);
        textViewAnalysisMove.setText(moveText);
        textViewMoveFeedback.setVisibility(View.GONE);
        textViewEngineLikes.setVisibility(View.GONE);
        buttonEngineMove.setVisibility(View.GONE);
        buttonEngineMove.setOnClickListener(null);
        if (jni.getVariant() != BoardConstants.VARIANT_DEFAULT) return;

        PositionSnapshot parent = positions.get(current.getParent());
        PositionSnapshot position = positions.get(current);
        PositionAnalysis before = getAnalysis(parent);
        PositionAnalysis after = getAnalysis(position);
        boolean hasEngineContinuation = gameApi.getContinuations(current).isEmpty()
            && after != null && after.preferredMove != null;
        if (hasEngineContinuation) {
            showEngineMove(current, after.preferredMove);
        }
        if (entry == null) return;

        PositionMove recommendation = before == null ? null : before.preferredMove;
        boolean matches = recommendation != null && Move.equalPositions(entry.move, recommendation.move)
            && Move.isPromotionMove(entry.move) == Move.isPromotionMove(recommendation.move)
            && (!Move.isPromotionMove(entry.move)
                || Move.getPromotionPiece(entry.move) == Move.getPromotionPiece(recommendation.move));
        textViewMoveFeedback.setVisibility(View.VISIBLE);
        MoveClassifier.Feedback feedback = parent == null || position == null ? null
            : MoveClassifier.classify(before == null ? null : before.evaluation,
                after == null ? null : after.evaluation, parent.turn == BoardConstants.WHITE,
                matches, parent.moves.size(), position.state == BoardConstants.MATE, isDraw(position.state));
        textViewMoveFeedback.setText(feedback != null ? feedbackText(feedback)
            : before == null || after == null ? R.string.analysis_move_pending : R.string.analysis_move_unavailable);
        if (hasEngineContinuation || recommendation == null || matches) return;
        showEngineMove(current.getParent(), recommendation);
    }

    private void showEngineMove(Node branchPoint, PositionMove recommendation) {
        textViewEngineLikes.setVisibility(View.VISIBLE);
        buttonEngineMove.setVisibility(View.VISIBLE);
        buttonEngineMove.setText(recommendation.san);
        buttonEngineMove.setOnClickListener(v ->
            gameApi.createVariation(branchPoint, recommendation.san));
    }

    private static int feedbackText(MoveClassifier.Feedback feedback) {
        switch (feedback) {
            case BEST: return R.string.analysis_move_best;
            case EXCELLENT: return R.string.analysis_move_excellent;
            case OKAY: return R.string.analysis_move_okay;
            case INACCURACY: return R.string.analysis_move_inaccuracy;
            case MISTAKE: return R.string.analysis_move_mistake;
            case BLUNDER: return R.string.analysis_move_blunder;
            case FORCED: return R.string.analysis_move_forced;
            default: throw new IllegalArgumentException("Unknown move feedback");
        }
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
