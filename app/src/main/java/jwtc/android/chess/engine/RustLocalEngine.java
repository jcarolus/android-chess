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
            if (position == null || !gameApi.isStandardVariant()) {
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
                try {
                    result = RustNative.search(ownedHandle, request,
                        progress -> updateHandler.post(() -> deliverProgress(request.searchId, snapshot, fen, progress)));
                }
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
            updateHandler.post(() -> {
                if (isCurrentSearch(request.searchId) && !destroyed)
                    for (EngineListener listener : new ArrayList<>(listeners)) {
                        if (!isCurrentSearch(request.searchId) || destroyed) break;
                        listener.OnEngineStarted();
                    }
            });
            worker.start();
        } catch (RuntimeException | LinkageError error) {
            if (handle != 0) { RustNative.destroy(handle); handle = 0; }
            worker = null;
            reportError(request.searchId, error);
        }
    }
    private synchronized void finish(long id, GameApi.PositionSnapshot snapshot, String fen,
                                     RustSearchResult result, Throwable error) {
        try {
            if (!isCurrentSearch(id) || destroyed) return;
            if (error != null || result == null || result.searchId != id) {
                deliverError(id, error == null ? new IllegalStateException("Invalid Rust result") : error);
                return;
            }
            deliverProgress(id, snapshot, fen, result);
            if (!isCurrentSearch(id) || destroyed) return;
            if (purpose == SearchPurpose.ANALYSIS) {
                EngineEvaluation evaluation = !result.hasEvaluation || Math.abs(result.score) >= 100000
                    ? null : new EngineEvaluation(false, analysisScore(fen, result.score), result.completedDepth);
                for (EngineListener listener : new ArrayList<>(listeners)) {
                    if (!isCurrentSearch(id) || destroyed) break;
                    listener.onAnalysisComplete(fen, result.bestMove, evaluation);
                }
            } else if (snapshot != null && gameApi.isCurrentPosition(snapshot) && !result.bestMove.isEmpty()) {
                GameApi.PositionMove move = snapshot.findMove(result.bestMove);
                if (move == null) deliverError(id, new IllegalStateException("Rust returned an illegal move"));
                else for (EngineListener listener : new ArrayList<>(listeners)) {
                    if (!isCurrentSearch(id) || destroyed || !gameApi.isCurrentPosition(snapshot)) break;
                    listener.OnEngineMove(move.move, -1, result.score);
                }
            }
        } finally {
            // A replacement may start only after final delivery and native destruction.
            worker = null;
            ArrayList<Runnable> callbacks = new ArrayList<>(completions);
            completions.clear();
            for (Runnable callback : callbacks) updateHandler.post(callback);
        }
    }
    private synchronized void deliverProgress(long id, GameApi.PositionSnapshot snapshot, String fen, RustSearchResult result) {
        if (!isCurrentSearch(id) || destroyed || result.searchId != id
            || purpose == SearchPurpose.PLAY && snapshot != null && !gameApi.isCurrentPosition(snapshot)) return;
        long nps = result.elapsedMicros == 0 ? 0 : result.nodes * 1_000_000 / result.elapsedMicros;
        String message = "depth " + result.completedDepth + " " + result.principalVariation
            + "\n" + result.nodes + " N, " + nps + " N/s (" + result.elapsedMicros / 1000 + " ms)";
        int score = purpose == SearchPurpose.ANALYSIS ? analysisScore(fen, result.score) : result.score;
        if (purpose == SearchPurpose.ANALYSIS && result.hasEvaluation && Math.abs(score) < 100000) {
            message = "info depth " + result.completedDepth + " score cp " + score
                + " pv " + result.principalVariation + "\n" + message;
        }
        if (result.hasEvaluation) message += "\n" + String.format("%.2f", score / 100.0);
        for (EngineListener listener : new ArrayList<>(listeners)) {
            if (!isCurrentSearch(id) || destroyed) break;
            listener.OnEngineInfo(message, score / 100.0f);
        }
    }
    // Analysis listeners consume side-to-move scores and convert to White at the view boundary.
    private static int analysisScore(String fen, int whiteScore) {
        return fen.trim().split("\\s+")[1].equals("w") ? whiteScore : -whiteScore;
    }
    private void reportError(long id, Throwable error) {
        updateHandler.post(() -> deliverError(id, error));
    }
    private void deliverError(long id, Throwable error) {
        if (!isCurrentSearch(id) || destroyed) return;
        Log.e("RustLocalEngine", "Search failed", error);
        for (EngineListener listener : new ArrayList<>(listeners)) {
            if (!isCurrentSearch(id) || destroyed) break;
            listener.OnEngineError();
        }
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
        if (worker != null && !destroyed) updateHandler.post(() -> {
            if (!destroyed) for (EngineListener listener : new ArrayList<>(listeners)) listener.OnEngineAborted();
        });
    }
    @Override public synchronized void destroy() { destroyed = true; abort(null); }
}
