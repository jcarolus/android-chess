package jwtc.android.chess.practice;

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
import com.google.android.material.progressindicator.LinearProgressIndicator;
import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.matecollection.MateCollection;
import jwtc.android.chess.matecollection.MateKind;
import jwtc.android.chess.matecollection.MateScore;
import jwtc.android.chess.play.MenuDialog;
import jwtc.android.chess.services.GameApi;
import jwtc.android.chess.tools.ImportActivity;
import jwtc.android.chess.tools.ImportService;
import jwtc.chess.board.BoardConstants;

public class PracticeActivity extends ChessBoardActivity implements MateCollection.Listener {
    private static final String TAG = "PracticeActivity";
    private MateCollection mateCollection;
    private TextView textViewPracticeMove, textViewPercentage, textViewSolution;
    private MaterialButton buttonNext, buttonRetry;
    private ImageView imageTurn, imgStatus;

    private LinearProgressIndicator percentBar;

    @Override
    public boolean requestMove(final int from, final int to) {
        if (!mateCollection.isPositionReady()) {
            rebuildBoard();
            return false;
        }

        if (gameApi.isEnded()) {
            setMessage(getString(R.string.practice_finished_position));
            rebuildBoard();
            return false;
        }

        if (!mateCollection.isUsersTurn()) {
            rebuildBoard();
            return false;
        }

        if (super.requestMove(from, to)) {
            return true;
        }
        rebuildBoard();
        return false;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.practice);

        ActivityHelper.fixPaddings(this, findViewById(R.id.root_layout));

        gameApi = new GameApi();
        mateCollection = new MateCollection(this, MateKind.PRACTICE_POSITIONS, gameApi, this);

        switchSound = findViewById(R.id.SwitchSound);
        switchMoveToSpeech = findViewById(R.id.SwitchSpeech);

        textViewPracticeMove = findViewById(R.id.TextViewPracticeMove);
        textViewPercentage = findViewById(R.id.TextViewPercentage);
        textViewSolution = findViewById(R.id.TextViewSolution);
        textViewWhitePieces = findViewById(R.id.TextViewWhitePieces);
        textViewBlackPieces = findViewById(R.id.TextViewBlackPieces);
        imageTurn = findViewById(R.id.ImageTurn);
        imgStatus = findViewById(R.id.ImageStatus);
        buttonNext = findViewById(R.id.ButtonPracticeNext);

        buttonNext.setOnClickListener(arg0 -> mateCollection.next());

        buttonRetry = findViewById(R.id.ButtonPracticeRetry);
        buttonRetry.setOnClickListener(arg0 -> mateCollection.retry());
        buttonRetry.setEnabled(false);

        percentBar = findViewById(R.id.percentBar);

        ActivityResultLauncher<String[]> pickFile =
                registerForActivityResult(
                        new ActivityResultContracts.OpenDocument(),
                        uri -> {
                            if (uri != null) {
                                Intent intent = new Intent(this, ImportActivity.class);
                                intent.setData(uri);
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                intent.putExtra("mode", ImportService.IMPORT_PRACTICE);
                                startActivity(intent);
                            }
                        });
        findViewById(R.id.ButtonMenu)
                .setOnClickListener(
                        v ->
                                new MenuDialog(
                                                this,
                                                (request, data) ->
                                                        onMenuItem(
                                                                data.getString("item"), pickFile),
                                                0,
                                                R.array.practice_menu)
                                        .show());

        afterCreate();
        View boardAreaLayout = findViewById(R.id.board_area);
        if (boardAreaLayout == null) {
            boardAreaLayout = findViewById(R.id.includeboard);
        }
        initBoardLayoutSizing(
                findViewById(R.id.root_layout),
                boardAreaLayout,
                findViewById(R.id.play_controls),
                findViewById(R.id.play_board_top),
                findViewById(R.id.play_board_bottom));

        chessBoardView.setNextFocusRightId(R.id.ButtonPracticeNext);
    }

    private void onMenuItem(String item, ActivityResultLauncher<String[]> pickFile) {
        if (getString(R.string.pgntool_import_practice).equals(item)) {
            pickFile.launch(new String[] {"*/*"});
        } else if (getString(R.string.pgntool_delete_practice).equals(item)) {
            confirmDeleteCollection();
        } else if (getString(R.string.pgntool_reset_practice).equals(item)) {
            openConfirmDialog(
                    getString(R.string.pgntool_confirm_practice_reset),
                    getString(R.string.button_ok),
                    getString(R.string.button_cancel),
                    () -> {
                        mateCollection.pause();
                        MateCollection.resetProgress(this, MateKind.PRACTICE_POSITIONS);
                        mateCollection.resume();
                        doToast(getString(R.string.practice_set_reset));
                    },
                    null);
        }
    }

    private void confirmDeleteCollection() {
        openConfirmDialog(
                getString(R.string.practice_restore_confirmation)
                        + "\n\n"
                        + getString(
                                R.string.pgntool_delete_item_count,
                                MateCollection.countPositions(this, MateKind.PRACTICE_POSITIONS)),
                getString(R.string.choice_delete),
                getString(R.string.button_cancel),
                () -> {
                    mateCollection.pause();
                    MateCollection.clearCollection(this, MateKind.PRACTICE_POSITIONS);
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

        mateCollection.pause();
    }

    @Override
    public void onPositionPreparing() {
        buttonRetry.setEnabled(false);
        textViewSolution.setText(R.string.practice_preparing_position);
    }

    @Override
    public void onPositionStarted(int position, int total, int usersColor) {
        buttonRetry.setEnabled(false);
        chessBoardView.setRotated(usersColor == BoardConstants.BLACK);

        textViewPracticeMove.setText("# " + (position + 1));
        textViewSolution.setText("");

        imageTurn.setImageResource(
                usersColor == BoardConstants.BLACK ? R.drawable.turnblack : R.drawable.turnwhite);

        imgStatus.setImageResource(R.drawable.ic_check_none);
    }

    @Override
    public void onEmptyCollection() {
        Intent intent = new Intent();
        intent.setClass(PracticeActivity.this, ImportActivity.class);
        intent.putExtra("mode", ImportService.IMPORT_PRACTICE);
        startActivityForResult(intent, ImportService.IMPORT_PRACTICE);
    }

    @Override
    public void onEndOfCollection() {
        setMessage(getString(R.string.practice_completed_collection));
    }

    @Override
    public void onScore(MateScore score) {
        textViewPercentage.setText(
                String.format("%d / %d = %.1f %%", score.solved, score.played, score.percentage()));
        percentBar.setProgressCompat((int) score.percentage(), /* animated= */ true);
    }

    @Override
    public void onOutcome(MateCollection.Outcome outcome, String lastMove) {
        switch (outcome) {
            case CORRECT_REPLY:
                animateCorrect();
                break;
            case SOLVED:
                buttonRetry.setEnabled(false);
                animateCorrect();
                break;
            case WRONG_REPLY:
                buttonRetry.setEnabled(true);
                animateWrong(
                        (lastMove.isEmpty() ? "" : lastMove + " ")
                                + getString(R.string.puzzle_not_correct_move));
                break;
        }
    }

    public void setMessage(String sMsg) {
        updateTextViewOrSpeech(textViewSolution, sMsg);
    }

    public void animateCorrect() {
        setMessage(getString(R.string.puzzle_correct_move));
        imgStatus.setImageResource(R.drawable.ic_check);
        pulseAnimation(imgStatus);
        if (sounds.isEnabled()) {
            sounds.playCorrect();
        }
    }

    public void animateWrong(String message) {
        setMessage(message);
        imgStatus.setImageResource(R.drawable.ic_exclamation_triangle);
        pulseAnimation(imgStatus);
        if (sounds.isEnabled()) {
            sounds.playError();
        }
    }

    @Override
    public void onMoveApplied(int move) {
        super.onMoveApplied(move);

        updateSelectedSquares();
    }
}
