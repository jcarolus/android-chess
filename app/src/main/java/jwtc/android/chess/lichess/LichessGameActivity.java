package jwtc.android.chess.lichess;

import static jwtc.android.chess.helpers.ActivityHelper.pulseAnimation;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;

import jwtc.android.chess.R;
import jwtc.android.chess.activities.ChessBoardActivity;
import jwtc.android.chess.helpers.ActivityHelper;
import jwtc.android.chess.lichess.models.GameFull;
import jwtc.android.chess.lichess.models.PuzzleAndGame;
import jwtc.android.chess.lichess.models.PuzzleBatchSolveRound;
import jwtc.android.chess.lichess.models.PuzzleGlicko;
import jwtc.android.chess.play.SaveGameDialog;
import jwtc.android.chess.services.ClockListener;
import jwtc.android.chess.services.LocalClockApi;
import jwtc.chess.Pos;
import jwtc.chess.board.BoardConstants;

/**
 * The board screen for both online play and puzzles (they share one layout and differ only in the
 * visible control strip). Launched by the lobby with either {@link #EXTRA_GAME_ID} or the puzzle
 * extras; on a bare relaunch it restores from the shared api's view mode. Extends
 * {@link ChessBoardActivity} for the board plumbing and reuses {@link LichessSession} for the
 * service-owned api.
 */
public class LichessGameActivity extends ChessBoardActivity
        implements LichessApi.LichessApiListener, ClockListener, LichessSession.Callbacks {
    private static final String TAG = "LichessGameActivity";
    public static final int REQUEST_SAVE_GAME_TO_FILE = 1;

    public static final String EXTRA_GAME_ID = "gameId";
    public static final String EXTRA_PUZZLE_ANGLE = "puzzleAngle";
    public static final String EXTRA_PUZZLE_DIFFICULTY = "puzzleDifficulty";
    public static final String EXTRA_PUZZLE_RATED = "puzzleRated";

    private final LichessSession session = new LichessSession(this);
    private LichessApi lichessApi;
    private LocalClockApi localClockApi;

    private LinearLayout layoutConfirm, layoutResignDraw, layoutSave, layoutPuzzleControls;
    private SwitchMaterial switchConfirmMoves;
    private ImageView imageTurnOpp, imageTurnMe;
    private TextView textViewClockOpp, textViewPlayerOpp, textViewRatingOpp;
    private TextView textViewClockMe, textViewPlayerMe, textViewRatingMe;
    private TextView textViewLastMove, textViewStatus, textViewOfferDraw;
    private MaterialButton buttonDraw, buttonResign, buttonConfirmMove;
    private MaterialButton buttonPuzzleShow, buttonPuzzleNext, buttonPuzzleRetry;

    // The launch request. currentGameId is durable and is updated when a new pairing (round N+1)
    // auto-opens, so it is persisted across rotation (not just re-read from the launch intent).
    private String currentGameId;
    private String puzzleAngle, puzzleDifficulty;
    private boolean puzzleRated;
    // True only for a genuinely fresh launch (not a rotation/process-recreation). Used to decide
    // whether to honor the launch extras vs. restore in-progress api state on first connect.
    private boolean freshLaunch;
    private boolean firstConnect = true;
    private String premoveGameId;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.lichess_play);
        ActivityHelper.fixPaddings(this, findViewById(R.id.ICSPlay));

        freshLaunch = savedInstanceState == null;
        Intent intent = getIntent();
        // currentGameId can change after launch (round N+1), so prefer the saved value on recreation.
        currentGameId = savedInstanceState != null && savedInstanceState.containsKey(EXTRA_GAME_ID)
            ? savedInstanceState.getString(EXTRA_GAME_ID)
            : intent.getStringExtra(EXTRA_GAME_ID);
        puzzleAngle = intent.getStringExtra(EXTRA_PUZZLE_ANGLE);
        puzzleDifficulty = intent.getStringExtra(EXTRA_PUZZLE_DIFFICULTY);
        puzzleRated = intent.getBooleanExtra(EXTRA_PUZZLE_RATED, false);

        buttonResign = findViewById(R.id.ButtonResign);
        buttonResign.setOnClickListener(v ->
            openConfirmDialog(getString(R.string.lichess_confirm_resign),
                getString(R.string.lichess_play_button_resign),
                getString(R.string.button_cancel),
                () -> lichessApi.resign(), null));

        MaterialButton buttonCancelMove = findViewById(R.id.ButtonCancelMove);
        buttonCancelMove.setOnClickListener(v -> {
            layoutConfirm.setVisibility(View.GONE);
            layoutResignDraw.setVisibility(View.VISIBLE);
            rebuildBoard();
        });

        buttonDraw = findViewById(R.id.ButtonDraw);
        buttonConfirmMove = findViewById(R.id.ButtonConfirmMove);

        MaterialButton buttonSaveToFile = findViewById(R.id.ButtonSaveToFile);
        buttonSaveToFile.setOnClickListener(v ->
            startIntentForSaveDocument("application/x-chess-pgn", "game.pgn", REQUEST_SAVE_GAME_TO_FILE));
        MaterialButton buttonSaveToDatabase = findViewById(R.id.ButtonSaveToDatabase);
        buttonSaveToDatabase.setOnClickListener(v -> {
            SaveGameDialog saveDialog = new SaveGameDialog(this, gameApi, 0, this::saveGameFromDialog);
            saveDialog.show();
        });

        switchConfirmMoves = findViewById(R.id.SwitchConfirmMoves);
        switchSound = findViewById(R.id.SwitchSound);
        switchMoveToSpeech = findViewById(R.id.SwitchSpeech);
        switchAccessibilityDrag = findViewById(R.id.SwitchAccessibilityDrag);

        layoutResignDraw = findViewById(R.id.LayoutResignDraw);
        layoutConfirm = findViewById(R.id.LayoutConfirm);
        layoutSave = findViewById(R.id.LayoutSave);
        layoutPuzzleControls = findViewById(R.id.LayoutPuzzleControls);

        buttonPuzzleShow = findViewById(R.id.ButtonPuzzleShow);
        buttonPuzzleShow.setOnClickListener(v -> lichessApi.showNextSolutionMove());
        buttonPuzzleNext = findViewById(R.id.ButtonPuzzleNext);
        buttonPuzzleNext.setOnClickListener(v -> lichessApi.nextPuzzle());
        buttonPuzzleRetry = findViewById(R.id.ButtonPuzzleRetry);
        buttonPuzzleRetry.setVisibility(View.GONE);
        buttonPuzzleRetry.setOnClickListener(v -> lichessApi.retryWrongPuzzleMove());

        imageTurnOpp = findViewById(R.id.ImageTopTurn);
        textViewClockOpp = findViewById(R.id.TextViewClockOpp);
        textViewPlayerOpp = findViewById(R.id.TextViewPlayerOpp);
        textViewRatingOpp = findViewById(R.id.TextViewRatingOpp);

        imageTurnMe = findViewById(R.id.ImageBottomTurn);
        textViewClockMe = findViewById(R.id.TextViewClockMe);
        textViewPlayerMe = findViewById(R.id.TextViewPlayerMe);
        textViewRatingMe = findViewById(R.id.TextViewRatingMe);

        textViewLastMove = findViewById(R.id.TextViewLastMove);
        textViewStatus = findViewById(R.id.TextViewStatus);
        textViewOfferDraw = findViewById(R.id.TextViewOfferDraw);
        textViewWhitePieces = findViewById(R.id.TextViewWhitePieces);
        textViewBlackPieces = findViewById(R.id.TextViewBlackPieces);

        View boardAreaLayout = findViewById(R.id.board_area);
        if (boardAreaLayout == null) {
            boardAreaLayout = findViewById(R.id.includeboard);
        }
        initBoardLayoutSizing(
            findViewById(R.id.ICSPlay),
            boardAreaLayout,
            findViewById(R.id.play_controls),
            findViewById(R.id.play_board_top),
            findViewById(R.id.play_board_bottom)
        );
        afterCreate();
    }

    // The single LichessApi is owned by LichessService and arrives asynchronously; the clock (which
    // wraps the api) is (re)created here. Each service bind yields a fresh api and clock.
    @Override
    protected void onGameApiReady() {
        super.onGameApiReady();
        localClockApi = new LocalClockApi(gameApi);
        localClockApi.addListener(this);
    }

    @Override
    public void onLichessApiReady(LichessApi api) {
        gameApi = api;
        lichessApi = api;
        onGameApiReady();
        lichessApi.setApiListener(this);
        // The shared api is (re)created unauthenticated when the service starts (e.g. this screen
        // triggered it on resume). Authenticate first if needed; onAuthenticate opens the view.
        if (lichessApi.getUser() != null) {
            openInitialView();
            openPendingGameStart();
        } else {
            lichessApi.resume();
        }
    }

    @Override
    public void onAuthenticate(String user) {
        if (user != null) {
            openInitialView();
            openPendingGameStart();
        } else {
            // No session; the lobby owns login, so bail back to it.
            finish();
        }
    }

    @Override
    public void onLichessApiLost() {
        if (lichessApi != null) {
            lichessApi.setApiListener(null);
        }
    }

    // Decide what to show on every (re)connect.
    //  - On a fresh launch we honor the launch extras (open the requested game, or fetch a fresh
    //    puzzle) even if the shared api still holds a previous game's state.
    //  - On a reconnect (rotation/background) we restore: a game re-opens from the durable
    //    currentGameId; a surviving api restores an in-progress puzzle/game; a torn-down api falls
    //    back to re-fetching a puzzle from the launch extras.
    // Nothing to show -> back to the lobby.
    private void openInitialView() {
        if (lichessApi.getUser() == null) {
            finish();
            return;
        }
        boolean honorExtras = freshLaunch && firstConnect;
        firstConnect = false;

        if (honorExtras) {
            if (currentGameId != null) {
                openGame(currentGameId);
                return;
            }
            if (puzzleAngle != null) {
                lichessApi.fetchPuzzle(puzzleAngle, puzzleDifficulty, null, puzzleRated);
                return;
            }
        }

        if (currentGameId != null) {
            openGame(currentGameId);
            return;
        }
        int mode = lichessApi.getViewMode();
        if (mode == LichessApi.VIEW_PUZZLE) {
            displayPuzzle();
            rebuildBoard();
            return;
        }
        if (mode == LichessApi.VIEW_PLAY && lichessApi.getOngoingGameId() != null) {
            openGame(lichessApi.getOngoingGameId());
            return;
        }
        if (puzzleAngle != null) {
            lichessApi.fetchPuzzle(puzzleAngle, puzzleDifficulty, null, puzzleRated);
            return;
        }
        finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // currentGameId may differ from the launch intent (round N+1 auto-open); preserve it.
        outState.putString(EXTRA_GAME_ID, currentGameId);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!session.bind(this)) {
            Log.e(TAG, "Failed to bind LichessService");
            Toast.makeText(this, R.string.lichess_service_unavailable, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = getPrefs();
        layoutConfirm.setVisibility(View.GONE);
        layoutSave.setVisibility(View.GONE);
        boolean puzzleActive = false;
        if (lichessApi != null) {
            lichessApi.setApiListener(this);
            puzzleActive = lichessApi.getViewMode() == LichessApi.VIEW_PUZZLE;
            openPendingGameStart();
        }
        layoutResignDraw.setVisibility(puzzleActive ? View.GONE : View.VISIBLE);
        layoutPuzzleControls.setVisibility(puzzleActive ? View.VISIBLE : View.GONE);
        switchConfirmMoves.setChecked(prefs.getBoolean("lichess_confirm_moves", false));
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (lichessApi != null) {
            lichessApi.setApiListener(null);
        }
        SharedPreferences.Editor editor = getPrefs().edit();
        editor.putBoolean("lichess_confirm_moves", switchConfirmMoves.isChecked());
        editor.commit();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Detach from the shared api before unbinding (it can outlive us on a fast rebind, e.g.
        // rotation), then drop stale references; a fresh api arrives on the next onLichessApiReady.
        if (localClockApi != null) {
            localClockApi.stopClock();
        }
        if (gameApi != null) {
            gameApi.removeListener(this);
            if (localClockApi != null) {
                gameApi.removeListener(localClockApi);
            }
        }
        session.unbind(this);
        gameApi = null;
        lichessApi = null;
        localClockApi = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SAVE_GAME_TO_FILE && data != null && data.getData() != null) {
            saveToFile(data.getData(), gameApi.exportFullPGN());
        }
    }

    protected void openGame(String gameId) {
        if (hasPremoved() && (premoveGameId == null || !premoveGameId.equals(gameId))) {
            clearPremove();
        }
        currentGameId = gameId;
        layoutResignDraw.setVisibility(View.VISIBLE);
        layoutPuzzleControls.setVisibility(View.GONE);
        lichessApi.game(gameId);
        displayPlay();
    }

    protected void displayPlay() {
        textViewLastMove.setText("");
        textViewStatus.setText("");
        textViewOfferDraw.setText("");
        textViewOfferDraw.setVisibility(View.VISIBLE);
        textViewClockOpp.setVisibility(View.VISIBLE);
        textViewClockMe.setVisibility(View.VISIBLE);
        layoutResignDraw.setVisibility(View.VISIBLE);
        layoutPuzzleControls.setVisibility(View.GONE);
    }

    protected void displayPuzzle() {
        clearPremove();
        textViewLastMove.setText("");
        textViewStatus.setText("");
        textViewOfferDraw.setText("");
        textViewOfferDraw.setVisibility(View.GONE);
        textViewPlayerMe.setText(lichessApi.getUser());
        textViewPlayerOpp.setText("");
        textViewRatingOpp.setText(lichessApi.getPuzzleId());
        textViewClockOpp.setVisibility(View.GONE);
        textViewClockMe.setVisibility(View.GONE);
        layoutResignDraw.setVisibility(View.GONE);
        layoutSave.setVisibility(View.GONE);
        layoutPuzzleControls.setVisibility(View.VISIBLE);
        buttonPuzzleNext.setEnabled(false);
        buttonPuzzleRetry.setVisibility(View.GONE);
        correctPosition = -1;
        wrongPosition = -1;
    }

    // --- LichessApiListener (game + puzzle callbacks) ---

    @Override
    public void onGameInit(String gameId, boolean boardCompatible) {
        // A new pairing (e.g. round N+1) while the board is up: switch to it if appropriate.
        openPendingGameStart();
    }

    private void openPendingGameStart() {
        if (lichessApi == null) {
            return;
        }
        LichessApi.PendingGameStart gameStart = lichessApi.consumePendingGameStart();
        if (gameStart == null) {
            return;
        }
        if (gameStart.boardCompatible) {
            openGame(gameStart.gameId);
        } else {
            Toast.makeText(this, R.string.lichess_game_not_board_compatible, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onGameUpdate(GameFull gameFull) {
        int myTurn = lichessApi.getMyTurn();
        int turn = lichessApi.getTurn();
        boolean playAsWhite = myTurn == BoardConstants.WHITE;
        boolean isStarted = gameFull.state.status.equals("started");
        if (!isStarted && hasPremoved()) {
            clearPremove();
            updateSelectedSquares();
        }
        textViewPlayerOpp.setText(playAsWhite ? gameFull.black.name : gameFull.white.name);
        textViewPlayerMe.setText(playAsWhite ? gameFull.white.name : gameFull.black.name);

        textViewRatingOpp.setText("" + (playAsWhite ? gameFull.black.rating : gameFull.white.rating));
        textViewRatingMe.setText("" + (playAsWhite ? gameFull.white.rating : gameFull.black.rating));

        if (gameFull.clock != null && isStarted) {
            localClockApi.startClock(gameFull.clock.increment, gameFull.state.wtime, gameFull.state.btime, turn, System.currentTimeMillis());
        }
        buttonDraw.setEnabled(isStarted);
        buttonResign.setEnabled(isStarted);

        layoutSave.setVisibility(isStarted ? View.GONE : View.VISIBLE);

        String stateMessage = gameStateToTranslated(gameFull.state.status);
        if (gameFull.state.winner != null) {
            stateMessage += ". " + getString(R.string.lichess_game_winner, gameFull.state.winner);
        }
        updateGameStateMessage(stateMessage);

        boolean isDrawOffer = playAsWhite ? gameFull.state.bdraw : gameFull.state.wdraw;
        if (isDrawOffer) {
            updateTextViewOrSpeech(textViewOfferDraw, getString(R.string.lichess_opponent_offers_draw));
            pulseAnimation(buttonDraw, 1.05f, 1);
            buttonDraw.setOnClickListener(v -> lichessApi.draw(true));
        } else {
            textViewOfferDraw.setText("");
            buttonDraw.setOnClickListener(v ->
                openConfirmDialog(getString(R.string.lichess_confirm_offer_draw),
                    getString(R.string.lichess_play_button_draw),
                    getString(R.string.button_cancel),
                    () -> lichessApi.draw(true),
                    null));
        }
    }

    @Override
    public void onGameFinish() {
        clearPremove();
        updateSelectedSquares();
        localClockApi.stopClock();
    }

    @Override
    public void onGameDisconnected() {
        clearPremove();
        Toast.makeText(this, R.string.lichess_game_disconnected, Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public void onInvalidMove(String reason) {
        clearPremove();
        updateSelectedSquares();
        feedbackIllegalMove();
    }

    @Override
    public void onPuzzle(PuzzleAndGame puzzle) {
        displayPuzzle();
    }

    @Override
    public void onPuzzleSolve(PuzzleAndGame nextPuzzle, PuzzleBatchSolveRound solveRound, PuzzleGlicko glicko) {
        onPuzzle(nextPuzzle);
        if (glicko != null) {
            textViewRatingMe.setText("" + (int) glicko.rating);
            String diffStr = solveRound.ratingDiff >= 0 ? "+" + solveRound.ratingDiff : "" + solveRound.ratingDiff;
            String msg = solveRound.win
                ? getString(R.string.lichess_puzzle_solved_rated, diffStr)
                : getString(R.string.lichess_puzzle_solved_hint_rated, diffStr);
            updateGameStateMessage(msg);
        }
    }

    @Override
    public void onPuzzleMoveCorrect() {
        updateGameStateMessage(getString(R.string.puzzle_correct_move));
    }

    @Override
    public void onPuzzleUnexpectedMove(String sMove, int toPos) {
        wrongPosition = toPos;
        rebuildBoard();
        buttonPuzzleRetry.setVisibility(View.VISIBLE);
        feedbackIllegalMove();
        updateGameStateMessage(sMove + " " + getString(R.string.puzzle_not_correct_move));
    }

    @Override
    public void onPuzzleRetried() {
        wrongPosition = -1;
        buttonPuzzleRetry.setVisibility(View.GONE);
        rebuildBoard();
    }

    @Override
    public void onPuzzleCompleted(int toPos) {
        correctPosition = toPos;
        rebuildBoard();
        buttonPuzzleNext.setEnabled(true);
    }

    @Override
    public boolean requestMove(int from, int to) {
        if (lichessApi == null) {
            rebuildBoard();
            return false;
        }

        boolean liveGame = lichessApi.getViewMode() == LichessApi.VIEW_PLAY;
        if (liveGame && !lichessApi.isOngoingGameInProgress()) {
            rebuildBoard();
            return false;
        }

        if (lichessApi.getMyTurn() == lichessApi.getTurn()) {
            return submitMove(from, to);
        }

        // Puzzles share this activity, but their automatic reply window must never accept a
        // pre-move. Only an active online game reaches the queueing path.
        if (liveGame && isMyPieceAt(from)) {
            storePremove(from, to);
            return true;
        }
        rebuildBoard();
        return false;
    }

    private boolean submitMove(int from, int to) {
        if (lichessApi.isPromotionMove(from, to)) {
            showPromotionPicker(piece -> {
                lichessApi.setPromotionPiece(piece);
                lichessApi.move(from, to);
            });
            return true;
        }
        if (switchConfirmMoves.isChecked()) {
            layoutConfirm.setVisibility(View.VISIBLE);
            layoutResignDraw.setVisibility(View.GONE);
            buttonConfirmMove.setText(getString(
                R.string.lichess_game_confirm_move,
                Pos.toString(from) + " " + Pos.toString(to)
            ));
            pulseAnimation(buttonConfirmMove, 1.05f, 1);
            buttonConfirmMove.setOnClickListener(v -> {
                lichessApi.move(from, to);
                layoutConfirm.setVisibility(View.GONE);
                layoutResignDraw.setVisibility(View.VISIBLE);
            });
        } else {
            lichessApi.move(from, to);
        }
        return false;
    }

    private void storePremove(int from, int to) {
        premoveGameId = lichessApi.getOngoingGameId();
        setPremove(from, to);
        updateSelectedSquares();
        executePremoveIfReady();
    }

    private interface PromotionSelectionListener {
        void onPromotionPieceSelected(int piece);
    }

    private void showPromotionPicker(PromotionSelectionListener listener) {
        final String[] items = getResources().getStringArray(R.array.promotionpieces);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.title_pick_promo);
        builder.setCancelable(false);
        builder.setSingleChoiceItems(items, 0, (dialog, item) -> {
            dialog.dismiss();
            listener.onPromotionPieceSelected(4 - item);
        });
        builder.create().show();
    }

    private boolean isMyPieceAt(int position) {
        return jni.pieceAt(lichessApi.getMyTurn(), position) != BoardConstants.FIELD;
    }

    private void executePremoveIfReady() {
        if (!hasPremoved() || lichessApi == null) {
            return;
        }
        if (lichessApi.getViewMode() != LichessApi.VIEW_PLAY
            || !lichessApi.isOngoingGameInProgress()
            || premoveGameId == null
            || !premoveGameId.equals(lichessApi.getOngoingGameId())) {
            clearPremove();
            updateSelectedSquares();
            return;
        }
        if (lichessApi.getMyTurn() != lichessApi.getTurn()) {
            return;
        }

        int from = premoveFrom;
        int to = premoveTo;
        // Clear before sending so duplicate stream states cannot submit the same pre-move twice.
        clearPremove();
        updateSelectedSquares();
        requestMove(from, to);
    }

    @Override
    protected int getSelectableColor() {
        if (lichessApi != null
            && lichessApi.getViewMode() == LichessApi.VIEW_PLAY
            && lichessApi.isOngoingGameInProgress()
            && lichessApi.getMyTurn() != lichessApi.getTurn()) {
            return lichessApi.getMyTurn();
        }
        return super.getSelectableColor();
    }

    @Override
    protected void clearPremove() {
        super.clearPremove();
        premoveGameId = null;
    }

    @Override
    public void OnState() {
        super.OnState();

        int myTurn = lichessApi.getMyTurn();
        int turn = lichessApi.getTurn();
        boolean isMyTurn = myTurn == turn;
        imageTurnOpp.setImageResource(isMyTurn
            ? R.drawable.turnempty
            : turn == BoardConstants.BLACK
            ? R.drawable.turnblack
            : R.drawable.turnwhite
        );

        imageTurnMe.setImageResource(isMyTurn
            ? turn == BoardConstants.BLACK
            ? R.drawable.turnblack
            : R.drawable.turnwhite
            : R.drawable.turnempty
        );

        chessBoardView.setRotated(myTurn == BoardConstants.BLACK);

        executePremoveIfReady();
    }

    @Override
    public void onMoveApplied(int move) {
        super.onMoveApplied(move);

        String sMove = getLastMoveAndTurnDescription(true);
        updateTextViewOrSpeech(textViewLastMove, sMove, protectLastMoveSpeech);
    }

    @Override
    public void onNewGameStarted(int variant) {
        super.onNewGameStarted(variant);

        feedbackNewGameStarted(lichessApi.getMyTurn(), textViewStatus);
    }

    @Override
    public void onPlayerResigned(int color) {
        super.onPlayerResigned(color);
        feedbackPlayerResigned(color, textViewStatus);
    }

    @Override
    public void onDrawAgreed() {
        super.onDrawAgreed();
        feedbackDrawAgreed(textViewStatus);
    }

    @Override
    public void onPlayerForfeitedOnTime(int color) {
        super.onPlayerForfeitedOnTime(color);
        feedbackPlayerForfeitedOnTime(color, textViewStatus);
    }

    protected String gameStateToTranslated(String state) {
        if (state.equals("created")) {
            return getString(R.string.lichess_game_state_created);
        } else if (state.equals("started")) {
            return "";
        } else if (state.equals("aborted")) {
            return getString(R.string.lichess_game_state_aborted);
        } else if (state.equals("mate")) {
            return getString(R.string.lichess_game_state_mate);
        } else if (state.equals("resign")) {
            return getString(R.string.lichess_game_state_resigned);
        }
        return "";
    }

    protected void updateGameStateMessage(String message) {
        updateTextViewOrSpeech(textViewStatus, message);
    }

    @Override
    public boolean needExitConfirmationDialog() {
        return true;
    }

    @Override
    public void showExitConfirmationDialog() {
        finish();
    }

    @Override
    public void OnClockTime() {
        if (lichessApi == null || localClockApi == null) {
            return;
        }
        int myTurn = lichessApi.getMyTurn();
        boolean playAsWhite = myTurn == BoardConstants.WHITE;
        String blackRemaining = localClockApi.getBlackRemainingTime();
        String whiteRemaining = localClockApi.getWhiteRemainingTime();
        textViewClockOpp.setText(playAsWhite ? blackRemaining : whiteRemaining);
        textViewClockMe.setText(playAsWhite ? whiteRemaining : blackRemaining);
    }

    @Override
    public void OnTimeWarning(int turn, long remainingMillies) {
        if (lichessApi != null && turn == lichessApi.getMyTurn()) {
            feedbackTimeWarning(remainingMillies);
        }
    }
}
