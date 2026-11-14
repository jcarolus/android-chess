package jwtc.android.chess.analyze;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
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
import jwtc.android.chess.study.StudyChapterNavigation;
import jwtc.android.chess.study.StudyStore;
import jwtc.android.chess.views.EngineEvaluationView;
import jwtc.chess.GameRecord;
import jwtc.chess.GameTree.Node;
import jwtc.chess.PGNEntry;
import jwtc.chess.PgnComment;
import jwtc.chess.board.BoardConstants;

public class AnalyzeActivity extends ChessBoardActivity {
    private static final String TAG = "AnalyzeActivity";
    public static final String EXTRA_FEN = "analysisFen";
    public static final String EXTRA_PGN = "analysisPgn";
    public static final String EXTRA_GAME_ID = "analysisGameId";
    public static final String EXTRA_SESSION_ID = "analysisSessionId";
    private static final String EXTRA_STUDY_COLLECTION = "analysisStudyCollection";
    private static final String EXTRA_STUDY_ID = "analysisStudyId";
    private static final String EXTRA_STUDY_TITLE = "analysisStudyTitle";
    private static final String EXTRA_STUDY_AUTHOR = "analysisStudyAuthor";
    private static final String EXTRA_STUDY_CHAPTER = "analysisStudyChapter";
    private static final String PREF_STUDY_ENGINE = "studyEngineEnabled";
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
    // The Move Classification of the current move, or why it is missing; null when not shown.
    private String moveFeedback;
    private MaterialButton buttonEngineMove;
    private PositionMove displayedEngineMove;
    private View buttonNext;
    private RecyclerView historyRecyclerView;
    private MoveRecyclerAdapter moveAdapter;
    private Node lastScrolledNode;
    private List<Node> navigationLine = new ArrayList<>();
    private Analysis analysis;
    private final AnalysisBoardDescriptions boardDescriptions =
            new AnalysisBoardDescriptions(this::updateSelectedSquares);
    private View buttonBackToMain;
    private View buttonVariations;
    private TextView textViewComment;
    private SwitchMaterial switchEngine;
    // Studies start without the engine so its evaluation does not give away the answers.
    private boolean engineEnabled;
    private StudyChapterNavigation chapterNavigation;
    private List<StudyStore.Chapter> studyChapters;
    private int chapterIndex;
    // The chapter PGN as last read from or written to the study, to skip unchanged saves.
    private String studySavedPgn;

    public static Intent studyIntent(
            Context context,
            String collectionId,
            String studyId,
            String title,
            String author,
            int chapterIndex) {
        return new Intent(context, AnalyzeActivity.class)
                .putExtra(EXTRA_STUDY_COLLECTION, collectionId)
                .putExtra(EXTRA_STUDY_ID, studyId)
                .putExtra(EXTRA_STUDY_TITLE, title)
                .putExtra(EXTRA_STUDY_AUTHOR, author)
                .putExtra(EXTRA_STUDY_CHAPTER, chapterIndex);
    }

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
        gameApi = GameApi.forAnalysis();
        analysis =
                new Analysis(
                        this,
                        gameApi,
                        new Analysis.Listener() {
                            @Override
                            public void onChanged() {
                                updateMoveSummary();
                                updateEvaluation();
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
        buttonVariations = findViewById(R.id.ButtonVariations);
        buttonVariations.setOnClickListener(v -> chooseVariation());
        textViewComment = findViewById(R.id.TextViewComment);
        textViewComment.setOnClickListener(v -> editComment());
        ViewCompat.replaceAccessibilityAction(
                textViewComment,
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
                getString(R.string.analysis_edit_comment),
                null);
        switchEngine = findViewById(R.id.SwitchEngine);
        engineEnabled = !isStudy() || getPrefs().getBoolean(PREF_STUDY_ENGINE, false);
        if (isStudy()) {
            chapterIndex =
                    restoredSession != null && sessionId.equals(restoredSession.getString("id"))
                            ? restoredSession.getInt("chapter")
                            : getIntent().getIntExtra(EXTRA_STUDY_CHAPTER, 0);
            switchEngine.setVisibility(View.VISIBLE);
            switchEngine.setChecked(engineEnabled);
            switchEngine.setOnCheckedChangeListener((button, checked) -> setEngineEnabled(checked));
        }
        imageAnalysisTurn = findViewById(R.id.ImageAnalysisTurn);
        textViewLastMove = findViewById(R.id.TextViewLastMove);
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
        View buttonPrevious = findViewById(R.id.ButtonPrevious);
        buttonPrevious.setOnClickListener(v -> gameApi.undoMove());
        buttonPrevious.setOnLongClickListener(
                v -> {
                    if (gameApi.hasPendingDuckMove()) {
                        gameApi.undoMove();
                    }
                    gameApi.goTo(gameApi.getRootNode());
                    return true;
                });
        buttonNext.setOnClickListener(
                v -> {
                    Node next = nextMove();
                    if (next != null) {
                        gameApi.goTo(next);
                    }
                });
        buttonNext.setOnLongClickListener(
                v -> {
                    Node end = navigationLine.get(navigationLine.size() - 1);
                    while (end.getNext() != null) {
                        end = end.getNext();
                    }
                    gameApi.goTo(end);
                    return true;
                });
        MenuDialog menuDialog =
                new MenuDialog(this, this::onMenuSelected, REQUEST_MENU, R.array.analyze_menu);
        findViewById(R.id.ButtonMenu).setOnClickListener(v -> menuDialog.show());
    }

    private void onMenuSelected(int requestCode, Bundle data) {
        String item = data.getString("item");
        if (getString(R.string.analysis_settings).equals(item)) {
            AnalysisSettingsDialog.show(
                    this, getPrefs(), gameApi.requiresBuiltinEngine(), this::restartAnalysis);
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
        chessBoardView.invalidatePieces();

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
            if (isStudy() && !loadStudyChapters()) {
                doToast(getString(R.string.study_load_failed));
                finish();
                return;
            }
            if (restoredSession != null && sessionId.equals(restoredSession.getString("id"))) {
                gameApi.loadPGN(restoredSession.getString("pgn"));
                Node node = nodeAt(restoredSession.getIntArray("path"));
                List<Node> line = pathTo(nodeAt(restoredSession.getIntArray("line")));
                if (line.contains(node)) {
                    navigationLine = line;
                }
                gameApi.goTo(node);
                if (isStudy()) {
                    studySavedPgn = gameApi.exportFullPGN();
                    applyStudyOrientation();
                }
            } else if (isStudy()) {
                if (!loadChapter()) {
                    doToast(getString(R.string.study_load_failed));
                    finish();
                    return;
                }
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
        AnalysisResults.Saved saved = readAnalysisState(restoredSession);
        if (engineEnabled) {
            startEngine(saved);
        }
        restoredSession = null;
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
        if (isStudy()) {
            chapterIndex = intent.getIntExtra(EXTRA_STUDY_CHAPTER, 0);
        }
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
        session.putIntArray("path", pathOf(gameApi.getCurrentNode()));
        if (!navigationLine.isEmpty()) {
            session.putIntArray("line", pathOf(navigationLine.get(navigationLine.size() - 1)));
        }
        session.putInt("chapter", chapterIndex);
        analysis.save().writeTo(session);
        outState.putBundle("analysisSession", session);
    }

    @Override
    protected boolean reusesPieceViews() {
        return true;
    }

    @Override
    public void rebuildBoard() {
        if (includeAttackersAndDefendersInFieldDescription) {
            boardDescriptions.show(gameApi.rustVariant(), gameApi.getFEN());
        } else {
            boardDescriptions.clear();
        }
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
        buttonBackToMain.setVisibility(onMainLine ? View.GONE : View.VISIBLE);
        boolean changed = analysis.selectPosition();
        updateNavigation();
        updateMoveSummary();
        buttonVariations.setEnabled(
                gameApi.getContinuations(gameApi.getCurrentNode()).size() > 1);
        updateComment();
        if (changed && analysis.isActive()) {
            evaluationView.clearEvaluation();
        }
        updateEvaluation();
        analysis.startNextAnalysis();
    }

    @Override
    protected int[] attackersForDescription(int square, int color) {
        return boardDescriptions.attackersFor(square, color);
    }

    private void updateEvaluation() {
        if (engineEnabled) {
            evaluationView.setVisibility(View.VISIBLE);
            showCachedEvaluation();
            return;
        }
        String score = PgnComment.evaluation(commentAfter(gameApi.getCurrentNode()));
        evaluationView.setVisibility(score.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        if (score.isEmpty()) {
            return;
        }
        if (score.startsWith("#")) {
            evaluationView.setEvaluation(score.startsWith("#-") ? -1000 : 1000);
        } else {
            evaluationView.setEvaluation(Float.parseFloat(score));
        }
        evaluationView.setContentDescription(getString(R.string.study_pgn_evaluation, score));
    }

    private void showCachedEvaluation() {
        if (gameApi.hasPendingDuckMove()) {
            return;
        }
        PositionSnapshot current = analysis.position(gameApi.getCurrentNode());
        AnalysisResults.Result result = analysis.result(current);
        if (result == null || result.evaluation == null) {
            return;
        }
        EngineEvaluation evaluation = result.evaluation;
        int winner = AnalysisResults.terminalWinner(current);
        float value =
                evaluation.mate
                        ? (winner != -1
                                ? (winner == BoardConstants.WHITE ? 1000 : -1000)
                                : evaluation.value > 0 ? 1000 : -1000)
                        : evaluation.value / 100.0f;
        evaluationView.setEvaluation(value);
    }

    private void startEngine(AnalysisResults.Saved saved) {
        SharedPreferences prefs = getPrefs();
        analysis.resume(
                prefs.getString(AnalysisSettingsDialog.PREF_ENGINE, null),
                AnalysisSettingsDialog.getTimeMillis(prefs),
                saved);
        showEngineWarning();
    }

    private void setEngineEnabled(boolean enabled) {
        if (enabled == engineEnabled) {
            return;
        }
        engineEnabled = enabled;
        getPrefs().edit().putBoolean(PREF_STUDY_ENGINE, enabled).apply();
        if (enabled) {
            // Results computed earlier on this screen stay cached while the engine is off.
            startEngine(null);
        } else {
            analysis.pause();
        }
        evaluationView.clearEvaluation();
        rebuildBoard();
    }

    private void restartAnalysis() {
        if (analysis.configure(
                getPrefs().getString(AnalysisSettingsDialog.PREF_ENGINE, null),
                AnalysisSettingsDialog.getTimeMillis(getPrefs()))) {
            showEngineWarning();
            evaluationView.clearEvaluation();
            updateMoveSummary();
            analysis.startNextAnalysis();
        }
    }

    private void showEngineWarning() {
        if (gameApi.requiresBuiltinEngine()
                || !analysis.isBuiltin()
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
        boardDescriptions.close();
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
        boolean pendingDuckMove = gameApi.hasPendingDuckMove();
        findViewById(R.id.ButtonPrevious).setEnabled(pendingDuckMove || current.getParent() != null);
        buttonNext.setEnabled(!pendingDuckMove && nextMove() != null);
    }

    /** The move Next plays: the selected line's continuation, else the preferred one. */
    private Node nextMove() {
        Node current = gameApi.getCurrentNode();
        int index = navigationLine.indexOf(current);
        return index >= 0 && index + 1 < navigationLine.size()
                ? navigationLine.get(index + 1)
                : current.getNext();
    }

    private void chooseVariation() {
        Node current = gameApi.getCurrentNode();
        List<Node> continuations = gameApi.getContinuations(current);
        String[] moves = new String[continuations.size()];
        for (int i = 0; i < continuations.size(); i++) {
            Node node = continuations.get(i);
            PGNEntry entry = node.getEntry();
            moves[i] =
                    gameApi.getMoveNumber(node)
                            + " "
                            + getMoveDescription(false, entry.sMove, entry.move);
        }
        new ChessAlertDialogBuilder(this)
                .setTitle(R.string.study_variations)
                .setSingleChoiceItems(
                        moves,
                        continuations.indexOf(nextMove()),
                        (dialog, index) -> {
                            followLine(continuations.get(index));
                            dialog.dismiss();
                        })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    /** Selects the line through the given continuation and its preferred moves after it. */
    private void followLine(Node continuation) {
        List<Node> line = pathTo(continuation);
        for (Node node = continuation.getNext(); node != null; node = node.getNext()) {
            line.add(node);
        }
        navigationLine = line;
        updateNavigation();
        updateComment();
    }

    private static String commentAfter(Node node) {
        PGNEntry entry = node.getEntry();
        return entry == null ? null : entry.sAnnotation;
    }

    private String currentComment() {
        Node current = gameApi.getCurrentNode();
        return current.getParent() == null ? gameApi.getRootComment() : commentAfter(current);
    }

    private void updateComment() {
        String text = PgnComment.text(currentComment());
        if (!text.isEmpty() && gameApi.getCurrentNode().getParent() != null) {
            text = getString(R.string.study_after_move, text);
        }
        Node next = nextMove();
        if (next != null) {
            String before = PgnComment.text(next.getLeadingComment());
            if (!before.isEmpty()) {
                if (!text.isEmpty()) {
                    text += "\n";
                }
                text += getString(R.string.study_before_move, before);
            }
        }
        if (!text.contentEquals(textViewComment.getText())) {
            textViewComment.setText(text);
        }
    }

    private void editComment() {
        Node node = gameApi.getCurrentNode();
        String comment = currentComment();
        View content = getLayoutInflater().inflate(R.layout.analysis_comment, null);
        EditText input = content.findViewById(R.id.AnalysisComment);
        input.setText(PgnComment.text(comment));
        // The next move's leading comment is shown for context; this dialog edits only the comment
        // on the current position.
        Node next = nextMove();
        String before = next == null ? "" : PgnComment.text(next.getLeadingComment());
        if (!before.isEmpty()) {
            TextView context = content.findViewById(R.id.AnalysisCommentBefore);
            context.setText(getString(R.string.study_before_move, before));
            context.setVisibility(View.VISIBLE);
        }
        new ChessAlertDialogBuilder(this)
                .setTitle(
                        node.getParent() == null
                                ? R.string.analysis_start_comment_title
                                : R.string.analysis_comment_title)
                .setView(content)
                .setNegativeButton(R.string.button_cancel, null)
                .setPositiveButton(
                        R.string.button_ok,
                        (dialog, which) -> {
                            gameApi.setAnnotation(
                                    node, PgnComment.withText(comment, input.getText().toString()));
                            updateComment();
                            updateEvaluation();
                        })
                .show();
    }

    private List<Node> pathTo(Node target) {
        List<Node> path = new ArrayList<>();
        for (Node node = target; node != null; node = node.getParent()) {
            path.add(0, node);
        }
        return path;
    }

    private int[] pathOf(Node target) {
        List<Node> path = pathTo(target);
        int[] indices = new int[path.size() - 1];
        for (int i = 1; i < path.size(); i++) {
            indices[i - 1] = gameApi.getContinuations(path.get(i - 1)).indexOf(path.get(i));
        }
        return indices;
    }

    private Node nodeAt(int[] path) {
        Node node = gameApi.getRootNode();
        if (path != null) {
            for (int index : path) {
                List<Node> children = gameApi.getContinuations(node);
                if (index < 0 || index >= children.size()) {
                    break;
                }
                node = children.get(index);
            }
        }
        return node;
    }

    private boolean isStudy() {
        return getIntent().hasExtra(EXTRA_STUDY_ID);
    }

    private boolean loadStudyChapters() {
        String collectionId = getIntent().getStringExtra(EXTRA_STUDY_COLLECTION);
        String studyId = getIntent().getStringExtra(EXTRA_STUDY_ID);
        studyChapters =
                collectionId == null || studyId == null
                        ? new ArrayList<>()
                        : StudyStore.chapters(this, collectionId, studyId);
        if (studyChapters.isEmpty()) {
            return false;
        }
        chapterIndex = Math.max(0, Math.min(chapterIndex, studyChapters.size() - 1));
        if (chapterNavigation == null) {
            chapterNavigation =
                    new StudyChapterNavigation(findViewById(R.id.root_layout), this::selectChapter);
        }
        chapterNavigation.show(
                getIntent().getStringExtra(EXTRA_STUDY_TITLE),
                getIntent().getStringExtra(EXTRA_STUDY_AUTHOR),
                studyChapters,
                chapterIndex);
        return true;
    }

    /** Loads the selected chapter at its starting position, where the introduction is. */
    private boolean loadChapter() {
        String pgn = StudyStore.chapterPgn(this, studyChapters.get(chapterIndex).id);
        if (pgn == null || !gameApi.loadPGN(pgn)) {
            return false;
        }
        studySavedPgn = gameApi.exportFullPGN();
        applyStudyOrientation();
        gameApi.goTo(gameApi.getRootNode());
        return true;
    }

    private void applyStudyOrientation() {
        chessBoardView.setRotated("black".equalsIgnoreCase(gameApi.pgnTags.get("Orientation")));
    }

    private void selectChapter(int index) {
        saveStudy();
        int previous = chapterIndex;
        analysis.reset();
        navigationLine = new ArrayList<>();
        lastScrolledNode = null;
        evaluationView.clearEvaluation();
        chapterIndex = index;
        if (!loadChapter()) {
            doToast(getString(R.string.study_load_failed));
            chapterIndex = previous;
            loadChapter();
        }
        chapterNavigation.setIndex(chapterIndex);
        if (engineEnabled) {
            startEngine(null);
        }
        rebuildBoard();
    }

    private void saveStudy() {
        if (!sessionLoaded || studyChapters == null) {
            return;
        }
        String pgn = gameApi.exportFullPGN();
        if (!pgn.equals(studySavedPgn)) {
            StudyStore.saveChapterPgn(this, studyChapters.get(chapterIndex).id, pgn);
            studySavedPgn = pgn;
        }
    }

    private void updateMoveSummary() {
        updateAnalysisSummary();
        Node current = gameApi.getCurrentNode();
        // Keep the previous announcement until all results needed for feedback are ready.
        if (engineEnabled && !analysis.summary(current).ready) {
            return;
        }

        final int state = chessStateToR(gameApi.getState());
        String moveMessage = getLastMoveAndTurnDescription(false);
        if (state != R.string.state_play
                && state != R.string.state_mate
                && state != R.string.state_check) {
            moveMessage += ". " + getString(state);
        }
        if (moveFeedback != null) {
            moveMessage += ". " + moveFeedback;
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
        moveFeedback = null;
        displayedEngineMove = null;
        buttonEngineMove.setVisibility(View.GONE);
        buttonEngineMove.setOnClickListener(null);
        if (!engineEnabled || gameApi.hasPendingDuckMove()) {
            return;
        }

        Analysis.Summary summary = analysis.summary(current);
        if (summary.recommendation != null) {
            showEngineMove(current, summary.recommendation);
        }
        if (entry == null) {
            return;
        }
        if (summary.feedback != null) {
            moveFeedback = getString(feedbackText(summary.feedback));
        } else if (summary.unavailable) {
            moveFeedback = getString(R.string.analysis_move_unavailable);
        }
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
        boardDescriptions.clear();
        analysis.pause();
        if (sessionLoaded) {
            if (lGameID > 0) {
                saveGame(GameRecord.of(gameApi.exportFullPGN(), gameApi.pgnTags), false, lGameID);
            } else if (fromGamePgnPref) {
                getPrefs().edit().putString("game_pgn", gameApi.exportFullPGN()).apply();
            } else {
                saveStudy();
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
