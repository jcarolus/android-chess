package jwtc.android.chess.engine;

import android.content.Context;

import jwtc.android.chess.services.GameApi;

/**
 * The Engines one Mode uses: picks the Engine for that Mode, owns its lifecycle and the
 * registration of the listener, and takes the search limit per request.
 * <p>
 * Once closed, a session ignores further requests, so a delayed callback that fires after
 * the screen has paused cannot start a search on a destroyed Engine.
 */
public final class EngineSession {
    private final Context context;
    private final GameApi gameApi;
    private final EngineListener listener;
    private LocalEngine local;
    private OexEngine oex;
    private EngineApi current;
    private boolean closed = false;

    private EngineSession(Context context, GameApi gameApi, EngineListener listener) {
        this.context = context;
        this.gameApi = gameApi;
        this.listener = listener;
    }

    /**
     * Session for Mate-in-Two Puzzles and Practice Positions: always the Local Engine, with
     * quiescence search off so a forced mate is found at the shallow depth the caller asks for.
     */
    public static EngineSession forPuzzles(GameApi gameApi, EngineListener listener) {
        EngineSession session = new EngineSession(null, gameApi, listener);
        session.local = new LocalEngine(gameApi);
        session.local.setQuiescentSearchOn(false);
        session.use(session.local);
        return session;
    }

    /**
     * Session for Analysis: the OEX Engine chosen by the user. The Local Engine does not
     * support analysis yet; once it does, this is where it becomes a candidate.
     */
    public static EngineSession forAnalysis(Context context, GameApi gameApi, EngineListener listener,
                                            String oexEngineId) {
        EngineSession session = new EngineSession(context, gameApi, listener);
        session.oex = new OexEngine(context, gameApi, oexEngineId);
        session.use(session.oex);
        return session;
    }

    /** Session for Play; no Engine is active until {@link #selectForPlay}. */
    public static EngineSession forPlay(Context context, GameApi gameApi, EngineListener listener) {
        return new EngineSession(context, gameApi, listener);
    }

    /**
     * Picks the Engine for Play from the user's settings and the Variant (see
     * {@link EnginePolicy#forPlay}). Switching Engines cancels the running search and moves the
     * listener over; selecting the same Engine again leaves it running.
     */
    public void selectForPlay(boolean prefersOex, String oexEngineId, boolean duckVariant,
                              boolean quiescentSearch) {
        if (closed) {
            return;
        }
        if (local == null) {
            local = new LocalEngine(gameApi);
        }
        if (oex == null) {
            oex = new OexEngine(context, gameApi, oexEngineId);
        } else {
            oex.setPreferredEngineId(oexEngineId);
        }
        boolean oexAvailable = prefersOex && OexEngine.hasAvailableEngines(context);
        EngineApi target = EnginePolicy.forPlay(prefersOex, oexAvailable, duckVariant)
                == EnginePolicy.Backend.OEX ? oex : local;
        if (target != current) {
            if (current != null) {
                current.abort(null);
                current.removeListener(listener);
            }
            use(target);
        }
        target.setQuiescentSearchOn(quiescentSearch);
    }

    private void use(EngineApi engine) {
        current = engine;
        engine.addListener(listener);
    }

    /** Whether an Engine is selected and not searching; false once closed. */
    public boolean isReady() {
        return !closed && current != null && current.isReady();
    }

    /** Whether the active Engine can analyse; false once closed. */
    public boolean supportsAnalysis() {
        return !closed && current != null && current.supportsAnalysis();
    }

    /** Starts a PLAY search of the current position; the result arrives at the listener. */
    public void play(SearchLimit limit) {
        if (closed || current == null) {
            return;
        }
        current.play(limit);
    }

    /** Starts an ANALYSIS search of the given FEN; results arrive at the listener. */
    public void analyze(String fen, int timeMillis) {
        if (closed || current == null) {
            return;
        }
        current.analyze(fen, timeMillis);
    }

    /**
     * Cancels the current search without emitting a move, then runs onDone on the main thread
     * once a new search may start. Does nothing once closed.
     */
    public void abort(Runnable onDone) {
        if (closed || current == null) {
            return;
        }
        current.abort(onDone);
    }

    /** Cancels any search and releases every Engine. Safe to call more than once. */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (current != null) {
            current.abort(null);
            current.removeListener(listener);
        }
        if (local != null) {
            local.destroy();
        }
        if (oex != null) {
            oex.destroy();
        }
    }
}
