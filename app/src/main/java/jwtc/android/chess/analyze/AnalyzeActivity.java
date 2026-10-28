package jwtc.android.chess.analyze;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.engine.EngineEvaluation;
import jwtc.android.chess.engine.EngineListener;
import jwtc.android.chess.engine.EngineSession;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.helpers.EinkMode;
import jwtc.android.chess.helpers.GameStore;
import jwtc.android.chess.helpers.MoveRecyclerAdapter;
import jwtc.android.chess.links.LinkActivity;
import jwtc.android.chess.services.GameApi;
import jwtc.android.chess.services.GameApi.PositionMove;
import jwtc.android.chess.services.GameApi.PositionSnapshot;
import jwtc.android.chess.views.EngineEvaluationView;
import jwtc.chess.GameRecord;
import jwtc.chess.GameTree.Node;
import jwtc.chess.Move;
import jwtc.chess.PGNEntry;
import jwtc.chess.Pos;
import jwtc.chess.board.BoardConstants;

public class AnalyzeActivity extends ChessBoardActivity implements EngineListener {
    private static final String TAG = "AnalyzeActivity";
    private static final long ANALYSIS_DEBOUNCE_MILLIS = 200;
    public static final String EXTRA_FEN = "analysisFen";
    public static final String EXTRA_PGN = "analysisPgn";
    public static final String EXTRA_GAME_ID = "analysisGameId";
    public static final String EXTRA_SESSION_ID = "analysisSessionId";
    private static final String PREF_HIDE_ENGINE_WARNING = "analysisHideBuiltinWarning";
    private boolean engineWarningAcknowledged;
    private AlertDialog analysisDialog;
    private String sessionId;
    private boolean sessionLoaded;
    private Bundle restoredSession;
    private String configuredEngine;
    private int configuredMillis;
    private EngineSession engineSession;
    private long lGameID;
    private EngineEvaluationView evaluationView;
    private ImageView imageAnalysisTurn;
    private TextView textViewLastMove;
    private TextView textViewAnalysisMove;
    private TextView textViewMoveFeedback;
    private MaterialButton buttonEngineMove;
    private PositionMove displayedEngineMove;
    private View buttonNext;
    private RecyclerView historyRecyclerView;
    private MoveRecyclerAdapter moveAdapter;
    private Node lastScrolledNode;
    private List<Node> navigationLine = new ArrayList<>();
    private final Handler analysisHandler = new Handler(Looper.getMainLooper());
    private boolean analysisDebouncing;
    private final Runnable resumeAnalysis =
            () -> {
                analysisDebouncing = false;
                startNextAnalysis();
            };
    private final LruCache<String, PositionAnalysis> positionAnalyses = new LruCache<>(128);
    private final Map<Node, PositionSnapshot> positions = new WeakHashMap<>();
    private Node displayedNode;
    private PositionSnapshot requestedPosition;

    private static class PositionAnalysis {
        final PositionMove preferredMove;
        final EngineEvaluation evaluation;
        final String bestMove;

        PositionAnalysis(PositionMove preferredMove, EngineEvaluation evaluation) {
            this.bestMove = preferredMove == null ? null : preferredMove.uci;
            this.preferredMove = preferredMove;
            this.evaluation = evaluation;
        }

        PositionAnalysis(Bundle saved) {
            preferredMove = null;
            bestMove = saved.getString("move");
            evaluation =
                    saved.containsKey("value")
                            ? new EngineEvaluation(
                                    saved.getBoolean("mate"),
                                    saved.getInt("value"),
                                    saved.getInt("depth"))
                            : null;
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
        engineWarningAcknowledged =
                savedInstanceState != null
                        && savedInstanceState.getBoolean("engineWarningAcknowledged");
        restoredSession =
                savedInstanceState == null ? null : savedInstanceState.getBundle("analysisSession");
        sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        if (sessionId == null) {
            sessionId = UUID.randomUUID().toString();
            getIntent().putExtra(EXTRA_SESSION_ID, sessionId);
        }
        setContentView(R.layout.analyze);
        ActivityHelper.fixPaddings(this, findViewById(R.id.root_layout));

        evaluationView = findViewById(R.id.engine_evaluation);
        gameApi = new GameApi();
        layoutVariations = findViewById(R.id.LayoutVariations);
        imageAnalysisTurn = findViewById(R.id.ImageAnalysisTurn);
        textViewLastMove = findViewById(R.id.TextViewLastMove);
        textViewAnalysisMove = findViewById(R.id.TextViewAnalysisMove);
        textViewMoveFeedback = findViewById(R.id.TextViewMoveFeedback);
        buttonEngineMove = findViewById(R.id.ButtonEngineMove);
        buttonNext = findViewById(R.id.ButtonNext);
        historyRecyclerView = findViewById(R.id.HistoryRecyclerView);
        historyRecyclerView.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        moveAdapter =
                new MoveRecyclerAdapter(
                        this,
                        gameApi,
                        false,
                        position -> {
                            int index = position + 1;
                            if (index < navigationLine.size()) {
                                gameApi.goTo(navigationLine.get(index));
                            }
                        });
        historyRecyclerView.setAdapter(moveAdapter);
        EinkMode.applyTo(historyRecyclerView);
        buttonBackToMain = findViewById(R.id.ButtonBackToMain);
        buttonBackToMain.setOnClickListener(
                v -> {
                    navigationLine = new ArrayList<>();
                    gameApi.returnToMainLine();
                });
        switchSound = findViewById(R.id.SwitchSound);
        switchMoveToSpeech = findViewById(R.id.SwitchSpeech);
        switchAccessibilityDrag = findViewById(R.id.SwitchAccessibilityDrag);
        textViewWhitePieces = findViewById(R.id.TextViewWhitePieces);
        textViewBlackPieces = findViewById(R.id.TextViewBlackPieces);
        afterCreate();
        chessBoardView.setNextFocusRightId(R.id.ButtonPrevious);

        initBoardLayoutSizing(
                findViewById(R.id.root_layout),
                findViewById(R.id.board_area),
                findViewById(R.id.analyze_controls),
                null,
                null,
                24);
        findViewById(R.id.ButtonPrevious).setOnClickListener(v -> gameApi.undoMove());
        buttonNext.setOnClickListener(
                v -> {
                    int index = navigationLine.indexOf(gameApi.getCurrentNode());
                    if (index >= 0 && index + 1 < navigationLine.size()) {
                        gameApi.goTo(navigationLine.get(index + 1));
                    }
                });
        findViewById(R.id.ButtonAnalysisSettings)
                .setOnClickListener(
                        v -> AnalysisSettingsDialog.show(this, getPrefs(), this::restartAnalysis));
    }

    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences prefs = getPrefs();

        if (!sessionLoaded) {
            lGameID = getIntent().getLongExtra(EXTRA_GAME_ID, 0);
            String pgn = getIntent().getStringExtra(EXTRA_PGN);
            String fen = getIntent().getStringExtra(EXTRA_FEN);
            if (pgn == null && fen == null) {
                pgn = prefs.getString("game_pgn", null);
                fen = prefs.getString("FEN", null);
            }
            if (restoredSession != null && sessionId.equals(restoredSession.getString("id"))) {
                gameApi.loadPGN(restoredSession.getString("pgn"));
                Node node = gameApi.getRootNode();
                int[] path = restoredSession.getIntArray("path");
                if (path != null) {
                    for (int index : path) {
                        List<Node> children = gameApi.getContinuations(node);
                        if (index < 0 || index >= children.size()) {
                            break;
                        }
                        node = children.get(index);
                    }
                }
                gameApi.goTo(node);
            } else {
                if (lGameID > 0 && loadGame()) {
                    Log.d(TAG, "Loaded game " + lGameID);
                } else if (!getIntent().hasExtra(EXTRA_GAME_ID) && pgn != null) {
                    if (!gameApi.loadPGN(pgn)) {
                        doToast(getString(R.string.app_link_invalid));
                        finish();
                        return;
                    }
                } else if (!getIntent().hasExtra(EXTRA_GAME_ID) && fen != null) {
                    if (!gameApi.initFEN(fen, true)) {
                        doToast(getString(R.string.app_link_invalid));
                        finish();
                        return;
                    }
                } else {
                    gameApi.newGame();
                }
                Node root = gameApi.getRootNode();
                List<Node> continuations = gameApi.getContinuations(root);
                int[] sharedPath = getIntent().getIntArrayExtra(LinkActivity.EXTRA_PATH);
                if (sharedPath != null) {
                    Node selected = root;
                    for (int index : sharedPath) {
                        List<Node> children = gameApi.getContinuations(selected);
                        if (index < 0 || index >= children.size()) {
                            doToast(getString(R.string.app_link_invalid));
                            finish();
                            return;
                        }
                        selected = children.get(index);
                    }
                    gameApi.goTo(selected);
                } else {
                    gameApi.goTo(continuations.isEmpty() ? root : continuations.get(0));
                }
            }
            sessionLoaded = true;
        } else {
            gameApi.restoreCurrentBoard();
        }
        if (gameApi.isDuckVariant() || gameApi.isKingOfTheHillVariant()) {
            analysisActive = false;
            closeEngineSession();
            if (getIntent().hasExtra(LinkActivity.EXTRA_PATH)) {
                restoredSession = null;
                rebuildBoard();
                updatePieceDescriptions();
                textViewMoveFeedback.setText(R.string.app_link_no_engine);
                textViewMoveFeedback.setVisibility(View.VISIBLE);
                return;
            }
            if (analysisDialog == null) {
                analysisDialog =
                        new MaterialAlertDialogBuilder(this)
                                .setMessage(
                                        gameApi.isKingOfTheHillVariant()
                                                ? R.string.analysis_king_of_the_hill_unsupported
                                                : R.string.analysis_duck_unsupported)
                                .setPositiveButton(R.string.button_ok, (dialog, which) -> finish())
                                .setOnCancelListener(dialog -> finish())
                                .show();
            }
            return;
        }
        String engineId = prefs.getString(AnalysisSettingsDialog.PREF_ENGINE, null);
        int millis = AnalysisSettingsDialog.getTimeMillis(prefs);
        if (!Objects.equals(engineId, configuredEngine) || millis != configuredMillis) {
            positionAnalyses.evictAll();
        }
        if (restoredSession != null
                && sessionId.equals(restoredSession.getString("id"))
                && Objects.equals(engineId, restoredSession.getString("engine"))
                && millis == restoredSession.getInt("millis")) {
            ArrayList<Bundle> results = restoredSession.getParcelableArrayList("results");
            if (results != null) {
                for (Bundle result : results) {
                    if (result.containsKey("position")) {
                        positionAnalyses.put(
                                result.getString("position"), new PositionAnalysis(result));
                    }
                }
            }
        }
        restoredSession = null;
        configuredEngine = engineId;
        configuredMillis = millis;
        engineSession = EngineSession.forAnalysis(this, gameApi, this, engineId);
        showEngineWarning();
        analysisFen = null;
        requestedPosition = null;
        analysisSwitchPending = false;
        displayedNode = gameApi.getCurrentNode();
        analysisActive = true;
        rebuildBoard();
        updatePieceDescriptions();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String incomingId = intent.getStringExtra(EXTRA_SESSION_ID);
        if (incomingId != null && incomingId.equals(sessionId)) {
            return;
        }
        setIntent(intent);
        sessionId = incomingId == null ? UUID.randomUUID().toString() : incomingId;
        intent.putExtra(EXTRA_SESSION_ID, sessionId);
        analysisActive = false;
        analysisGeneration++;
        closeEngineSession();
        sessionLoaded = false;
        engineWarningAcknowledged = false;
        if (analysisDialog != null) {
            analysisDialog.dismiss();
            analysisDialog = null;
        }
        restoredSession = null;
        positionAnalyses.evictAll();
        positions.clear();
        navigationLine = new ArrayList<>();
        evaluationView.clearEvaluation();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("engineWarningAcknowledged", engineWarningAcknowledged);
        if (!sessionLoaded) {
            return;
        }
        Bundle session = new Bundle();
        session.putString("id", sessionId);
        session.putString("pgn", gameApi.exportFullPGN());
        ArrayList<Integer> reversed = new ArrayList<>();
        for (Node node = gameApi.getCurrentNode();
                node.getParent() != null;
                node = node.getParent()) {
            reversed.add(gameApi.getContinuations(node.getParent()).indexOf(node));
        }
        int[] path = new int[reversed.size()];
        for (int i = 0; i < path.length; i++) {
            path[i] = reversed.get(path.length - 1 - i);
        }
        session.putIntArray("path", path);
        session.putString("engine", configuredEngine);
        session.putInt("millis", configuredMillis);
        ArrayList<Bundle> results = new ArrayList<>();
        for (Map.Entry<String, PositionAnalysis> entry : positionAnalyses.snapshot().entrySet()) {
            PositionAnalysis analysis = entry.getValue();
            Bundle result = new Bundle();
            result.putString("position", entry.getKey());
            result.putString("move", analysis.bestMove);
            if (analysis.evaluation != null) {
                result.putBoolean("mate", analysis.evaluation.mate);
                result.putInt("value", analysis.evaluation.value);
                result.putInt("depth", analysis.evaluation.depth);
            }
            results.add(result);
        }
        session.putParcelableArrayList("results", results);
        outState.putBundle("analysisSession", session);
    }

    @Override
    public void rebuildBoard() {
        super.rebuildBoard();
        boolean whiteToMove = gameApi.getTurn() == BoardConstants.WHITE;
        imageAnalysisTurn.setImageResource(
                whiteToMove ? R.drawable.turnwhite : R.drawable.turnblack);
        imageAnalysisTurn.setContentDescription(
                getString(
                        whiteToMove
                                ? R.string.analysis_white_to_move
                                : R.string.analysis_black_to_move));
        boolean onMainLine = gameApi.isOnMainLine();
        buttonBackToMain.setVisibility(onMainLine ? View.INVISIBLE : View.VISIBLE);
        boolean changed = displayedNode != gameApi.getCurrentNode();
        displayedNode = gameApi.getCurrentNode();
        if (!gameApi.isDuckVariant()) {
            rememberPosition(displayedNode);
            if (displayedNode.getParent() != null) {
                rememberPosition(displayedNode.getParent());
            }
        }
        updateMoveSummary();
        updateVariations();
        updateNavigation();
        if (!analysisActive || engineSession == null || !engineSession.supportsAnalysis()) {
            return;
        }
        if (changed) {
            evaluationView.clearEvaluation();
            analysisHandler.removeCallbacks(resumeAnalysis);
            analysisDebouncing = true;
            analysisHandler.postDelayed(resumeAnalysis, ANALYSIS_DEBOUNCE_MILLIS);
            // Rust drops results after navigation; OEX can retain searches needed for feedback.
            PositionSnapshot current = positions.get(displayedNode);
            PositionSnapshot parent = positions.get(displayedNode.getParent());
            if (analysisFen != null
                    && (engineSession.isBuiltin()
                            || (!matchesAnalysis(current) && !matchesAnalysis(parent)))) {
                analysisFen = null;
                requestedPosition = null;
                analysisSwitchPending = true;
                final int generation = ++analysisGeneration;
                engineSession.abort(
                        () -> {
                            if (analysisActive && generation == analysisGeneration) {
                                analysisSwitchPending = false;
                                startNextAnalysis();
                            }
                        });
            }
        }
        showCachedEvaluation();
        startNextAnalysis();
    }

    private boolean matchesAnalysis(PositionSnapshot position) {
        return position != null
                && requestedPosition != null
                && position.positionCommand.equals(requestedPosition.positionCommand);
    }

    private void showCachedEvaluation() {
        PositionSnapshot current = positions.get(displayedNode);
        PositionAnalysis result = getAnalysis(current);
        if (result == null || result.evaluation == null) {
            return;
        }
        EngineEvaluation evaluation = result.evaluation;
        float value =
                evaluation.mate
                        ? (current.state == BoardConstants.MATE
                                ? (current.turn == BoardConstants.WHITE ? -1000 : 1000)
                                : evaluation.value > 0 ? 1000 : -1000)
                        : evaluation.value / 100.0f;
        evaluationView.setEvaluation(value);
    }

    private void restartAnalysis() {
        if (!analysisActive) {
            return;
        }
        String engineId = getPrefs().getString(AnalysisSettingsDialog.PREF_ENGINE, null);
        int millis = AnalysisSettingsDialog.getTimeMillis(getPrefs());
        if (Objects.equals(engineId, configuredEngine) && millis == configuredMillis) {
            return;
        }
        configuredEngine = engineId;
        configuredMillis = millis;
        analysisGeneration++;
        closeEngineSession();
        engineSession =
                EngineSession.forAnalysis(
                        this,
                        gameApi,
                        this,
                        getPrefs().getString(AnalysisSettingsDialog.PREF_ENGINE, null));
        showEngineWarning();
        analysisFen = null;
        requestedPosition = null;
        analysisSwitchPending = false;
        positionAnalyses.evictAll();
        evaluationView.clearEvaluation();
        updateMoveSummary();
        updateNavigation();
        startNextAnalysis();
    }

    private void showEngineWarning() {
        if (!engineSession.isBuiltin()
                || engineWarningAcknowledged
                || analysisDialog != null
                || getPrefs().getBoolean(PREF_HIDE_ENGINE_WARNING, false)) {
            return;
        }
        View content = getLayoutInflater().inflate(R.layout.analysis_engine_warning, null);
        CheckBox dontShowAgain = content.findViewById(R.id.AnalysisDontShowAgain);
        analysisDialog =
                new MaterialAlertDialogBuilder(this)
                        .setView(content)
                        .setPositiveButton(
                                R.string.analysis_continue,
                                (dialog, which) -> {
                                    engineWarningAcknowledged = true;
                                    if (dontShowAgain.isChecked()) {
                                        getPrefs()
                                                .edit()
                                                .putBoolean(PREF_HIDE_ENGINE_WARNING, true)
                                                .apply();
                                    }
                                })
                        .setCancelable(false)
                        .setOnDismissListener(dialog -> analysisDialog = null)
                        .show();
    }

    @Override
    protected void onDestroy() {
        if (analysisDialog != null) {
            analysisDialog.dismiss();
            analysisDialog = null;
        }
        super.onDestroy();
    }

    private void rememberPosition(Node node) {
        if (positions.containsKey(node)) {
            return;
        }
        PositionSnapshot position = gameApi.getPositionSnapshot(node);
        if (position != null) {
            positions.put(node, position);
        }
    }

    private static boolean isDraw(int state) {
        return state == BoardConstants.STALEMATE
                || state == BoardConstants.DRAW_MATERIAL
                || state == BoardConstants.DRAW_50
                || state == BoardConstants.DRAW_REPEAT;
    }

    private PositionAnalysis getAnalysis(PositionSnapshot position) {
        if (position == null) {
            return null;
        }
        // Terminal outcomes come from the board, including repetition history absent from FEN.
        if (isDraw(position.state)) {
            return new PositionAnalysis(null, new EngineEvaluation(false, 0, 0));
        }
        if (position.state == BoardConstants.MATE) {
            return new PositionAnalysis(null, new EngineEvaluation(true, 0, 0));
        }
        PositionAnalysis result = positionAnalyses.get(position.positionCommand);
        if (result != null && result.preferredMove == null && result.bestMove != null) {
            result = new PositionAnalysis(position.findMove(result.bestMove), result.evaluation);
            positionAnalyses.put(position.positionCommand, result);
        }
        return result;
    }

    private void startNextAnalysis() {
        if (!analysisActive
                || analysisDebouncing
                || analysisSwitchPending
                || analysisFen != null
                || engineSession == null
                || !engineSession.supportsAnalysis()
                || displayedNode != gameApi.getCurrentNode()) {
            return;
        }
        PositionSnapshot current = positions.get(displayedNode);
        if (current == null) {
            return;
        }
        PositionSnapshot parent = positions.get(displayedNode.getParent());
        // Feedback needs both sides of the move, even when navigation skips uncached positions.
        PositionSnapshot next =
                parent != null && getAnalysis(parent) == null
                        ? parent
                        : getAnalysis(current) == null ? current : null;
        if (next == null) {
            showCachedEvaluation();
            updateMoveSummary();
            updateNavigation();
            return;
        }
        requestedPosition = next;
        analysisFen = next.fen;
        analysisTurn = next.turn;
        engineSession.analyze(next, AnalysisSettingsDialog.getTimeMillis(getPrefs()));
    }

    private void updateNavigation() {
        Node current = gameApi.getCurrentNode();
        // Retain the selected variation when navigating back across its branch point.
        boolean lineChanged = !navigationLine.contains(current);
        if (lineChanged) {
            navigationLine = new ArrayList<>();
            navigationLine.add(gameApi.getRootNode());
            navigationLine.addAll(gameApi.getCurrentLineNodes());
        }
        int index = navigationLine.indexOf(current);
        moveAdapter.update(navigationLine.subList(1, navigationLine.size()));
        if (lineChanged || lastScrolledNode != current) {
            // Place the current move at the start so its continuation is visible.
            ((LinearLayoutManager) historyRecyclerView.getLayoutManager())
                    .scrollToPositionWithOffset(Math.max(0, index - 1), 0);
            lastScrolledNode = current;
        }
        findViewById(R.id.ButtonPrevious).setEnabled(current.getParent() != null);
        buttonNext.setEnabled(index >= 0 && index + 1 < navigationLine.size());
    }

    private void updateVariations() {
        layoutVariations.removeAllViews();
        List<Node> continuations = gameApi.getContinuations(gameApi.getCurrentNode());
        layoutVariations.setVisibility(continuations.size() > 1 ? View.VISIBLE : View.GONE);
        if (continuations.size() < 2) {
            return;
        }
        // Child zero is the preferred continuation; the remaining children are variations.
        for (int i = 1; i < continuations.size(); i++) {
            Node continuation = continuations.get(i);
            PGNEntry entry = continuation.getEntry();
            String move = gameApi.getMoveNumber(continuation) + " " + entry.sMove;
            if (entry.duckMove != -1) {
                move += "@" + Pos.toString(entry.duckMove);
            }
            MaterialButton button = new MaterialButton(this);
            FlexboxLayout.LayoutParams params =
                    new FlexboxLayout.LayoutParams(
                            FlexboxLayout.LayoutParams.WRAP_CONTENT,
                            FlexboxLayout.LayoutParams.WRAP_CONTENT);
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
        if (analysisActive
                && analysisFen != null
                && matchesAnalysis(positions.get(displayedNode))
                && message != null
                && message.contains(" score ")) {
            evaluationView.setEvaluation(analysisTurn == BoardConstants.WHITE ? value : -value);
        }
    }

    @Override
    public void OnEngineError() {
        evaluationView.clearEvaluation();
        if (!analysisActive || analysisFen == null) {
            return;
        }
        positionAnalyses.put(requestedPosition.positionCommand, new PositionAnalysis(null, null));
        analysisFen = null;
        requestedPosition = null;
        updateMoveSummary();
        updateNavigation();
        // Continue after the engine's error callback has unwound and released its search.
        textViewMoveFeedback.post(this::startNextAnalysis);
    }

    @Override
    public void onAnalysisComplete(String fen, String bestMove, EngineEvaluation evaluation) {
        if (!analysisActive
                || !fen.equals(analysisFen)
                || requestedPosition == null
                || displayedNode != gameApi.getCurrentNode()) {
            return;
        }
        PositionMove preferredMove = requestedPosition.findMove(bestMove);
        EngineEvaluation whiteEvaluation =
                evaluation == null
                        ? null
                        : evaluation.forWhite(requestedPosition.turn == BoardConstants.WHITE);
        positionAnalyses.put(
                requestedPosition.positionCommand,
                new PositionAnalysis(preferredMove, whiteEvaluation));
        analysisFen = null;
        requestedPosition = null;
        updateNavigation();
        updateMoveSummary();
        showCachedEvaluation();
        // The engine releases its completed search after this callback returns.
        analysisHandler.post(this::startNextAnalysis);
    }

    private void updateMoveSummary() {
        updateAnalysisSummary();
        if (gameApi.isDuckVariant() || gameApi.isKingOfTheHillVariant()) {
            textViewLastMove.setText(getLastMoveAndTurnDescription(false));
            textViewMoveFeedback.setText(R.string.app_link_no_engine);
            textViewMoveFeedback.setVisibility(View.VISIBLE);
            return;
        }
        Node current = gameApi.getCurrentNode();
        // Keep the previous announcement until all results needed for feedback are ready.
        if (getAnalysis(positions.get(current)) == null
                || (current.getParent() != null
                        && getAnalysis(positions.get(current.getParent())) == null)) {
            return;
        }

        final int state = chessStateToR(gameApi.getState());
        String moveMessage = getLastMoveAndTurnDescription(false);
        if (state != R.string.state_play
                && state != R.string.state_mate
                && state != R.string.state_check) {
            moveMessage += ". " + getString(state);
        }
        if (textViewMoveFeedback.getVisibility() == View.VISIBLE) {
            moveMessage += ". " + textViewMoveFeedback.getText();
        }
        if (displayedEngineMove != null) {
            moveMessage +=
                    ". "
                            + getString(R.string.analysis_engine_likes)
                            + " "
                            + getMoveDescription(
                                    false, displayedEngineMove.san, displayedEngineMove.move);
        }
        updateTextViewOrSpeech(textViewLastMove, moveMessage, protectLastMoveSpeech);
    }

    private void updateAnalysisSummary() {
        Node current = gameApi.getCurrentNode();
        PGNEntry entry = current.getEntry();
        String moveText = entry == null ? "" : gameApi.getMoveNumber(current) + " " + entry.sMove;
        if (entry != null && entry.duckMove != -1) {
            moveText += "@" + Pos.toString(entry.duckMove);
        }
        textViewAnalysisMove.setText(moveText);
        textViewMoveFeedback.setVisibility(View.GONE);
        displayedEngineMove = null;
        buttonEngineMove.setVisibility(View.GONE);
        buttonEngineMove.setOnClickListener(null);
        if (gameApi.isDuckVariant()) {
            return;
        }

        PositionSnapshot parent = positions.get(current.getParent());
        PositionSnapshot position = positions.get(current);
        PositionAnalysis before = getAnalysis(parent);
        PositionAnalysis after = getAnalysis(position);
        boolean hasEngineContinuation =
                gameApi.getContinuations(current).isEmpty()
                        && after != null
                        && after.preferredMove != null;
        if (hasEngineContinuation) {
            showEngineMove(current, after.preferredMove);
        }
        if (entry == null) {
            return;
        }

        PositionMove recommendation = before == null ? null : before.preferredMove;
        boolean matches =
                recommendation != null
                        && Move.equalPositions(entry.move, recommendation.move)
                        && Move.isPromotionMove(entry.move)
                                == Move.isPromotionMove(recommendation.move)
                        && (!Move.isPromotionMove(entry.move)
                                || Move.getPromotionPiece(entry.move)
                                        == Move.getPromotionPiece(recommendation.move));
        textViewMoveFeedback.setVisibility(View.VISIBLE);
        MoveClassifier.Feedback feedback =
                parent == null || position == null
                        ? null
                        : MoveClassifier.classify(
                                before == null ? null : before.evaluation,
                                after == null ? null : after.evaluation,
                                parent.turn == BoardConstants.WHITE,
                                matches,
                                parent.moves.size(),
                                position.state == BoardConstants.MATE,
                                isDraw(position.state));
        textViewMoveFeedback.setText(
                feedback != null
                        ? feedbackText(feedback)
                        : before == null || after == null
                                ? R.string.analysis_move_pending
                                : R.string.analysis_move_unavailable);
        if (hasEngineContinuation || recommendation == null || matches) {
            return;
        }
        showEngineMove(current.getParent(), recommendation);
    }

    private void showEngineMove(Node branchPoint, PositionMove recommendation) {
        displayedEngineMove = recommendation;
        buttonEngineMove.setVisibility(View.VISIBLE);
        buttonEngineMove.setText(recommendation.san);
        buttonEngineMove.setOnClickListener(
                v -> gameApi.createVariation(branchPoint, recommendation.san));
    }

    private static int feedbackText(MoveClassifier.Feedback feedback) {
        switch (feedback) {
            case BEST:
                return R.string.analysis_move_best;
            case EXCELLENT:
                return R.string.analysis_move_excellent;
            case OKAY:
                return R.string.analysis_move_okay;
            case INACCURACY:
                return R.string.analysis_move_inaccuracy;
            case MISTAKE:
                return R.string.analysis_move_mistake;
            case BLUNDER:
                return R.string.analysis_move_blunder;
            case FORCED:
                return R.string.analysis_move_forced;
            default:
                throw new IllegalArgumentException("Unknown move feedback");
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
        closeEngineSession();
        super.onPause();
    }

    private void closeEngineSession() {
        analysisHandler.removeCallbacks(resumeAnalysis);
        analysisDebouncing = false;
        if (engineSession != null) {
            engineSession.close();
            engineSession = null;
        }
    }

    protected boolean loadGame() {
        if (lGameID > 0) {
            GameRecord record = new GameStore(getContentResolver()).load(lGameID);
            if (record != null && gameApi.loadGame(record)) {
                lGameID = record.id;
                return true;
            }
            Log.d(TAG, "Game not loaded: " + lGameID);
        }
        lGameID = 0;
        return false;
    }
}
