package jwtc.android.chess.puzzle;

import static jwtc.android.chess.helpers.ActivityHelper.pulseAnimation;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.matecollection.MateCollection;
import jwtc.android.chess.matecollection.MateKind;
import jwtc.android.chess.matecollection.MateScore;
import jwtc.android.chess.services.GameApi;
import jwtc.android.chess.services.MoveSpeech;
import jwtc.android.chess.tools.ImportActivity;
import jwtc.android.chess.tools.ImportService;
import jwtc.chess.board.BoardConstants;

public class PuzzleActivity extends ChessBoardActivity implements MateCollection.Listener {
    private static final String TAG = "PuzzleActivity";
    private MateCollection mateCollection;
    private TextView textViewPuzzleText, textViewSolution;
    private ImageView imageTurn;
    private MaterialButton butPrev, butNext, butRetry, butShow;
    private ImageView imgStatus;

    @Override
    public boolean requestMove(int from, int to) {
        if (gameApi.isEnded()) {
            setMessage(getString(R.string.puzzle_already_solved));
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

        setContentView(R.layout.puzzle);

        ActivityHelper.fixPaddings(this, findViewById(R.id.LayoutMain));

        gameApi = new GameApi();
        mateCollection = new MateCollection(this, MateKind.MATE_IN_TWO_PUZZLES, gameApi, this);

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

        afterCreate();
        View boardAreaLayout = findViewById(R.id.board_area);
        if (boardAreaLayout == null) {
            boardAreaLayout = findViewById(R.id.includeboard);
        }

        initBoardLayoutSizing(findViewById(R.id.LayoutMain), boardAreaLayout, findViewById(R.id.play_controls), findViewById(R.id.play_board_top), findViewById(R.id.play_board_bottom));

        chessBoardView.setNextFocusRightId(R.id.ButtonPuzzlePrevious);
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
    public void onPositionStarted(int position, int total, int usersColor) {
        chessBoardView.setRotated(usersColor == BoardConstants.BLACK);
        imgStatus.setImageResource(R.drawable.ic_check_none);
        butShow.setEnabled(true);

        imageTurn.setImageResource(usersColor == BoardConstants.BLACK ? R.drawable.turnblack : R.drawable.turnwhite);

        String sWhite = gameApi.getWhite();
        if (sWhite == null) {
            sWhite = "";
        } else {
            sWhite = sWhite.replace("?", "");
        }

        textViewPuzzleText.setText("# " + (position + 1) + " - " + sWhite);
        textViewSolution.setText("");
    }

    @Override
    public void onEmptyCollection() {
        Intent intent = new Intent();
        intent.setClass(PuzzleActivity.this, ImportActivity.class);
        intent.putExtra("mode", ImportService.IMPORT_PUZZLES);
        startActivityForResult(intent, ImportService.IMPORT_PUZZLES);
    }

    @Override
    public void onScore(MateScore score) {
    }

    @Override
    public void onEndOfCollection() {
    }

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
                setMessage((lastMove.isEmpty() ? "" : lastMove + " ") + getString(R.string.puzzle_not_correct_move));
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
        int move = jni.getMyMove();
        if (move != 0) {
            String sMove = MoveSpeech.describe(getResources(), jni.getMyMoveToString(), move, useLongMoveFormat);
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
    }

    @Override
    public void OnState() {
        super.OnState();

        updatePieces();
    }
}
