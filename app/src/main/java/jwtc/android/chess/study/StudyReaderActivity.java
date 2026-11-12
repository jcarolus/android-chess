package jwtc.android.chess.study;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.helpers.ChessAlertDialogBuilder;
import jwtc.android.chess.services.GameApi;
import jwtc.android.chess.views.EngineEvaluationView;
import jwtc.chess.GameTree.Node;
import jwtc.chess.PGNEntry;
import jwtc.chess.board.BoardConstants;

public final class StudyReaderActivity extends ChessBoardActivity {
    private static final String STUDY_ID = "studyId";
    private static final String CHAPTER_ID = "chapterId";
    private static final String COLLECTION_ID = "collectionId";
    private static final String TITLE = "studyTitle";
    private static final String AUTHOR = "studyAuthor";

    private View previous, next, variations, retry;
    private Button nextChapter;
    private TextView chapterTitle, moveText, commentText, status;
    private EngineEvaluationView evaluation;
    private Node chosenContinuation;
    private boolean loading;
    private boolean loaded;
    private int[] restoredPath;
    private int restoredContinuation = -1;

    static Intent intent(Context context, StudyStore.Study study, String chapterId) {
        return new Intent(context, StudyReaderActivity.class)
                .putExtra(STUDY_ID, study.id)
                .putExtra(CHAPTER_ID, chapterId)
                .putExtra(COLLECTION_ID, study.collectionId)
                .putExtra(TITLE, study.title)
                .putExtra(AUTHOR, study.author);
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.study_reader);
        View root = findViewById(R.id.root_layout);
        ActivityHelper.fixPaddings(this, root);

        gameApi = new GameApi();
        chapterTitle = findViewById(R.id.StudyReaderChapter);
        moveText = findViewById(R.id.StudyReaderMove);
        commentText = findViewById(R.id.StudyReaderComment);
        commentText.setMovementMethod(ScrollingMovementMethod.getInstance());
        commentText.setOnTouchListener(
                (view, event) -> {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                            && (view.canScrollVertically(1) || view.canScrollVertically(-1))) {
                        view.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return false;
                });
        evaluation = findViewById(R.id.StudyReaderEvaluation);
        status = findViewById(R.id.StudyReaderStatus);
        retry = findViewById(R.id.StudyReaderRetry);
        previous = findViewById(R.id.StudyPreviousMove);
        next = findViewById(R.id.StudyNextMove);
        nextChapter = findViewById(R.id.StudyNextChapter);
        variations = findViewById(R.id.StudyVariations);
        switchMoveToSpeech = findViewById(R.id.SwitchSpeech);
        textViewWhitePieces = findViewById(R.id.TextViewWhitePieces);
        textViewBlackPieces = findViewById(R.id.TextViewBlackPieces);

        if (state != null) {
            restoredPath = state.getIntArray("path");
            restoredContinuation = state.getInt("continuation", -1);
        }
        TextView title = findViewById(R.id.StudyReaderTitle);
        title.setText(
                getString(
                        R.string.study_title_by_author,
                        getIntent().getStringExtra(TITLE),
                        getIntent().getStringExtra(AUTHOR)));

        previous.setOnClickListener(
                v -> {
                    chosenContinuation = null;
                    gameApi.stepBack();
                });
        next.setOnClickListener(
                v -> {
                    Node continuation = nextMove();
                    chosenContinuation = null;
                    gameApi.stepInto(continuation);
                });
        variations.setOnClickListener(v -> chooseVariation());
        retry.setOnClickListener(v -> loadChapter());

        afterCreate();
        chessBoardView.setNextFocusRightId(R.id.StudyPreviousMove);
        initBoardLayoutSizing(
                root,
                findViewById(R.id.board_area),
                findViewById(R.id.study_reader_controls),
                null,
                evaluation);
        loadChapter();
    }

    private void loadChapter() {
        if (loading) {
            return;
        }
        String studyId = getIntent().getStringExtra(STUDY_ID);
        String chapterId = getIntent().getStringExtra(CHAPTER_ID);
        String collectionId = getIntent().getStringExtra(COLLECTION_ID);
        if (collectionId == null || studyId == null || chapterId == null) {
            finish();
            return;
        }
        status.setText(R.string.study_loading_chapter);
        retry.setVisibility(View.GONE);
        nextChapter.setEnabled(false);
        updatePosition();
        loading = true;
        CompletableFuture.supplyAsync(() -> StudyStore.chapter(this, collectionId, studyId, chapterId))
                .whenComplete(
                        (chapter, error) ->
                                runOnUiThread(
                                        () -> {
                                            if (isDestroyed() || isFinishing()) {
                                                return;
                                            }
                                            if (error != null) {
                                                Log.w("StudyReader", "Could not read study chapter", error);
                                            }
                                            showNextChapter(chapter == null ? null : chapter.next);
                                            showChapter(chapter == null ? null : chapter.pgn);
                                        }));
    }

    private void showNextChapter(StudyStore.Chapter chapter) {
        if (chapter == null) {
            nextChapter.setText(R.string.study_next_chapter);
            nextChapter.setOnClickListener(null);
            nextChapter.setEnabled(false);
            return;
        }
        nextChapter.setText(getString(R.string.study_next_chapter_named, chapter.title));
        nextChapter.setOnClickListener(
                v -> {
                    nextChapter.setEnabled(false);
                    startActivity(new Intent(getIntent()).putExtra(CHAPTER_ID, chapter.id));
                    finish();
                });
        nextChapter.setEnabled(true);
    }

    private void showChapter(String pgn) {
        loading = false;
        if (pgn == null || pgn.trim().isEmpty() || !gameApi.loadPGN(pgn)) {
            status.setText(R.string.study_load_failed);
            retry.setVisibility(View.VISIBLE);
            return;
        }
        chapterTitle.setText(StudyPgn.chapterTitle(gameApi.pgnTags));
        chessBoardView.setRotated("black".equalsIgnoreCase(gameApi.pgnTags.get("Orientation")));
        Node position = gameApi.getRootNode();
        if (restoredPath != null) {
            for (int index : restoredPath) {
                List<Node> continuations = gameApi.getContinuations(position);
                if (index < 0 || index >= continuations.size()) {
                    break;
                }
                position = continuations.get(index);
            }
        }
        List<Node> continuations = gameApi.getContinuations(position);
        if (restoredContinuation >= 0 && restoredContinuation < continuations.size()) {
            chosenContinuation = continuations.get(restoredContinuation);
        }
        loaded = true;
        status.setVisibility(View.GONE);
        gameApi.goTo(position);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (loaded) {
            rebuildBoard();
            updatePieceDescriptions();
        }
    }

    @Override
    public void rebuildBoard() {
        if (!loaded) {
            return;
        }
        super.rebuildBoard();
        updatePosition();
    }

    @Override
    protected void selectPosition(int position) {
        // The reader follows the author's recorded moves.
    }

    @Override
    public boolean requestMove(int from, int to) {
        return false;
    }

    private Node nextMove() {
        return chosenContinuation == null ? gameApi.getCurrentNode().getNext() : chosenContinuation;
    }

    private void updatePosition() {
        Node current = gameApi.getCurrentNode();
        previous.setEnabled(loaded && current.getParent() != null);
        next.setEnabled(loaded && nextMove() != null);
        variations.setVisibility(
                loaded && gameApi.getContinuations(current).size() > 1 ? View.VISIBLE : View.GONE);
        if (loaded) {
            String description =
                    current.getParent() == null
                            ? getString(
                                    gameApi.getTurn() == BoardConstants.WHITE
                                            ? R.string.analysis_white_to_move
                                            : R.string.analysis_black_to_move)
                            : getLastMoveAndTurnDescription(false);
            updateTextViewOrSpeech(moveText, description, protectLastMoveSpeech);
        }
        updateAnnotations();
    }

    private void chooseVariation() {
        List<Node> continuations = gameApi.getContinuations(gameApi.getCurrentNode());
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
                            chosenContinuation = continuations.get(index);
                            updateAnnotations();
                            dialog.dismiss();
                        })
                .setNegativeButton(R.string.button_cancel, null)
                .show();
    }

    private void updateAnnotations() {
        String text = "";
        String score = "";
        if (loaded) {
            PGNEntry current = gameApi.getCurrentNode().getEntry();
            Node continuation = nextMove();
            if (current == null) {
                text = StudyPgn.comment(gameApi.getRootComment());
                score = StudyPgn.evaluation(gameApi.getRootComment());
            } else {
                String after = StudyPgn.comment(current.sAnnotation);
                if (!after.isEmpty()) {
                    text = getString(R.string.study_after_move, after);
                }
                score = StudyPgn.evaluation(current.sAnnotation);
            }
            if (continuation != null) {
                String before = StudyPgn.comment(continuation.getLeadingComment());
                if (!before.isEmpty()) {
                    if (!text.isEmpty()) {
                        text += "\n\n";
                    }
                    text += getString(R.string.study_before_move, before);
                }
            }
        }
        if (!text.contentEquals(commentText.getText())) {
            commentText.setText(text);
            commentText.scrollTo(0, 0);
        }
        commentText.setVisibility(text.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        evaluation.setVisibility(score.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        if (!score.isEmpty()) {
            if (score.startsWith("#")) {
                evaluation.setEvaluation(score.startsWith("#-") ? -100 : 100);
            } else {
                evaluation.setEvaluation(Float.parseFloat(score));
            }
            evaluation.setContentDescription(getString(R.string.study_pgn_evaluation, score));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (loaded) {
            List<Integer> path = new ArrayList<>();
            Node node = gameApi.getCurrentNode();
            while (node.getParent() != null) {
                path.add(gameApi.getContinuations(node.getParent()).indexOf(node));
                node = node.getParent();
            }
            restoredPath = new int[path.size()];
            for (int i = 0; i < path.size(); i++) {
                restoredPath[i] = path.get(path.size() - i - 1);
            }
            restoredContinuation =
                    gameApi.getContinuations(gameApi.getCurrentNode()).indexOf(chosenContinuation);
        }
        state.putIntArray("path", restoredPath);
        state.putInt("continuation", restoredContinuation);
    }


}
