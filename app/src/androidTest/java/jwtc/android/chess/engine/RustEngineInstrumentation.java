package jwtc.android.chess.engine;

import android.app.Instrumentation;
import android.os.Bundle;
import android.os.Looper;
import android.os.Handler;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import jwtc.android.chess.services.GameApi;
import jwtc.chess.board.BoardConstants;

/** Device smoke test of the real JNI and EngineSession path, with no Activity. */
public final class RustEngineInstrumentation extends Instrumentation {
    private static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle report = new Bundle();
        try {
            nativeContract();
            sessionMove();
            sessionAbort();
            nativeProgress();
            sessionTimedStop();
            sessionRestart();
            sessionSuppressQueued(false);
            sessionSuppressQueued(true);
            sessionAnalysisAndError();
            sessionForAnalysisSelectsRustForStandardHistory();
            sessionForAnalysisUsesRustForDuck();
            sessionForAnalysisUsesRustForChess960Castling();
            sessionPlaysAChess960Game();
            sessionPuzzleMateScoreIsSideToMove();
            sessionDuckTurns(false);
            sessionDuckTurns(true);
            sessionDuckPendingAndStale();
            duckPositionIdentity();
            report.putString("stream", "Rust engine: replay, progress, timing, stop ordering, abort/restart, navigation, teardown, terminal, errors and Analysis backend selection (Standard, Duck, Chess 960) passed\n");
            finish(-1, report);
        } catch (Throwable error) {
            report.putString("stream", "FAIL: " + error + "\n");
            finish(0, report);
        }
    }
    private void nativeContract() {
        long handle = RustNative.create();
        try {
            RustSearchResult result = RustNative.search(handle, new RustSearchRequest(248,
                RustSearchRequest.Variant.STANDARD, START, new String[]{"e2e4", "e7e5"}, 2, 0, true));
            check(result.searchId == 248 && result.completedDepth == 2 && result.hasEvaluation
                && !result.bestMove.isEmpty(), "Native replay did not complete");
            boolean failed = false;
            try { RustNative.search(handle, new RustSearchRequest(249, RustSearchRequest.Variant.STANDARD,
                START, new String[]{"e2e5"}, 1, 0, true)); }
            catch (IllegalArgumentException expected) { failed = true; }
            check(failed, "Invalid history was accepted");
            RustSearchResult duck = RustNative.search(handle, new RustSearchRequest(250, RustSearchRequest.Variant.DUCK,
                START, new String[]{"e2e4@d5", "e7e5"}, 1, 0, true));
            RustSearchResult completeDuck = RustNative.search(handle, new RustSearchRequest(250, RustSearchRequest.Variant.DUCK,
                START, new String[]{"e2e4@d5"}, 1, 0, true));
            check(duck.bestMove.equals(completeDuck.bestMove) && duck.score == completeDuck.score
                && duck.bestMove.contains("@"), "Pending history not prepared at complete position");
            failed = false;
            try { RustNative.search(handle, new RustSearchRequest(250, RustSearchRequest.Variant.DUCK,
                START, new String[]{"e2e4", "e7e5@d5"}, 1, 0, true)); }
            catch (IllegalArgumentException expected) { failed = true; }
            check(failed, "Incomplete middle history accepted");
            RustSearchResult chess960 = RustNative.search(handle, new RustSearchRequest(251,
                RustSearchRequest.Variant.CHESS_960, "4k3/8/8/8/8/8/8/1R2K1R1 w KQ - 0 1", new String[0], 2, 0, true));
            check(chess960.searchId == 251 && chess960.hasEvaluation && !chess960.bestMove.isEmpty(),
                "Chess 960 variant was rejected by the native search");
        } finally { RustNative.destroy(handle); }
    }
    private void sessionMove() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        EngineSession[] session = new EngineSession[1];
        runOnMainSync(() -> {
            GameApi game = new GameApi();
            session[0] = EngineSession.forRustTesting(game, new Listener() {
                @Override public void OnEngineMove(int move, int duck, int score) {
                    try {
                        check(game.getCurrentPly() == 0 && game.getFEN().equals(START),
                            "Rust changed the live board during search");
                        check(game.applyMove(move, duck) && game.getCurrentPly() == 1,
                            "Rust move could not be applied through GameApi");
                    } catch (Throwable error) { failure.set(error); }
                    finally { done.countDown(); }
                }
                @Override public void OnEngineError() { failure.set(new AssertionError("Session search failed")); done.countDown(); }
            });
            session[0].play(SearchLimit.ply(2));
        });
        try {
            check(done.await(30, TimeUnit.SECONDS), "Session search timed out");
            if (failure.get() != null) throw new AssertionError(failure.get());
        } finally { runOnMainSync(() -> session[0].close()); }
    }
    private void sessionAbort() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        EngineSession[] session = new EngineSession[1];
        runOnMainSync(() -> {
            GameApi game = new GameApi();
            session[0] = EngineSession.forRustTesting(game, new Listener() {
                @Override public void OnEngineMove(int move, int duck, int score) { failure.set(new AssertionError("Aborted search delivered a move")); }
            });
            session[0].play(SearchLimit.ply(10));
            session[0].abort(() -> {
                if (!session[0].isReady() || game.getCurrentPly() != 0) failure.set(new AssertionError("Abort did not leave the game idle"));
                done.countDown();
            });
        });
        try {
            check(done.await(10, TimeUnit.SECONDS), "Abort timed out");
            if (failure.get() != null) throw new AssertionError(failure.get());
        } finally { runOnMainSync(() -> session[0].close()); }
    }
    private void nativeProgress() {
        final long progressHandle = RustNative.create();
        ArrayList<RustSearchResult> progress = new ArrayList<>();
        try {
            RustSearchResult result = RustNative.search(progressHandle, new RustSearchRequest(300,
                RustSearchRequest.Variant.STANDARD, START, new String[0], 0, 60_000, false), event -> {
                check(Looper.myLooper() != Looper.getMainLooper(), "Native search ran on UI thread");
                progress.add(event);
                RustNative.stop(progressHandle);
            });
            check(progress.size() == 1 && result.interrupted && result.completedDepth == 1,
                "Stop did not retain completed progress");
            check(result.searchId == 300 && progress.get(0).searchId == 300
                && result.bestMove.equals(progress.get(0).bestMove)
                && result.nodes >= progress.get(0).nodes && result.elapsedMicros >= progress.get(0).elapsedMicros
                && !result.principalVariation.isEmpty(), "Invalid typed progress");
        } finally { RustNative.destroy(progressHandle); }
        long handle = RustNative.create();
        try {
            RustNative.stop(handle);
            RustSearchResult result = RustNative.search(handle, new RustSearchRequest(301,
                RustSearchRequest.Variant.STANDARD, START, new String[0], 10, 0, true));
            check(result.interrupted && !result.hasEvaluation && result.bestMove.isEmpty(),
                "Pre-depth interruption fabricated a result");
        } finally { RustNative.destroy(handle); }
    }
    private void sessionTimedStop() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        EngineSession[] session = new EngineSession[1];
        AtomicInteger moves = new AtomicInteger();
        runOnMainSync(() -> {
            GameApi game = new GameApi();
            game.newGame();
            session[0] = EngineSession.forRustTesting(game, new Listener() {
                boolean stopping;
                @Override public void OnEngineInfo(String message, float score) {
                    if (stopping) return;
                    stopping = true;
                    session[0].stop(() -> {
                        try {
                            check(Looper.myLooper() == Looper.getMainLooper(), "Completion off main thread");
                            check(moves.get() == 1 && session[0].isReady(), "Stop completed before move delivery");
                        } catch (Throwable error) { failure.set(error); }
                        done.countDown();
                    });
                }
                @Override public void OnEngineMove(int move, int duck, int score) {
                    if (Looper.myLooper() != Looper.getMainLooper()) failure.set(new AssertionError("Move off main thread"));
                    moves.incrementAndGet();
                }
                @Override public void OnEngineError() { failure.set(new AssertionError("Timed search failed")); done.countDown(); }
            });
            session[0].play(SearchLimit.millis(60_000));
        });
        await(done, failure, session);
    }
    private void sessionRestart() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        EngineSession[] session = new EngineSession[1];
        AtomicInteger phase = new AtomicInteger();
        runOnMainSync(() -> {
            GameApi game = new GameApi();
            game.newGame();
            session[0] = EngineSession.forRustTesting(game, new Listener() {
                @Override public void OnEngineMove(int move, int duck, int score) {
                    if (phase.get() != 1) failure.set(new AssertionError("Obsolete move after abort"));
                    done.countDown();
                }
                @Override public void OnEngineError() { failure.set(new AssertionError("Restart failed")); done.countDown(); }
            });
            session[0].play(SearchLimit.ply(10));
            session[0].abort(() -> {
                if (!session[0].isReady()) failure.set(new AssertionError("Restart not safe"));
                phase.set(1);
                session[0].play(SearchLimit.ply(2));
            });
            session[0].play(SearchLimit.ply(1)); // Busy requests must not replace the aborted worker.
        });
        await(done, failure, session);
    }
    private void sessionSuppressQueued(boolean close) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        EngineSession[] session = new EngineSession[1];
        runOnMainSync(() -> {
            GameApi game = new GameApi();
            game.newGame();
            session[0] = EngineSession.forRustTesting(game, new Listener() {
                @Override public void OnEngineMove(int move, int duck, int score) {
                    failure.set(new AssertionError("Obsolete queued move delivered"));
                }
            });
            session[0].play(SearchLimit.ply(1));
            // Keep the main queue blocked while the worker queues its result.
            try { Thread.sleep(200); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            if (close) {
                session[0].close();
                session[0].close();
                session[0].play(SearchLimit.ply(1));
                new Handler(Looper.getMainLooper()).postDelayed(done::countDown, 200);
            } else {
                GameApi.PositionMove move = game.getPositionSnapshot(game.getCurrentNode()).findMove("e2e4");
                game.applyMove(move.move, -1);
                session[0].stop(done::countDown);
            }
        });
        await(done, failure, session);
    }
    private void sessionAnalysisAndError() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        EngineSession[] session = new EngineSession[1];
        AtomicInteger completed = new AtomicInteger();
        runOnMainSync(() -> {
            GameApi game = new GameApi();
            game.newGame();
            session[0] = EngineSession.forRustTesting(game, new Listener() {
                @Override public void OnEngineMove(int move, int duck, int score) {
                    failure.set(new AssertionError("Analysis delivered a Play move"));
                }
                @Override public void onAnalysisComplete(String fen, String best, EngineEvaluation evaluation) {
                    try {
                        check(Looper.myLooper() == Looper.getMainLooper(), "Analysis off main thread");
                        check(best.isEmpty() && evaluation == null, "Terminal mate invented a distance");
                        check(completed.incrementAndGet() == 1, "Duplicate completion");
                        session[0].stop(() -> session[0].analyze("invalid fen", 100));
                    } catch (Throwable error) { failure.set(error); done.countDown(); }
                }
                @Override public void OnEngineError() {
                    if (completed.incrementAndGet() != 2) failure.set(new AssertionError("Wrong or duplicate error"));
                    session[0].stop(done::countDown);
                }
            });
            session[0].analyze("7k/6Q1/5K2/8/8/8/8/8 b - - 0 1", 100);
        });
        await(done, failure, session);
    }
    /** Exercises the production EngineSession.forAnalysis entry point, not forRustTesting. */
    private void sessionForAnalysisSelectsRustForStandardHistory() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        EngineSession[] session = new EngineSession[1];
        runOnMainSync(() -> {
            GameApi game = new GameApi();
            game.newGame();
            GameApi.PositionMove e4 = game.getPositionSnapshot(game.getCurrentNode()).findMove("e2e4");
            game.applyMove(e4.move, -1);
            GameApi.PositionMove e5 = game.getPositionSnapshot(game.getCurrentNode()).findMove("e7e5");
            game.applyMove(e5.move, -1);
            check(game.isStandardVariant(), "Fresh game was not Standard");
            session[0] = EngineSession.forAnalysis(getContext(), game, new Listener() {
                @Override public void OnEngineMove(int move, int duck, int score) {
                    failure.set(new AssertionError("Analysis delivered a Play move"));
                }
                @Override public void onAnalysisComplete(String fen, String best, EngineEvaluation evaluation) {
                    try {
                        check(fen.equals(game.getPositionSnapshot(game.getCurrentNode()).fen), "Wrong position reported");
                        check(best != null && !best.isEmpty(), "Standard analysis with history produced no move");
                    } catch (Throwable error) { failure.set(error); }
                    finally { done.countDown(); }
                }
                @Override public void OnEngineError() { failure.set(new AssertionError("Standard analysis failed")); done.countDown(); }
            }, EngineSession.LOCAL_ENGINE_ID);
            check(session[0].supportsAnalysis(), "Built-in Analysis session did not support analysis");
            session[0].analyzeCurrentPosition(500);
        });
        await(done, failure, session);
    }
    /** Duck Analysis uses the Rust Engine. */
    private void sessionForAnalysisUsesRustForDuck() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        EngineSession[] session = new EngineSession[1];
        runOnMainSync(() -> {
            GameApi game = new GameApi();
            game.newGame(BoardConstants.VARIANT_DUCK);
            check(game.requestMove("e4@d5"), "Duck recorded turn");
            String original = game.getFEN();
            check(!game.isStandardVariant(), "Duck game was reported as Standard");
            session[0] = EngineSession.forAnalysis(getContext(), game, new Listener() {
                @Override public void OnEngineMove(int move, int duck, int score) {
                    failure.set(new AssertionError("Analysis delivered a Play move"));
                }
                @Override public void onAnalysisComplete(String fen, String best, EngineEvaluation evaluation) {
                    try {
                        check(best.contains("@"), "Duck analysis omitted placement");
                        check(game.getFEN().equals(original), "Analysis mutated Duck board");
                    } catch (Throwable error) { failure.set(error); }
                    finally { done.countDown(); }
                }
                @Override public void OnEngineError() { failure.set(new AssertionError("Duck analysis failed")); done.countDown(); }
            }, EngineSession.LOCAL_ENGINE_ID);
            session[0].analyze(game.getPositionSnapshot(game.getRootNode()), 500);
        });
        await(done, failure, session);
    }
    /**
     * A FEN-only position can carry Chess 960 castling rights with no Variant tag at all, and
     * such a game is now Rust-eligible: it must resolve to a move the live board still recognizes.
     */
    private void sessionForAnalysisUsesRustForChess960Castling() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        EngineSession[] session = new EngineSession[1];
        runOnMainSync(() -> {
            GameApi game = new GameApi();
            check(game.initFEN("4k3/8/8/8/8/8/8/1R2K1R1 w KQ - 0 1", true), "Could not load a Chess 960-shaped FEN");
            check(game.isChess960Variant(), "Off-square castling rights were not reported as Chess 960");
            session[0] = EngineSession.forAnalysis(getContext(), game, new Listener() {
                @Override public void OnEngineMove(int move, int duck, int score) {
                    failure.set(new AssertionError("Analysis delivered a Play move"));
                }
                @Override public void onAnalysisComplete(String fen, String best, EngineEvaluation evaluation) {
                    try {
                        check(best != null && !best.isEmpty(), "Chess 960 analysis produced no move");
                        GameApi.PositionMove move = game.getPositionSnapshot(game.getCurrentNode()).findMove(best);
                        check(move != null, "Resolved Chess 960 move " + best + " did not match a legal move");
                    } catch (Throwable error) { failure.set(error); }
                    finally { done.countDown(); }
                }
                @Override public void OnEngineError() { failure.set(new AssertionError("Chess 960 analysis failed")); done.countDown(); }
            }, EngineSession.LOCAL_ENGINE_ID);
            session[0].analyzeCurrentPosition(500);
        });
        await(done, failure, session);
    }
    /**
     * Drives a full Chess 960 Play search through {@link RustLocalEngine}: the returned move,
     * castling or not, must resolve and apply through the live board.
     */
    private void sessionPlaysAChess960Game() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        EngineSession[] session = new EngineSession[1];
        runOnMainSync(() -> {
            GameApi game = new GameApi();
            check(game.initFEN("4k3/8/8/8/8/8/8/1R2K1R1 w KQ - 0 1", true), "Could not load a Chess 960-shaped FEN");
            session[0] = EngineSession.forRustTesting(game, new Listener() {
                @Override public void OnEngineMove(int move, int duck, int score) {
                    try {
                        check(game.applyMove(move, duck) && game.getCurrentPly() == 1,
                            "Chess 960 move could not be applied through GameApi");
                    } catch (Throwable error) { failure.set(error); }
                    finally { done.countDown(); }
                }
                @Override public void OnEngineError() { failure.set(new AssertionError("Chess 960 Play search failed")); done.countDown(); }
            });
            session[0].play(SearchLimit.ply(2));
        });
        await(done, failure, session);
    }
    /** Black to move mates in one: the puzzle session must deliver the side-to-move mate score. */
    private void sessionPuzzleMateScoreIsSideToMove() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        EngineSession[] session = new EngineSession[1];
        runOnMainSync(() -> {
            GameApi game = new GameApi();
            check(game.initFEN("rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq g3 0 2", true),
                "Could not load the Black-to-move mate position");
            session[0] = EngineSession.forPuzzles(game, new Listener() {
                @Override public void OnEngineMove(int move, int duck, int score) {
                    try {
                        check(score == BoardConstants.VALUATION_MATE,
                            "Expected the side-to-move mate score but got " + score);
                    } catch (Throwable error) { failure.set(error); }
                    finally { done.countDown(); }
                }
                @Override public void OnEngineError() { failure.set(new AssertionError("Puzzle search failed")); done.countDown(); }
            });
            session[0].play(SearchLimit.ply(2));
        });
        await(done, failure, session);
    }
    private void duckPositionIdentity() {
        runOnMainSync(() -> {
            GameApi game = new GameApi();
            game.newGame(BoardConstants.VARIANT_DUCK);
            check(game.requestMove("e4@d5") && game.requestMove("e5@e2"), "First Duck path");
            GameApi.PositionSnapshot original = game.getPositionSnapshot(game.getCurrentNode());
            game.undoMove(); game.undoMove();
            check(game.requestMove("e4@a5") && game.requestMove("e5@e2"), "Second Duck path");
            check(original.fen.equals(game.getFEN()), "Expected equal final FENs");
            check(!game.isCurrentPosition(original), "Duck history identity lost");
        });
    }
    private void sessionDuckTurns(boolean terminal) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        EngineSession[] session = new EngineSession[1];
        runOnMainSync(() -> {
            GameApi game = new GameApi();
            game.newGame(BoardConstants.VARIANT_DUCK);
            if (terminal) check(game.initFEN("4k3/4R3/8/3$4/8/8/8/4K3 w - - 0 1", true), "Duck terminal setup");
            session[0] = EngineSession.forPlay(getContext(), game, new Listener() {
                @Override public void OnEngineMove(int move, int duck, int score) {
                    try {
                        check(terminal ? duck == -1 : duck >= 0, "Duck result placement");
                        check(game.applyMove(move, duck), "Duck result application");
                        check(!game.hasPendingDuckMove(), "Engine left incomplete turn");
                        GameApi.PositionSnapshot snapshot = game.getPositionSnapshot(game.getCurrentNode());
                        RustSearchRequest request = RustSearchRequest.fromSnapshot(252, snapshot, 1, 0, false,
                            RustSearchRequest.Variant.DUCK);
                        check(request.getHistory().length == 1, "Duck history length");
                        check(terminal ? request.getHistory()[0].equals("e7e8")
                            : request.getHistory()[0].contains("@"), "Duck history coordinate");
                        if (terminal) check(game.isEnded(), "King capture did not end game");
                        else {
                            String fen = snapshot.fen;
                            game.undoMove();
                            game.nextMove();
                            check(game.getFEN().equals(fen), "History lost duck placement");
                        }
                    } catch (Throwable error) { failure.set(error); }
                    finally { done.countDown(); }
                }
                @Override public void OnEngineError() { failure.set(new AssertionError("Duck Play failed")); done.countDown(); }
            });
            session[0].selectForPlay(false, EngineSession.LOCAL_ENGINE_ID, true, false);
            session[0].play(SearchLimit.ply(2));
        });
        await(done, failure, session);
    }
    private void sessionDuckPendingAndStale() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        EngineSession[] session = new EngineSession[1];
        AtomicInteger callbacks = new AtomicInteger();
        runOnMainSync(() -> {
            GameApi game = new GameApi();
            game.newGame(BoardConstants.VARIANT_DUCK);
            GameApi.PositionSnapshot root = game.getPositionSnapshot(game.getCurrentNode());
            session[0] = EngineSession.forAnalysis(getContext(), game, new Listener() {
                @Override public void OnEngineStarted() { callbacks.incrementAndGet(); }
                @Override public void OnEngineMove(int move, int duck, int score) { failure.set(new AssertionError("Stale Duck move")); }
                @Override public void onAnalysisComplete(String fen, String best, EngineEvaluation value) { failure.set(new AssertionError("Stale Duck analysis")); }
                @Override public void OnEngineError() { failure.set(new AssertionError("Pending Duck error")); }
            }, EngineSession.LOCAL_ENGINE_ID);
            check(game.requestMove(52, 36), "Interactive piece move");
            String pending = game.getFEN();
            session[0].play(SearchLimit.ply(1));
            session[0].analyzeCurrentPosition(100);
            session[0].analyze(root, 100);
            session[0].analyze(START, 100);
            check(session[0].isReady() && game.getFEN().equals(pending), "Pending request changed board or dispatched");
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    check(callbacks.get() == 0, "Pending search dispatched");
                    check(game.requestDuckMove(27), "Complete interactive placement");
                    session[0].analyzeCurrentPosition(100);
                    game.undoMove();
                    session[0].stop(done::countDown);
                } catch (Throwable error) { failure.set(error); done.countDown(); }
            });
        });
        await(done, failure, session);
    }
    private void await(CountDownLatch done, AtomicReference<Throwable> failure, EngineSession[] session) throws Exception {
        try {
            check(done.await(30, TimeUnit.SECONDS), "Session check timed out");
            if (failure.get() != null) throw new AssertionError(failure.get());
        } finally { runOnMainSync(() -> session[0].close()); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static class Listener implements EngineListener {
        public void OnEngineMove(int move, int duck, int score) {}
        public void OnEngineInfo(String message, float value) {}
        public void OnEngineStarted() {}
        public void OnEngineAborted() {}
        public void OnEngineError() {}
    }
}
