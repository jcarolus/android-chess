package jwtc.android.chess.study;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.SeekBar;
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
    private static final String CHAPTER_INDEX = "chapterIndex";
    private static final String COLLECTION_ID = "collectionId";
    private static final String TITLE = "studyTitle";
    private static final String AUTHOR = "studyAuthor";

    private View previous, next, variations, retry;
    private View chapterNavigation, previousChapter, nextChapter;
    private SeekBar chapterSeek;
    private List<StudyStore.Chapter> chapters;
    private int chapterIndex;
    private boolean seeking;
    private TextView chapterTitle, moveText, commentText, status;
    private EngineEvaluationView evaluation;
    private Node chosenContinuation;
    private boolean loaded;
    private int[] restoredPath;
    private int restoredContinuation = -1;

    static Intent intent(Context context, StudyStore.Study study, int chapterIndex) {
        return new Intent(context, StudyReaderActivity.class)
                .putExtra(STUDY_ID, study.id)
                .putExtra(CHAPTER_INDEX, chapterIndex)
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
        chapterNavigation = findViewById(R.id.StudyChapterNavigation);
        previousChapter = findViewById(R.id.StudyPreviousChapter);
        nextChapter = findViewById(R.id.StudyNextChapter);
        chapterSeek = findViewById(R.id.StudyChapterSeek);
        variations = findViewById(R.id.StudyVariations);
        switchMoveToSpeech = findViewById(R.id.SwitchSpeech);
        textViewWhitePieces = findViewById(R.id.TextViewWhitePieces);
        textViewBlackPieces = findViewById(R.id.TextViewBlackPieces);

        chapterIndex = getIntent().getIntExtra(CHAPTER_INDEX, 0);
        if (state != null) {
            chapterIndex = state.getInt(CHAPTER_INDEX, chapterIndex);
            restoredPath = state.getIntArray("path");
            restoredContinuation = state.getInt("continuation", -1);
        }
        TextView title = findViewById(R.id.StudyReaderTitle);
        String author = getIntent().getStringExtra(AUTHOR);
        String studyTitle = getIntent().getStringExtra(TITLE);
        title.setText(author == null ? studyTitle
                : getString(R.string.study_title_by_author, studyTitle, author));

        previousChapter.setOnClickListener(v -> selectChapter(chapterIndex - 1));
        nextChapter.setOnClickListener(v -> selectChapter(chapterIndex + 1));
        chapterSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                seeking = true;
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    showChapterTitle(progress);
                    if (!seeking) {
                        selectChapter(progress);
                    }
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seeking = false;
                selectChapter(seekBar.getProgress());
            }
        });

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
        retry.setOnClickListener(v -> {
            if (chapters == null) {
                loadChapters();
            } else {
                loadChapter();
            }
        });

        afterCreate();
        chessBoardView.setNextFocusRightId(R.id.StudyPreviousMove);
        initBoardLayoutSizing(
                root,
                findViewById(R.id.board_area),
                findViewById(R.id.study_reader_controls),
                null,
                evaluation);
        loadChapters();
    }

    private void loadChapters() {
        String studyId = getIntent().getStringExtra(STUDY_ID);
        String collectionId = getIntent().getStringExtra(COLLECTION_ID);
        if (collectionId == null || studyId == null) {
            finish();
            return;
        }
        showLoading();
        CompletableFuture.supplyAsync(() -> StudyStore.chapters(this, collectionId, studyId))
                .whenComplete((result, error) -> runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) {
                        return;
                    }
                    if (error != null || result.isEmpty()) {
                        showLoadError(error);
                        return;
                    }
                    chapters = result;
                    chapterIndex = Math.min(chapterIndex, chapters.size() - 1);
                    chapterSeek.setMax(chapters.size() - 1);
                    chapterNavigation.setVisibility(chapters.size() > 1 ? View.VISIBLE : View.GONE);
                    loadChapter();
                }));
    }

    private void selectChapter(int index) {
        if (chapterSeek.isEnabled() && index >= 0 && index < chapters.size() && index != chapterIndex) {
            chapterIndex = index;
            restoredPath = null;
            restoredContinuation = -1;
            loadChapter();
        }
    }

    private void showChapterTitle(int index) {
        chapterTitle.setText(getString(R.string.study_chapter_choice, index + 1, chapters.get(index).title));
    }

    private void showLoading() {
        status.setText(R.string.study_loading_chapter);
        status.setVisibility(View.VISIBLE);
        retry.setVisibility(View.GONE);
        setChapterNavigationEnabled(false);
        loaded = false;
        chosenContinuation = null;
        moveText.setText("");
        updatePosition();
    }

    private void loadChapter() {
        showLoading();
        chapterSeek.setProgress(chapterIndex);
        showChapterTitle(chapterIndex);
        long id = chapters.get(chapterIndex).id;
        CompletableFuture.supplyAsync(() -> StudyStore.chapterPgn(this, id))
                .whenComplete((pgn, error) -> runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) {
                        return;
                    }
                    setChapterNavigationEnabled(true);
                    if (error != null || pgn == null || !gameApi.loadPGN(pgn)) {
                        showLoadError(error);
                    } else {
                        showChapter();
                    }
                }));
    }

    private void setChapterNavigationEnabled(boolean enabled) {
        chapterSeek.setEnabled(enabled);
        previousChapter.setEnabled(enabled && chapterIndex > 0);
        nextChapter.setEnabled(enabled && chapterIndex < chapters.size() - 1);
    }

    private void showLoadError(Throwable error) {
        if (error != null) {
            Log.w("StudyReader", "Could not read study chapter", error);
        }
        status.setText(R.string.study_load_failed);
        retry.setVisibility(View.VISIBLE);
    }

    private void showChapter() {
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
        state.putInt(CHAPTER_INDEX, chapterIndex);
        state.putIntArray("path", restoredPath);
        state.putInt("continuation", restoredContinuation);
    }


}
