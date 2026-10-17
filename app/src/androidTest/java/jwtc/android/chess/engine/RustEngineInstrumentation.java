package jwtc.android.chess.engine;

import android.app.Instrumentation;
import android.os.Bundle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import jwtc.android.chess.services.GameApi;
import jwtc.chess.JNI;

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
            report.putString("stream", "Rust engine: native replay, invalid input, session move and abort passed\n");
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
            failed = false;
            try { RustNative.search(handle, new RustSearchRequest(250, RustSearchRequest.Variant.DUCK,
                START, new String[0], 1, 0, true)); }
            catch (IllegalArgumentException expected) { failed = true; }
            check(failed, "Unsupported variant was accepted");
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
                        check(game.getCurrentPly() == 0 && JNI.getInstance().toFEN().equals(START),
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
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static class Listener implements EngineListener {
        public void OnEngineMove(int move, int duck, int score) {}
        public void OnEngineInfo(String message, float value) {}
        public void OnEngineStarted() {}
        public void OnEngineAborted() {}
        public void OnEngineError() {}
    }
}
