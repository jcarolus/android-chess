package jwtc.android.chess.engine;

import android.content.Context;

import jwtc.android.chess.services.GameApi;

/**
 * The Engines one Mode uses: picks the Engine for that Mode, owns its lifecycle and the
 * registration of the listener, and takes the search limit per request.
 * <p>
 * Once closed, a session ignores further searches, so a delayed callback that fires after
 * the screen has paused cannot start a search on a destroyed Engine.
 */
public final class EngineSession {
    private final EngineApi engine;
    private final EngineListener listener;
    private boolean closed = false;

    private EngineSession(EngineApi engine, EngineListener listener) {
        this.engine = engine;
        this.listener = listener;
        engine.addListener(listener);
    }

    /**
     * Session for Mate-in-Two Puzzles and Practice Positions: always the Local Engine, with
     * quiescence search off so a forced mate is found at the shallow depth the caller asks for.
     */
    public static EngineSession forPuzzles(GameApi gameApi, EngineListener listener) {
        EngineApi engine = new LocalEngine(gameApi);
        engine.setQuiescentSearchOn(false);
        return new EngineSession(engine, listener);
    }

    /**
     * Session for Analysis: the OEX Engine chosen by the user. The Local Engine does not
     * support analysis yet; once it does, this is where it becomes a candidate.
     */
    public static EngineSession forAnalysis(Context context, GameApi gameApi, EngineListener listener,
                                            String oexEngineId) {
        return new EngineSession(new OexEngine(context, gameApi, oexEngineId), listener);
    }

    /** Whether the Engine behind this session can analyse; false once closed. */
    public boolean supportsAnalysis() {
        return !closed && engine.supportsAnalysis();
    }

    /** Starts an ANALYSIS search of the given FEN; results arrive at the listener. */
    public void analyze(String fen, int timeMillis) {
        if (closed) {
            return;
        }
        engine.analyze(fen, timeMillis);
    }

    /**
     * Cancels the current search without emitting a move, then runs onDone on the main thread
     * once a new search may start. Does nothing once closed.
     */
    public void abort(Runnable onDone) {
        if (closed) {
            return;
        }
        engine.abort(onDone);
    }

    /** Starts a PLAY search of the current position; the result arrives at the listener. */
    public void play(SearchLimit limit) {
        if (closed) {
            return;
        }
        engine.play(limit);
    }

    /** Cancels any search and releases the Engine. Safe to call more than once. */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        engine.abort(null);
        engine.removeListener(listener);
        engine.destroy();
    }
}
