package jwtc.android.chess.engine;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.util.ArrayList;

import jwtc.chess.Move;
import jwtc.chess.Pos;

public abstract class EngineApi {
    private static final String TAG = "EngineApi";

    public static final int LEVEL_TIME = 1;
    public static final int LEVEL_PLY = 2;

    /** Search purpose, independent of whether the engine is currently searching. */
    public enum Mode {
        PLAY,
        ANALYSIS
    }

    /** Implementations set this when accepting a search, before emitting callbacks. */
    protected volatile Mode mode = Mode.PLAY;
    private volatile long searchId = 0;

    protected synchronized long beginSearch(Mode mode) {
        this.mode = mode;
        return ++searchId;
    }

    protected synchronized void discardSearchResults() {
        ++searchId;
    }

    protected boolean isCurrentSearch(long id) {
        return id == searchId;
    }

    protected static final int MSG_MOVE = 1;
    protected static final int MSG_INFO = 2;
    protected static final int MSG_ERROR = 3;
    protected int msecs = 0;
    protected int ply = 0;
    protected boolean quiescentSearchOn = true;

    protected ArrayList<EngineListener> listeners = new ArrayList<>();

    protected Handler updateHandler = new Handler(Looper.getMainLooper()) {
        // @Override
        public void handleMessage(Message msg) {
            if (!isCurrentSearch(msg.getData().getLong("searchId"))) {
                return;
            }
            if (msg.what == MSG_MOVE && mode == Mode.PLAY) {
                int move = msg.getData().getInt("move");
                int duckMove = msg.getData().getInt("duckMove");
                int value = msg.getData().getInt("value");
                Log.d(TAG, "handleMessage MOVE " + Move.toDbgString(move) + " :: " + Pos.toString(duckMove));
                for (EngineListener listener : listeners) {
                    listener.OnEngineMove(move, duckMove, value);
                }

            } else if (msg.what == MSG_INFO) {
                String message = msg.getData().getString("message");
                float value = msg.getData().getFloat("value");
                // Log.d(TAG, "handleMessage INFO " + message);
                for (EngineListener listener : listeners) {
                    listener.OnEngineInfo(message, value);
                }
            } else if (msg.what == MSG_ERROR) {
                for (EngineListener listener : listeners) {
                    listener.OnEngineError();
                }
            }
            super.handleMessage(msg);
        }
    };

    public void sendMessageFromThread(String sText, float value) {
        sendMessageFromThread(searchId, sText, value);
    }

    protected void sendMessageFromThread(long id, String sText, float value) {
        Message m = new Message();
        Bundle b = new Bundle();
        m.what = MSG_INFO;
        b.putLong("searchId", id);
        b.putString("message", sText);
        b.putFloat("value", value);
        m.setData(b);
        updateHandler.sendMessage(m);
    }

    public void sendMoveMessageFromThread(int move, int duckMove, int value) {
        sendMoveMessageFromThread(searchId, move, duckMove, value);
    }

    protected void sendMoveMessageFromThread(long id, int move, int duckMove, int value) {
        Message m = new Message();
        Bundle b = new Bundle();
        b.putLong("searchId", id);
        b.putInt("move", move);
        b.putInt("duckMove", duckMove);
        b.putInt("value", value);
        m.what = MSG_MOVE;
        m.setData(b);
        updateHandler.sendMessage(m);
    }

    public void sendErrorMessageFromThread() {
        Message m = new Message();
        m.what = MSG_ERROR;
        m.getData().putLong("searchId", searchId);
        updateHandler.sendMessage(m);
    }

    /**
     * Starts a PLAY search of the current game position using the configured time or
     * depth limit. A successful search may emit OnEngineMove.
     */
    abstract public void play();

    /** Whether this backend implements analysis; callers should check before displaying a score. */
    abstract public boolean supportsAnalysis();

    /**
     * Analyses the supplied FEN snapshot with a positive timeMillis search budget.
     * Ignores and preserves the PLAY time/depth settings. Reports OnEngineInfo only,
     * never OnEngineMove, including after stop. Does not modify the live board.
     * An unsupported backend reports an empty message with value zero without searching.
     * Calls while busy are ignored; abort with a completion callback before replacing a search.
     *
     * @throws IllegalArgumentException if the budget is not positive or FEN is empty/multiline
     */
    abstract public void analyze(String fen, int timeMillis);

    protected static void validateAnalysisRequest(String fen, int timeMillis) {
        if (timeMillis <= 0) {
            throw new IllegalArgumentException("Analysis timeMillis must be positive");
        }
        if (fen == null || fen.trim().isEmpty() || fen.indexOf('\n') >= 0 || fen.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Analysis requires a single-line FEN");
        }
    }

    /** Returns the purpose of the most recently accepted search; defaults to PLAY. */
    public Mode getMode() {
        return mode;
    }

    abstract public boolean isReady();

    /**
     * Finishes the current search as soon as possible (UCI stop), retaining its final
     * result. PLAY may emit OnEngineMove; ANALYSIS must only report information.
     * Runs a non-null onDone on the main thread after search completion and result
     * delivery, or immediately via the main-thread handler if already idle.
     */
    abstract public void stop(Runnable onDone);

    /**
     * Cancels the current search and discards its remaining results. Unlike stop,
     * cancellation must not emit OnEngineMove. Runs a non-null onDone on the main
     * thread once the old search can no longer deliver results and a new one may start.
     */
    abstract public void abort(Runnable onDone);

    abstract public void destroy();

    public void setMsecs(int msecs) {
        Log.d(TAG, "setMsecs " + msecs);
        this.msecs = msecs;
        this.ply = 0;
    }

    public void setPly(int ply) {
        Log.d(TAG, "setPly " + ply);
        this.ply = ply;
        this.msecs = 0;
    }

    public void setQuiescentSearchOn(boolean on) {
        this.quiescentSearchOn = on;
    }

    public void addListener(EngineListener listener) {
        this.listeners.add(listener);
    }

    public void removeListener(EngineListener listener) {
        this.listeners.remove(listener);
    }
}
