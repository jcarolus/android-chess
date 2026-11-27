package jwtc.android.chess.puzzle;

import static jwtc.android.chess.helpers.ActivityHelper.pulseAnimation;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.android.material.button.MaterialButton;
import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.matecollection.MateCollection;
import jwtc.android.chess.matecollection.MateCollectionDefaults;
import jwtc.android.chess.matecollection.MateCollectionMoveIntake;
import jwtc.android.chess.matecollection.MateKind;
import jwtc.android.chess.matecollection.MateScore;
import jwtc.android.chess.moveintake.MoveIntake;
import jwtc.android.chess.moveintake.MoveOutcome;
import jwtc.android.chess.play.MenuDialog;
import jwtc.android.chess.play.MoveEntry;
import jwtc.android.chess.services.GameApi;
import jwtc.android.chess.services.MoveSpeech;
import jwtc.android.chess.tools.ImportActivity;
import jwtc.android.chess.tools.ImportService;
import jwtc.chess.board.BoardConstants;

public class PuzzleActivity extends ChessBoardActivity implements MateCollection.Listener {
    private static final String TAG = "PuzzleActivity";
    private MateCollection mateCollection;
    private MateCollectionDefaults collectionDefaults;
    private boolean emptyCollection;
    private MoveEntry moveEntry;
    private TextView textViewPuzzleText, textViewSolution;
    private ImageView imageTurn;
    private MaterialButton butPrev, butNext, butRetry, butShow;
    private ImageView imgStatus;

    @Override
    protected MoveIntake moveIntake() {
        return new MateCollectionMoveIntake(gameApi, mateCollection);
    }

    @Override
    protected void onMoveRejected(MoveOutcome.Reason reason) {
        if (reason == MoveOutcome.Reason.ENDED) {
            setMessage(getString(R.string.puzzle_already_solved));
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.puzzle);

        ActivityHelper.fixPaddings(this, findViewById(R.id.LayoutMain));

        gameApi = new GameApi();
        mateCollection = new MateCollection(this, MateKind.MATE_IN_TWO_PUZZLES, gameApi, this);
        collectionDefaults =
                new MateCollectionDefaults(
                        this,
                        MateKind.MATE_IN_TWO_PUZZLES,
                        savedInstanceState,
                        this::importDefaultPositions);

        textViewPuzzleText = findViewById(R.id.TextViewPuzzleText);
        textViewSolution = findViewById(R.id.TextViewSolution);
        textViewWhitePieces = findViewById(R.id.TextViewWhitePieces);
        textViewBlackPieces = findViewById(R.id.TextViewBlackPieces);
        imageTurn = findViewById(R.id.ImageTurn);

        imgStatus = findViewById(R.id.ImageStatus);

        butPrev = findViewById(R.id.ButtonPuzzlePrevious);
        butPrev.setOnClickListener(arg0 -> mateCollection.previous());

        butNext = findViewById(R.id.ButtonPuzzleNext);
        butNext.setOnClickListener(arg0 -> mateCollection.next());

        butRetry = findViewById(R.id.ButtonPuzzleRetry);
        butRetry.setOnClickListener(arg0 -> mateCollection.retry());

        butShow = findViewById(R.id.ButtonPuzzleShow);
        butShow.setOnClickListener(arg0 -> mateCollection.showSolution());

        switchSound = findViewById(R.id.SwitchSound);
        switchMoveToSpeech = findViewById(R.id.SwitchSpeech);

        ActivityResultLauncher<String[]> pickFile =
                registerForActivityResult(
                        new ActivityResultContracts.OpenDocument(),
                        uri -> {
                            if (uri != null) {
                                Intent intent = new Intent(this, ImportActivity.class);
                                intent.setData(uri);
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                intent.putExtra("mode", ImportService.IMPORT_PUZZLES);
                                startActivity(intent);
                            }
                        });
        View.OnClickListener openMenu =
                v ->
                        new MenuDialog(
                                        this,
                                        (request, data) ->
                                                onMenuItem(data.getString("item"), pickFile),
                                        0,
                                        emptyCollection
                                                ? R.array.puzzle_empty_menu
                                                : R.array.puzzle_menu)
                                .show();
        findViewById(R.id.ButtonMenu).setOnClickListener(openMenu);
        findViewById(R.id.EmptyCollectionMenu).setOnClickListener(openMenu);

        moveEntry = new MoveEntry(this);
        afterCreate();
        View boardAreaLayout = findViewById(R.id.board_area);
        if (boardAreaLayout == null) {
            boardAreaLayout = findViewById(R.id.includeboard);
        }

        initBoardLayoutSizing(
                findViewById(R.id.LayoutMain),
                boardAreaLayout,
                findViewById(R.id.play_controls),
                findViewById(R.id.play_board_top),
                findViewById(R.id.play_board_bottom));

        chessBoardView.setNextFocusRightId(R.id.ButtonPuzzlePrevious);
    }

    private void onMenuItem(String item, ActivityResultLauncher<String[]> pickFile) {
        if (getString(R.string.pgntool_import_puzzle).equals(item)) {
            pickFile.launch(new String[] {"*/*"});
        } else if (getString(R.string.collection_restore_defaults).equals(item)) {
            collectionDefaults.restoreDefaults();
        } else if (getString(R.string.pgntool_delete_puzzles).equals(item)) {
            confirmDeleteCollection();
        }
    }

    private void confirmDeleteCollection() {
        openConfirmDialog(
                getString(R.string.puzzle_delete_confirmation)
                        + "\n\n"
                        + getString(
                                R.string.pgntool_delete_item_count,
                                MateCollection.countPositions(this, MateKind.MATE_IN_TWO_PUZZLES)),
                getString(R.string.choice_delete),
                getString(R.string.button_cancel),
                () -> {
                    mateCollection.pause();
                    collectionDefaults.deleteAllPositions();
                    mateCollection.resume();
                },
                null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");

        useAccessibilityDrag = false;
        applySquareDragListeners();

        textToSpeech.setEnabled(false, getPrefs());

        mateCollection.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();

        moveEntry.reset();
        mateCollection.pause();
    }

    @Override
    public void onPositionStarted(int position, int total, int usersColor) {
        showCollection(true);
        chessBoardView.setRotated(usersColor == BoardConstants.BLACK);
        imgStatus.setImageResource(R.drawable.ic_check_none);
        butShow.setEnabled(true);

        imageTurn.setImageResource(
                usersColor == BoardConstants.BLACK ? R.drawable.turnblack : R.drawable.turnwhite);

        String sWhite = gameApi.getWhite();
        if (sWhite == null) {
            sWhite = "";
        } else {
            sWhite = sWhite.replace("?", "");
        }

        textViewPuzzleText.setText("# " + (position + 1) + " - " + sWhite);
        textViewSolution.setText("");
        updateMoveEntry();
    }

    @Override
    public void onEmptyCollection() {
        showCollection(false);
        moveEntry.update(null, null);
        collectionDefaults.onEmptyCollection();
    }

    private void importDefaultPositions() {
        Intent intent = new Intent(this, ImportActivity.class);
        intent.putExtra("mode", ImportService.IMPORT_PUZZLES);
        startActivity(intent);
    }

    private void showCollection(boolean hasPositions) {
        emptyCollection = !hasPositions;
        findViewById(R.id.board_area).setVisibility(hasPositions ? View.VISIBLE : View.INVISIBLE);
        findViewById(R.id.play_controls)
                .setVisibility(hasPositions ? View.VISIBLE : View.INVISIBLE);
        findViewById(R.id.EmptyCollection).setVisibility(hasPositions ? View.GONE : View.VISIBLE);
        butPrev.setEnabled(hasPositions);
        butNext.setEnabled(hasPositions);
        butRetry.setEnabled(hasPositions);
        butShow.setEnabled(hasPositions);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        collectionDefaults.saveState(state);
    }

    @Override
    protected void onDestroy() {
        collectionDefaults.close();
        super.onDestroy();
    }

    @Override
    public void onScore(MateScore score) {}

    @Override
    public void onEndOfCollection() {}

    @Override
    public void onOutcome(MateCollection.Outcome outcome, String lastMove) {
        switch (outcome) {
            case CORRECT_REPLY:
                animateCorrect();
                break;
            case SOLVED:
                animateCorrect();
                butShow.setEnabled(false);
                break;
            case WRONG_REPLY:
                setMessage(
                        (lastMove.isEmpty() ? "" : lastMove + " ")
                                + getString(R.string.puzzle_not_correct_move));
                imgStatus.setImageResource(R.drawable.ic_exclamation_triangle);
                break;
        }
    }

    public void animateCorrect() {
        imgStatus.setImageResource(R.drawable.ic_check);
        pulseAnimation(imgStatus);
        solutionMessage();
    }

    public void setMessage(String sMsg) {
        updateTextViewOrSpeech(textViewSolution, sMsg);
    }

    public void solutionMessage() {
        int move = gameApi.getMyMove();
        if (move != 0) {
            String sMove =
                    MoveSpeech.describe(
                            getResources(), gameApi.getMyMoveToString(), move, useLongMoveFormat);
            textViewSolution.setText(sMove);
        }
    }

    public void updatePieces() {
        textViewWhitePieces.setText(getPiecesDescription(BoardConstants.WHITE));
        textViewBlackPieces.setText(getPiecesDescription(BoardConstants.BLACK));
    }

    @Override
    public void onMoveApplied(int move) {
        super.onMoveApplied(move);

        updateSelectedSquares();
        updatePieces();
        updateMoveEntry();
    }

    private void updateMoveEntry() {
        moveEntry.update(gameApi, gameApi.getCurrentNode());
    }

    @Override
    public void OnState() {
        super.OnState();

        updatePieces();
    }
}
