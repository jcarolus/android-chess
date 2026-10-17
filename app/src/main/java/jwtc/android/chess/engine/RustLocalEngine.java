package jwtc.android.chess.engine;

import android.util.Log;
import java.util.ArrayList;
import jwtc.android.chess.services.GameApi;

/** Internal Standard backend. Every search owns its replayed Rust position. */
public final class RustLocalEngine extends EngineApi {
    private final GameApi gameApi;
    private final ArrayList<Runnable> completions = new ArrayList<>();
    private Thread worker;
    private long handle;
    private boolean destroyed;

    public RustLocalEngine(GameApi gameApi) { this.gameApi = gameApi; }

    @Override public synchronized void play() {
        if (destroyed || worker != null || gameApi.isEnded()) return;
        start(gameApi.getPositionSnapshot(gameApi.getCurrentNode()), SearchPurpose.PLAY, ply, msecs);
    }
    @Override public boolean supportsAnalysis() { return true; }
    @Override public synchronized void analyzeCurrentPosition(int millis) {
        analyze(gameApi.getPositionSnapshot(gameApi.getCurrentNode()), millis);
    }
    @Override public synchronized void analyze(GameApi.PositionSnapshot position, int millis) {
        if (position == null) throw new IllegalArgumentException("Missing position");
        validateAnalysisRequest(position.fen, millis);
        if (!destroyed && worker == null) start(position, SearchPurpose.ANALYSIS, 0, millis);
    }
    @Override public synchronized void analyze(String fen, int millis) {
        validateAnalysisRequest(fen, millis);
        if (destroyed || worker != null) return;
        long id = beginSearch(SearchPurpose.ANALYSIS);
        launch(new RustSearchRequest(id, RustSearchRequest.Variant.STANDARD, fen, new String[0],
            0, millis, quiescentSearchOn), null, fen);
    }
    private void start(GameApi.PositionSnapshot position, SearchPurpose purpose, int depth, int millis) {
        long id = beginSearch(purpose);
        try {
            String variant = gameApi.pgnTags.get("Variant");
            if (position == null || variant != null && !variant.equalsIgnoreCase("Standard")) {
                throw new IllegalArgumentException("Rust currently supports Standard only");
            }
            launch(RustSearchRequest.fromSnapshot(id, position, depth, millis, quiescentSearchOn),
                position, position.fen);
        } catch (RuntimeException | LinkageError error) {
            reportError(id, error);
        }
    }
    private void launch(RustSearchRequest request, GameApi.PositionSnapshot snapshot, String fen) {
        try {
            handle = RustNative.create();
            if (handle == 0) throw new IllegalStateException("Rust search allocation failed");
            final long ownedHandle = handle;
            worker = new Thread(() -> {
                RustSearchResult result = null;
                Throwable failure = null;
                try { result = RustNative.search(ownedHandle, request); }
                catch (RuntimeException | LinkageError error) { failure = error; }
                finally {
                    synchronized (RustLocalEngine.this) {
                        RustNative.destroy(ownedHandle);
                        handle = 0;
                    }
                }
                final RustSearchResult completed = result;
                final Throwable error = failure;
                updateHandler.post(() -> finish(request.searchId, snapshot, fen, completed, error));
            }, "RustLocalEngine");
            for (EngineListener listener : listeners) listener.OnEngineStarted();
            worker.start();
        } catch (RuntimeException | LinkageError error) {
            if (handle != 0) { RustNative.destroy(handle); handle = 0; }
            worker = null;
            reportError(request.searchId, error);
        }
    }
    private synchronized void finish(long id, GameApi.PositionSnapshot snapshot, String fen,
                                     RustSearchResult result, Throwable error) {
        worker = null;
        ArrayList<Runnable> callbacks = new ArrayList<>(completions);
        completions.clear();
        if (isCurrentSearch(id) && !destroyed) {
            if (error != null || result == null || result.searchId != id) {
                reportError(id, error == null ? new IllegalStateException("Invalid Rust result") : error);
            } else if (purpose == SearchPurpose.ANALYSIS) {
                EngineEvaluation evaluation = !result.hasEvaluation || Math.abs(result.score) >= 100000
                    ? null : new EngineEvaluation(false, result.score, result.completedDepth);
                for (EngineListener listener : listeners) listener.onAnalysisComplete(fen, result.bestMove, evaluation);
            } else if (snapshot != null && gameApi.isCurrentPosition(snapshot) && !result.bestMove.isEmpty()) {
                GameApi.PositionMove move = snapshot.findMove(result.bestMove);
                if (move == null) reportError(id, new IllegalStateException("Rust returned an illegal move"));
                else for (EngineListener listener : listeners) listener.OnEngineMove(move.move, -1, result.score);
            }
        }
        for (Runnable callback : callbacks) updateHandler.post(callback);
    }
    private void reportError(long id, Throwable error) {
        Log.e("RustLocalEngine", "Search failed", error);
        updateHandler.post(() -> {
            if (isCurrentSearch(id) && !destroyed) for (EngineListener listener : listeners) listener.OnEngineError();
        });
    }
    @Override public synchronized boolean isReady() { return !destroyed && worker == null; }
    @Override public synchronized void stop(Runnable onDone) {
        if (worker == null) { if (onDone != null) updateHandler.post(onDone); return; }
        if (onDone != null) completions.add(onDone);
        if (handle != 0) RustNative.stop(handle);
    }
    @Override public synchronized void abort(Runnable onDone) {
        discardSearchResults();
        stop(onDone);
        if (worker != null) for (EngineListener listener : listeners) listener.OnEngineAborted();
    }
    @Override public synchronized void destroy() { destroyed = true; abort(null); }
}
