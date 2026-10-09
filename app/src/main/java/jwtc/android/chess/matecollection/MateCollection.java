package jwtc.android.chess.matecollection;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import jwtc.android.chess.engine.EngineListener;
import jwtc.android.chess.engine.EngineSession;
import jwtc.android.chess.engine.SearchLimit;
import jwtc.android.chess.puzzle.MyPuzzleProvider;
import jwtc.android.chess.services.GameApi;
import jwtc.android.chess.services.GameListener;
import jwtc.chess.JNI;
import jwtc.chess.PGNEntry;

/**
 * One Mate Collection for one screen: loads Positions from the collection, runs the turn flow
 * (the opponent's reply after each of the user's moves, judged by the Local Engine) and keeps
 * the saved position and, for Practice Positions, the score.
 * <p>
 * The screen owns the {@link GameApi} and its board, and calls {@link #resume} and
 * {@link #pause} from its lifecycle. The user's moves are applied to the GameApi by the
 * screen; the module notices them through the GameApi and answers with a delayed reply.
 * Everything is on the main thread, and the listener hears nothing after {@link #pause}.
 */
public final class MateCollection {
    private static final String TAG = "MateCollection";
    private static final String PREFS = "ChessPlayer";
    private static final long REPLY_DELAY_MILLIS = 1000;

    public enum Outcome {
        /** The Engine's reply kept the mate in reach; it is the user's turn again. */
        CORRECT_REPLY,
        /** The user's last move does not lead to mate; the screen offers a retry. */
        WRONG_REPLY,
        /** The mate is on the board. */
        SOLVED
    }

    public interface Listener {
        /** A Position was loaded into the GameApi and the board is at its start. */
        void onPositionStarted(int position, int total, int usersColor);

        /** The collection has no Positions; the screen offers to import some. */
        void onEmptyCollection();

        /** Practice Positions only. */
        void onScore(MateScore score);

        /** @param lastMove the move the Engine judged, as the GameApi recorded it; empty if none */
        void onOutcome(Outcome outcome, String lastMove);

        /** {@link #next} was asked at the last Position. */
        void onEndOfCollection();
    }

    private final Context context;
    private final MateKind kind;
    private final GameApi gameApi;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable requestReply = this::requestReply;

    private final GameListener gameListener = new GameListener() {
        @Override
        public void onMoveApplied(int move) {
            moveApplied();
        }
    };

    private final EngineListener engineListener = new EngineListener() {
        @Override
        public void OnEngineMove(int move, int duckMove, int value) {
            judgeReply(move, duckMove, value);
        }

        @Override
        public void OnEngineInfo(String message, float value) {
        }

        @Override
        public void OnEngineStarted() {
        }

        @Override
        public void OnEngineAborted() {
        }

        @Override
        public void OnEngineError() {
        }
    };

    private EngineSession engineSession;
    private Cursor cursor;
    private int position;
    private int total;
    private int usersColor;
    private int numMoved;
    private boolean solvedPendingAdvance;
    private MateScore score = MateScore.NONE;

    public MateCollection(Context context, MateKind kind, GameApi gameApi, Listener listener) {
        this.context = context.getApplicationContext();
        this.kind = kind;
        this.gameApi = gameApi;
        this.listener = listener;
    }

    /** Opens the Engine session and the collection, and starts the saved Position. */
    public void resume() {
        engineSession = EngineSession.forPuzzles(gameApi, engineListener);
        gameApi.addListener(gameListener);

        SharedPreferences prefs = prefs(context);
        position = prefs.getInt(kind.positionKey, 0);
        if (kind.hasScore()) {
            score = new MateScore(prefs.getInt(kind.playedKey, 0), prefs.getInt(kind.solvedKey, 0));
        }

        cursor = context.getContentResolver().query(kind.uri(), MyPuzzleProvider.COLUMNS, null, null, "");
        total = cursor == null ? 0 : cursor.getCount();
        if (total == 0) {
            listener.onEmptyCollection();
            return;
        }
        position = MateRules.restore(position, total);
        startPosition();
    }

    /** Cancels the pending reply, saves progress and releases the Engine session and cursor. */
    public void pause() {
        handler.removeCallbacks(requestReply);
        gameApi.removeListener(gameListener);
        if (engineSession != null) {
            engineSession.close();
            engineSession = null;
        }
        if (cursor != null) {
            cursor.close();
            cursor = null;
        }
        if (total == 0) {
            return;
        }

        // A solved Practice Position is left behind when the screen goes away
        if (solvedPendingAdvance) {
            position++;
            solvedPendingAdvance = false;
        }
        SharedPreferences.Editor editor = prefs(context).edit();
        editor.putInt(kind.positionKey, position);
        if (kind.hasScore()) {
            editor.putInt(kind.playedKey, score.played);
            editor.putInt(kind.solvedKey, score.solved);
        }
        editor.apply();
    }

    /** Whether the side to move is the user's, the only time the board takes a move. */
    public boolean isUsersTurn() {
        return JNI.getInstance().getTurn() == usersColor;
    }

    public void next() {
        if (total == 0) {
            return;
        }
        if (MateRules.hasNext(position, total)) {
            position++;
            startPosition();
        } else if (kind == MateKind.PRACTICE_POSITIONS) {
            listener.onEndOfCollection();
        }
    }

    public void previous() {
        if (total == 0) {
            return;
        }
        position = MateRules.previous(position);
        startPosition();
    }

    /** Starts the current Position again; a wrong reply already counted in the score. */
    public void retry() {
        if (total == 0) {
            return;
        }
        startPosition();
    }

    /**
     * Plays the Engine's move for the user, one move per call. The opponent's reply then
     * follows like after any move of the user.
     */
    public void showSolution() {
        gameApi.jumpToBoardNum(numMoved);
        if (!gameApi.isEnded()) {
            requestReply();
        }
    }

    /** Back to the first Position, forgetting the score of Practice Positions. */
    public static void resetProgress(Context context, MateKind kind) {
        SharedPreferences.Editor editor = prefs(context).edit();
        editor.putInt(kind.positionKey, 0);
        if (kind.hasScore()) {
            editor.putInt(kind.playedKey, 0);
            editor.putInt(kind.solvedKey, 0);
        }
        editor.apply();
    }

    /** Deletes every Position of the collection and resets the progress through it. */
    public static void clearCollection(Context context, MateKind kind) {
        context.getContentResolver().delete(kind.uri(), "1=1", null);
        resetProgress(context, kind);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void startPosition() {
        handler.removeCallbacks(requestReply);

        String pgn = pgnAt(position);
        if (pgn == null) {
            return;
        }
        gameApi.loadPGN(pgn);
        // The source game's resignation must not end the position before checkmate.
        gameApi.resetForfeitTime();
        numMoved = 0;
        solvedPendingAdvance = false;
        gameApi.jumpToBoardNum(0);
        usersColor = JNI.getInstance().getTurn();

        listener.onPositionStarted(position, total, usersColor);
        if (kind.hasScore()) {
            listener.onScore(score);
        }
    }

    private String pgnAt(int pos) {
        if (cursor == null || !cursor.moveToPosition(pos)) {
            return null;
        }
        int index = cursor.getColumnIndex(MyPuzzleProvider.COL_PGN);
        if (index < 0) {
            Log.d(TAG, "Could not start a Position without a PGN column");
            return null;
        }
        return cursor.getString(index);
    }

    private void moveApplied() {
        numMoved++;
        if (gameApi.isEnded()) {
            solved();
        } else if (!isUsersTurn()) {
            handler.removeCallbacks(requestReply);
            handler.postDelayed(requestReply, REPLY_DELAY_MILLIS);
        }
    }

    private void requestReply() {
        if (engineSession != null) {
            engineSession.play(SearchLimit.ply(MateRules.plyBudget(numMoved)));
        }
    }

    private void judgeReply(int move, int duckMove, int value) {
        if (MateRules.isMateReply(value, isUsersTurn())) {
            gameApi.move(move, duckMove);
            // a mate on the board was already reported as solved by the move itself
            if (!gameApi.isEnded()) {
                listener.onOutcome(Outcome.CORRECT_REPLY, "");
            }
            return;
        }

        PGNEntry entry = gameApi.getCurrentNode().getEntry();
        String lastMove = entry == null ? "" : entry.sMove;
        numMoved--;
        if (kind.hasScore()) {
            score = score.afterWrong();
            listener.onScore(score);
        }
        listener.onOutcome(Outcome.WRONG_REPLY, lastMove);
    }

    private void solved() {
        handler.removeCallbacks(requestReply);
        if (kind.hasScore()) {
            score = score.afterSolved();
            solvedPendingAdvance = true;
            listener.onScore(score);
        }
        listener.onOutcome(Outcome.SOLVED, "");
    }
}
