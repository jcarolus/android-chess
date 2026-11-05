package jwtc.android.chess.analyze;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.engine.EngineEvaluation;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.helpers.ChessAlertDialogBuilder;
import jwtc.android.chess.helpers.EinkMode;
import jwtc.android.chess.helpers.GameStore;
import jwtc.android.chess.helpers.MoveRecyclerAdapter;
import jwtc.android.chess.links.LinkActivity;
import jwtc.android.chess.links.LinkSharing;
import jwtc.android.chess.play.MenuDialog;
import jwtc.android.chess.services.GameApi;
import jwtc.android.chess.services.GameApi.PositionMove;
import jwtc.android.chess.services.GameApi.PositionSnapshot;
import jwtc.android.chess.views.EngineEvaluationView;
import jwtc.chess.GameRecord;
import jwtc.chess.GameTree.Node;
import jwtc.chess.PGNEntry;
import jwtc.chess.Pos;
import jwtc.chess.board.BoardConstants;

public class AnalyzeActivity extends ChessBoardActivity {
    private static final String TAG = "AnalyzeActivity";
    public static final String EXTRA_FEN = "analysisFen";
    public static final String EXTRA_PGN = "analysisPgn";
    public static final String EXTRA_GAME_ID = "analysisGameId";
    public static final String EXTRA_SESSION_ID = "analysisSessionId";
    private static final String PREF_HIDE_ENGINE_WARNING = "analysisHideBuiltinWarning";
    private static final int REQUEST_MENU = 1;
    private static final int REQUEST_SAVE_GAME_TO_FILE = 2;
    private boolean engineWarningAcknowledged;
    private AlertDialog analysisDialog;
    private String sessionId;
    private boolean sessionLoaded;
    private Bundle restoredSession;
    private Uri pendingSaveUri;
    // The game came from Play's "game_pgn" preference, so analysis changes are written back there.
    private boolean fromGamePgnPref;
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
    private Analysis analysis;
    private View buttonBackToMain;
    private FlexboxLayout layoutVariations;

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
        analysis =
                new Analysis(
                        this,
                        gameApi,
                        new Analysis.Listener() {
                            @Override
                            public void onChanged() {
                                updateNavigation();
                                updateMoveSummary();
                                showCachedEvaluation();
                            }

                            @Override
                            public void onEvaluation(Float value) {
                                if (value == null) {
                                    evaluationView.clearEvaluation();
                                } else {
                                    evaluationView.setEvaluation(value);
                                }
                            }
                        });
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

        boolean landscape =
                getResources().getConfiguration().orientation
                        == Configuration.ORIENTATION_LANDSCAPE;
        initBoardLayoutSizing(
                findViewById(R.id.root_layout),
                findViewById(R.id.board_area),
                findViewById(R.id.analyze_controls),
                null,
                landscape ? null : evaluationView,
                landscape ? 24 : 0);
        findViewById(R.id.ButtonPrevious).setOnClickListener(v -> gameApi.undoMove());
        buttonNext.setOnClickListener(
                v -> {
                    int index = navigationLine.indexOf(gameApi.getCurrentNode());
                    if (index >= 0 && index + 1 < navigationLine.size()) {
                        gameApi.goTo(navigationLine.get(index + 1));
                    }
                });
        MenuDialog menuDialog =
                new MenuDialog(this, this::onMenuSelected, REQUEST_MENU, R.array.analyze_menu);
        findViewById(R.id.ButtonMenu).setOnClickListener(v -> menuDialog.show());
    }

    private void onMenuSelected(int requestCode, Bundle data) {
        String item = data.getString("item");
        if (getString(R.string.analysis_settings).equals(item)) {
            AnalysisSettingsDialog.show(this, getPrefs(), this::restartAnalysis);
        } else if (getString(R.string.share_chess_link).equals(item)) {
            LinkSharing.show(this, gameApi);
        } else if (getString(R.string.menu_save_game).equals(item)) {
            showSaveGameDialog(lGameID);
        } else if (getString(R.string.menu_save_game_to_file).equals(item)) {
            startSaveGameToFile(REQUEST_SAVE_GAME_TO_FILE);
        } else if (getString(R.string.menu_clip_pgn).equals(item)) {
            copyGameToClipboard();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SAVE_GAME_TO_FILE && resultCode == RESULT_OK && data != null) {
            pendingSaveUri = data.getData();
        }
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
                fromGamePgnPref = !getIntent().hasExtra(EXTRA_GAME_ID) && pgn != null;
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
        }
        if (pendingSaveUri != null) {
            saveToFile(pendingSaveUri, gameApi.exportFullPGN());
            pendingSaveUri = null;
        }
        if (gameApi.isDuckVariant() || gameApi.isKingOfTheHillVariant()) {
            analysis.pause();
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
                        new ChessAlertDialogBuilder(this)
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
        AnalysisResults.Saved saved = readAnalysisState(restoredSession);
        analysis.resume(engineId, millis, saved);
        restoredSession = null;
        showEngineWarning();
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
        sessionLoaded = false;
        engineWarningAcknowledged = false;
        if (analysisDialog != null) {
            analysisDialog.dismiss();
            analysisDialog = null;
        }
        restoredSession = null;
        analysis.reset();
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
        analysis.save().writeTo(session);
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
        boolean changed = analysis.selectPosition();
        updateMoveSummary();
        updateVariations();
        updateNavigation();
        if (changed && analysis.isActive()) {
            evaluationView.clearEvaluation();
        }
        showCachedEvaluation();
        analysis.startNextAnalysis();
    }

    private void showCachedEvaluation() {
        PositionSnapshot current = analysis.position(gameApi.getCurrentNode());
        AnalysisResults.Result result = analysis.result(current);
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
        if (analysis.configure(
                getPrefs().getString(AnalysisSettingsDialog.PREF_ENGINE, null),
                AnalysisSettingsDialog.getTimeMillis(getPrefs()))) {
            showEngineWarning();
            evaluationView.clearEvaluation();
            updateMoveSummary();
            updateNavigation();
            analysis.startNextAnalysis();
        }
    }

    private void showEngineWarning() {
        if (!analysis.isBuiltin()
                || engineWarningAcknowledged
                || analysisDialog != null
                || getPrefs().getBoolean(PREF_HIDE_ENGINE_WARNING, false)) {
            return;
        }
        View content = getLayoutInflater().inflate(R.layout.analysis_engine_warning, null);
        CheckBox dontShowAgain = content.findViewById(R.id.AnalysisDontShowAgain);
        analysisDialog =
                new ChessAlertDialogBuilder(this)
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
        if (!analysis.summary(current).ready) {
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

        Analysis.Summary summary = analysis.summary(current);
        if (summary.recommendation != null) {
            showEngineMove(summary.branchPoint, summary.recommendation);
        }
        if (entry == null) {
            return;
        }
        textViewMoveFeedback.setVisibility(View.VISIBLE);
        textViewMoveFeedback.setText(
                summary.feedback != null
                        ? feedbackText(summary.feedback)
                        : summary.unavailable
                                ? R.string.analysis_move_unavailable
                                : R.string.analysis_move_pending);
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
    protected void onPause() {
        analysis.pause();
        if (sessionLoaded) {
            if (lGameID > 0) {
                saveGame(GameRecord.of(gameApi.exportFullPGN(), gameApi.pgnTags), false, lGameID);
            } else if (fromGamePgnPref) {
                getPrefs().edit().putString("game_pgn", gameApi.exportFullPGN()).apply();
            }
        }
        super.onPause();
    }

    private AnalysisResults.Saved readAnalysisState(Bundle session) {
        if (session == null || !sessionId.equals(session.getString("id"))) {
            return null;
        }
        return AnalysisResults.Saved.readFrom(session);
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
