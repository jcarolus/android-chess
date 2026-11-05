package jwtc.android.chess.analyze;

import android.app.Instrumentation;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import jwtc.android.chess.engine.EngineEvaluation;
import jwtc.android.chess.engine.EngineSession;
import jwtc.android.chess.services.GameApi;

/** Exercises Analysis ownership through the real Local Engine, without an Activity. */
public final class AnalysisInstrumentation {
    public static void verify(Instrumentation instrumentation) throws Exception {
        AtomicReference<Analysis> owner = new AtomicReference<>();
        AtomicReference<GameApi> game = new AtomicReference<>();
        AtomicReference<String> parentKey = new AtomicReference<>();
        AtomicReference<String> currentKey = new AtomicReference<>();
        CountDownLatch parent = new CountDownLatch(1);
        CountDownLatch complete = new CountDownLatch(1);
        try {
            instrumentation.runOnMainSync(
                    () -> {
                        GameApi board = new GameApi();
                        check(board.loadPGN("1. e4 *"), "Cannot load Analysis Game");
                        game.set(board);
                        Analysis analysis =
                                new Analysis(
                                        instrumentation.getTargetContext(),
                                        board,
                                        new Analysis.Listener() {
                                            public void onChanged() {
                                                AnalysisResults.Saved saved = owner.get().save();
                                                if (saved.results.containsKey(parentKey.get())) {
                                                    parent.countDown();
                                                }
                                                if (saved.results.containsKey(currentKey.get())) {
                                                    complete.countDown();
                                                }
                                            }

                                            public void onEvaluation(Float value) {}
                                        });
                        owner.set(analysis);
                        analysis.resume(EngineSession.LOCAL_ENGINE_ID, 100, null);
                        analysis.selectPosition();
                        parentKey.set(
                                analysis.position(board.getCurrentNode().getParent())
                                        .positionCommand);
                        currentKey.set(analysis.position(board.getCurrentNode()).positionCommand);
                        analysis.startNextAnalysis();
                    });
            check(parent.await(10, TimeUnit.SECONDS), "Parent Analysis did not complete");
            check(complete.await(10, TimeUnit.SECONDS), "Current Analysis did not complete");
            instrumentation.runOnMainSync(
                    () -> {
                        Analysis analysis = owner.get();
                        AnalysisResults.Saved saved = analysis.save();
                        check(
                                new ArrayList<>(saved.results.keySet())
                                        .get(0)
                                        .equals(parentKey.get()),
                                "Analysis did not evaluate the parent first");
                        analysis.pause();
                        analysis.onAnalysisComplete(
                                game.get().getFEN(), "e7e5", new EngineEvaluation(false, 999, 1));
                        check(
                                analysis.save().results.get(currentKey.get())
                                        == saved.results.get(currentKey.get()),
                                "Paused Analysis accepted a completion");
                        Bundle bundle = new Bundle();
                        Map<String, AnalysisResults.Result> savedResults =
                                new LinkedHashMap<>(saved.results);
                        savedResults.put(
                                "unavailable", new AnalysisResults.Result((String) null, null));
                        new AnalysisResults.Saved(saved.engine, saved.millis, savedResults)
                                .writeTo(bundle);
                        AnalysisResults.Saved restored = AnalysisResults.Saved.readFrom(bundle);
                        check(
                                restored.results.containsKey("unavailable")
                                        && restored.results.get("unavailable").evaluation == null,
                                "Unavailable result did not survive saved state");
                        analysis.resume(EngineSession.LOCAL_ENGINE_ID, 100, restored);
                        analysis.selectPosition();
                        AnalysisResults.Result result =
                                analysis.result(analysis.position(game.get().getCurrentNode()));
                        check(
                                result != null,
                                "Matching settings did not restore completed Analysis");
                        check(
                                result.preferredMove != null
                                        && result.preferredMove.uci.equals(result.bestMove),
                                "Restored best move did not resolve against the Position");
                        check(
                                analysis.configure(EngineSession.LOCAL_ENGINE_ID, 200),
                                "Settings were ignored");
                        check(
                                analysis.save().results.isEmpty(),
                                "Settings retained stale Analysis");
                        GameApi terminal = new GameApi();
                        check(
                                terminal.loadPGN("[FEN \"7k/6Q1/5K2/8/8/8/8/8 b - - 0 1\"]\n\n*"),
                                "Cannot load terminal Position");
                        AnalysisResults.Result terminalResult =
                                new AnalysisResults()
                                        .result(
                                                terminal.getPositionSnapshot(
                                                        terminal.getCurrentNode()));
                        check(
                                terminalResult != null
                                        && terminalResult.evaluation.mate
                                        && terminalResult.evaluation.value == 0,
                                "Terminal Position did not produce mate result");
                        GameApi repetition = new GameApi();
                        check(
                                repetition.loadPGN("1. Nf3 Nf6 2. Ng1 Ng8 3. Nf3 Nf6 4. Ng1 Ng8 *"),
                                "Cannot load repeated Position");
                        AnalysisResults.Result draw =
                                new AnalysisResults()
                                        .result(
                                                repetition.getPositionSnapshot(
                                                        repetition.getCurrentNode()));
                        check(
                                draw != null && !draw.evaluation.mate && draw.evaluation.value == 0,
                                "Repeated Position did not produce a draw result");
                    });
        } finally {
            instrumentation.runOnMainSync(
                    () -> {
                        if (owner.get() != null) {
                            owner.get().pause();
                        }
                    });
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
