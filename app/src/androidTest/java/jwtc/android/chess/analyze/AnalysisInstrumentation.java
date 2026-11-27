package jwtc.android.chess.analyze;

import android.app.Instrumentation;
import android.os.Bundle;
import android.os.Looper;
import android.os.Parcel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import jwtc.android.chess.engine.EngineEvaluation;
import jwtc.android.chess.engine.EngineSession;
import jwtc.android.chess.services.GameApi;
import jwtc.chess.GameTree.Node;
import jwtc.chess.Variant;
import jwtc.chess.board.BoardConstants;
import jwtc.chess.board.BoardPosition;

/** Exercises Analysis ownership through the real Local Engine, without an Activity. */
public final class AnalysisInstrumentation {
    public static void verify(Instrumentation instrumentation) throws Exception {
        verifyBoardDescriptions(instrumentation);
        verifyRecordedPositions();
        verifyStartingPosition();
        verifyStandard(instrumentation);
        verifyReportState();
        verifyReport(instrumentation);
        verifyVariant(instrumentation, "[Variant \"Duck\"]\n\n1. e4@d5 *", true);
        verifyVariant(
                instrumentation,
                "[Variant \"King of the Hill\"]\n[FEN \"7k/8/8/8/8/3K4/8/8 w - - 0 1\"]\n\n*",
                false);
    }

    public static void verifyStartingPosition() {
        for (boolean recorded : new boolean[] {false, true}) {
            GameApi game = recorded ? GameApi.forAnalysis() : new GameApi();
            check(
                    game.loadPGN(
                            "[Event \"Study\"]\n[White \"Player\"]\n\n"
                                    + "{old intro} 1. e4 $1 {new intro} (1. d4 d5) "
                                    + "e5 ( {Sicilian} 1... c5 $5 {counterplay} 2. Nf3 "
                                    + "(2. Nc3) d6) 2. Nf3 Nc6 1-0"),
                    "Cannot load starting-position study");
            String original = game.exportFullPGN();
            Node selected = game.getMainLineNodes().get(0);
            check(game.goTo(selected), "Cannot select new start");
            String fen = game.getFEN();
            check(game.useCurrentPositionAsStart(), "Cannot change starting Position");
            check(game.getCurrentNode() == game.getRootNode(), "Did not stay at new start");
            check(game.getFEN().equals(fen), "Starting Position lost FEN state");
            check("1".equals(game.pgnTags.get("SetUp")), "Missing standard SetUp tag");
            check(!game.pgnTags.containsKey("Setup"), "Retained obsolete Setup spelling");
            check("Study".equals(game.pgnTags.get("Event")), "Lost study title");
            check("Player".equals(game.pgnTags.get("White")), "Lost game metadata");
            check(game.getRootComment().equals("new intro"), "Lost Position comment");
            check(game.getContinuations(game.getRootNode()).size() == 2, "Lost root variations");
            check(game.exportFullPGN().contains("[Result \"1-0\"]"), "Lost main-line result");
            check(
                    game.exportMovesPGN()
                            .equals(
                                    "{new intro} 1... e5 ( {Sicilian} 1... c5 $5 {counterplay} "
                                            + "2. Nf3 ( 2. Nc3 ) d6 ) 2. Nf3 Nc6"),
                    "Retained tree lost moves or annotations");
            check(
                    game.getPositionSnapshot(game.getRootNode())
                            .positionCommand
                            .equals("position fen " + fen),
                    "New start retained earlier engine history");
            for (Node node : game.getMainLineNodes()) {
                check(game.goTo(node), "Cannot navigate rebuilt main line");
                check(game.getPositionSnapshot(node) != null, "Missing rebuilt Position");
            }
            check(game.goTo(game.getRootNode()), "Cannot navigate to new root");
            String trimmed = game.exportFullPGN();
            check(!game.useCurrentPositionAsStart(), "Allowed trimming existing start");
            check(trimmed.equals(game.exportFullPGN()), "Rejected trim changed Game");

            check(game.loadPGN(original), "Cannot reload original study");
            Node variation = game.getMainLineNodes().get(0).getChildren().get(1);
            check(game.goTo(variation), "Cannot select variation start");
            check(game.useCurrentPositionAsStart(), "Cannot start inside variation");
            check(game.exportFullPGN().contains("[Result \"*\"]"), "Kept unrelated result");
            check(
                    game.exportMovesPGN().equals("{counterplay} 2. Nf3 ( 2. Nc3 ) d6"),
                    "Variation start retained earlier branches");
            check(game.goTo(game.getMainLineNodes().get(1)), "Cannot select endpoint");
            check(game.useCurrentPositionAsStart(), "Cannot make position-only study");
            check(game.getMainLineNodes().isEmpty(), "Position-only study retained moves");
            check(game.loadPGN(game.exportFullPGN()), "Position-only PGN cannot round trip");
        }

        for (String pgn :
                new String[] {
                    "[Variant \"Duck\"]\n\n1. e4@d5 e5@d4 2. Nf3@c6 *",
                    "[Variant \"King of the Hill\"]\n\n1. e4 e5 2. Nf3 *",
                    "[Variant \"Chess960\"]\n"
                            + "[FEN \"4k3/8/8/8/8/8/8/5K1R w K - 0 23\"]\n\n23. O-O Kd7 *"
                }) {
            GameApi game = GameApi.forAnalysis();
            check(game.loadPGN(pgn), "Cannot load Variant study");
            Variant variant = game.variant();
            check(game.goTo(game.getMainLineNodes().get(0)), "Cannot select Variant start");
            String fen = game.getFEN();
            check(game.useCurrentPositionAsStart(), "Cannot trim Variant study");
            check(game.variant() == variant, "Starting Position changed Variant");
            check(game.getFEN().equals(fen), "Starting Position changed Variant FEN");
            check(game.loadPGN(game.exportFullPGN()), "Variant PGN cannot round trip");
            check(game.variant() == variant, "Variant tag did not survive round trip");
        }

        GameApi repetition = GameApi.forAnalysis();
        check(
                repetition.loadPGN("1. Nf3 Nf6 2. Ng1 Ng8 3. Nf3 Nf6 4. Ng1 Ng8 *"),
                "Cannot load repetition study");
        check(repetition.getState() == BoardConstants.DRAW_REPEAT, "Missing original repetition");
        check(repetition.useCurrentPositionAsStart(), "Cannot trim repetition study");
        check(
                repetition.getState() != BoardConstants.DRAW_REPEAT,
                "New Starting Position retained discarded repetition history");
    }

    private static void verifyReportState() {
        AnalysisResults results = new AnalysisResults();
        results.configure("local", 100, null);
        Set<String> histories = new HashSet<>();
        String history = "position startpos moves";
        for (int i = 0; i < 500; i++) {
            history += " g1f3";
            histories.add(history);
        }
        results.retain(histories);
        for (String key : histories) {
            results.put(
                    key, new AnalysisResults.Result("g1f3", new EngineEvaluation(false, 30, 10)));
        }
        Bundle bundle = new Bundle();
        results.save().writeTo(bundle);
        Parcel parcel = Parcel.obtain();
        try {
            bundle.writeToParcel(parcel, 0);
            check(parcel.dataSize() < 250000, "Report state duplicates full histories");
        } finally {
            parcel.recycle();
        }
        AnalysisResults restored = new AnalysisResults();
        restored.configure("local", 100, AnalysisResults.Saved.readFrom(bundle));
        check(
                restored.save().retained.equals(histories),
                "Report retention did not survive recreation");
        for (String key : histories) {
            check(restored.get(key).evaluation.value == 30, "Report result lost during recreation");
        }
    }

    private static void verifyReport(Instrumentation instrumentation) throws Exception {
        AtomicReference<Analysis> owner = new AtomicReference<>();
        AtomicReference<GameApi> game = new AtomicReference<>();
        AtomicReference<Node> displayed = new AtomicReference<>();
        CountDownLatch partial = new CountDownLatch(1);
        CountDownLatch complete = new CountDownLatch(1);
        try {
            instrumentation.runOnMainSync(
                    () -> {
                        GameApi board = GameApi.forAnalysis();
                        check(
                                board.loadPGN("1. e4 e5 (1... c5 2. Nf3) 2. Nf3 Nc6 3. Bb5 a6 *"),
                                "Cannot load report game");
                        Node variation =
                                board.getContinuations(board.getMainLineNodes().get(0)).get(1);
                        check(board.goTo(variation), "Cannot select report variation");
                        game.set(board);
                        displayed.set(variation);
                        Analysis analysis =
                                new Analysis(
                                        instrumentation.getTargetContext(),
                                        board,
                                        new Analysis.Listener() {
                                            public void onChanged() {
                                                Analysis analysis = owner.get();
                                                check(
                                                        board.getCurrentNode() == displayed.get(),
                                                        "Report moved the displayed board");
                                                if (analysis.isReporting()
                                                        && analysis.reportCompleted() >= 2) {
                                                    analysis.cancelReport();
                                                    partial.countDown();
                                                } else if (!analysis.isReporting()
                                                        && analysis.reportCompleted() == 7) {
                                                    complete.countDown();
                                                }
                                            }

                                            public void onEvaluation(Float value) {}
                                        });
                        owner.set(analysis);
                        analysis.resume(EngineSession.LOCAL_ENGINE_ID, 100, null);
                        analysis.selectPosition();
                        check(analysis.startReport(), "Report did not start");
                    });
            check(
                    partial.await(10, TimeUnit.SECONDS),
                    "Report did not produce cancellable partial work");
            instrumentation.runOnMainSync(
                    () -> {
                        Analysis analysis = owner.get();
                        check(!analysis.isReporting(), "Report ignored cancellation");
                        check(
                                analysis.reportCompleted() >= 2,
                                "Cancellation discarded completed report positions");
                        // A finished partial pass must not trigger cancellation in the listener
                        // again.
                        owner.set(
                                new Analysis(
                                        instrumentation.getTargetContext(),
                                        game.get(),
                                        new Analysis.Listener() {
                                            public void onChanged() {
                                                Analysis current = owner.get();
                                                check(
                                                        game.get().getCurrentNode()
                                                                == displayed.get(),
                                                        "Report changed selected variation");
                                                if (!current.isReporting()
                                                        && current.reportCompleted() == 7) {
                                                    complete.countDown();
                                                }
                                            }

                                            public void onEvaluation(Float value) {}
                                        }));
                        AnalysisResults.Saved saved = analysis.save();
                        analysis.pause();
                        Analysis resumed = owner.get();
                        resumed.resume(EngineSession.LOCAL_ENGINE_ID, 100, saved);
                        resumed.selectPosition();
                        check(resumed.startReport(), "Report could not resume");
                    });
            check(complete.await(15, TimeUnit.SECONDS), "Report did not finish the main line");
            instrumentation.runOnMainSync(
                    () -> {
                        Analysis analysis = owner.get();
                        check(
                                analysis.report().complete,
                                "Report finished without usable evaluations");
                        check(analysis.reportNodes().size() == 7, "Report included variations");
                        check(
                                Double.isFinite(analysis.report().whiteAccuracy),
                                "White accuracy is missing");
                        check(
                                Double.isFinite(analysis.report().blackAccuracy),
                                "Black accuracy is missing");
                        Node target = analysis.reportNodes().get(3);
                        check(
                                game.get().goTo(target) && game.get().getCurrentNode() == target,
                                "Report navigation did not select the position after the move");
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

    private static void verifyBoardDescriptions(Instrumentation instrumentation) throws Exception {
        int e4 = BoardPosition.square("e4");
        int d5 = BoardPosition.square("d5");
        CountDownLatch ready = new CountDownLatch(1);
        AtomicReference<AnalysisBoardDescriptions> owner = new AtomicReference<>();
        AtomicReference<int[]> delivered = new AtomicReference<>();
        try {
            instrumentation.runOnMainSync(
                    () -> {
                        AnalysisBoardDescriptions descriptions =
                                new AnalysisBoardDescriptions(
                                        () -> {
                                            if (Looper.myLooper() == Looper.getMainLooper()) {
                                                delivered.set(
                                                        owner.get()
                                                                .attackersFor(
                                                                        e4, BoardConstants.BLACK));
                                            }
                                            ready.countDown();
                                        });
                        owner.set(descriptions);
                        descriptions.show(Variant.STANDARD, "7k/8/8/8/8/8/8/K7 w - - 0 1");
                        descriptions.show(Variant.STANDARD, "7k/8/8/3p4/8/8/8/K7 w - - 0 1");
                        check(
                                descriptions.attackersFor(e4, BoardConstants.BLACK).length == 0,
                                "Square exposed attack details before delivery");
                    });
            check(ready.await(5, TimeUnit.SECONDS), "No square description delivered");
            check(
                    Arrays.equals(delivered.get(), new int[] {d5}),
                    "Square received stale attackers or a callback off the UI thread");
            instrumentation.runOnMainSync(
                    () -> {
                        owner.get().clear();
                        check(
                                owner.get().attackersFor(e4, BoardConstants.BLACK).length == 0,
                                "Cleared Position retained attack details");
                    });
        } finally {
            instrumentation.runOnMainSync(
                    () -> {
                        if (owner.get() != null) {
                            owner.get().close();
                        }
                    });
        }
    }

    private static void verifyRecordedPositions() {
        GameApi game = GameApi.forAnalysis();
        for (String pgn :
                new String[] {
                    "1. Nf3 Nf6 (1... d5 2. d4) 2. Ng1 Ng8 3. Nf3 Nf6 4. Ng1 Ng8 *",
                    "[Variant \"Duck\"]\n\n1. e4@d5 (1. d4@e5 d5@e4) e5@d4 2. Nf3@c6 *",
                    "[Variant \"Fischerandom\"]\n"
                            + "[FEN \"4k3/8/8/8/8/8/8/1R2K1R1 w KQ - 0 1\"]\n\n"
                            + "1. O-O (1. O-O-O) *"
                }) {
            // Compare with the live board's replay, including history-dependent terminal states.
            GameApi replayed = new GameApi();
            check(game.loadPGN(pgn) && replayed.loadPGN(pgn), "Cannot load recorded positions");
            verifyLine(game, game.getRootNode(), replayed, replayed.getRootNode());
        }
        String fen = game.getFEN();
        check(!game.loadPGN("1. e4 e5 2. Ke4 *"), "Accepted an invalid replacement");
        check(
                game.variant() == Variant.CHESS_960 && fen.equals(game.getFEN()),
                "Rollback lost Chess 960");

        check(game.loadPGN("1. Nf3 Nf6 2. Ng1 Ng8 3. Nf3 Nf6 4. Ng1 *"), "Cannot load repetition");
        check(
                game.goTo(game.getRootNode()) && game.goTo(game.getMainLineNodes().get(6)),
                "Cannot return to recorded position");
        check(game.requestMove("Ng8"), "Cannot play from recorded position");
        check(game.getEngineState() == BoardConstants.DRAW_REPEAT, "Restored board lost history");
    }

    private static void verifyLine(GameApi game, Node node, GameApi replayed, Node reference) {
        Node displayed = game.getCurrentNode();
        GameApi.PositionSnapshot snapshot = game.getPositionSnapshot(node);
        check(game.getCurrentNode() == displayed, "Reading a recorded position moved the board");
        check(game.goTo(node) && replayed.goTo(reference), "Cannot navigate to recorded position");
        GameApi.PositionSnapshot expected = replayed.getPositionSnapshot(reference);
        check(
                snapshot.fen.equals(expected.fen) && snapshot.fen.equals(game.getFEN()),
                "Recorded position differs from replay");
        check(
                snapshot.state == expected.state && snapshot.state == game.getEngineState(),
                "Recorded state differs from replay");
        check(snapshot.positionCommand.equals(expected.positionCommand), "Move history differs");
        check(snapshot.moves.size() == expected.moves.size(), "Legal move count differs");
        for (GameApi.PositionMove move : expected.moves) {
            GameApi.PositionMove recorded = snapshot.findMove(move.uci);
            check(recorded != null && recorded.san.equals(move.san), "Recorded SAN differs");
        }
        for (int i = 0; i < node.getChildren().size(); i++) {
            verifyLine(game, node.getChildren().get(i), replayed, reference.getChildren().get(i));
        }
    }

    private static void verifyStandard(Instrumentation instrumentation) throws Exception {
        AtomicReference<Analysis> owner = new AtomicReference<>();
        AtomicReference<GameApi> game = new AtomicReference<>();
        AtomicReference<String> parentKey = new AtomicReference<>();
        AtomicReference<String> currentKey = new AtomicReference<>();
        CountDownLatch parent = new CountDownLatch(1);
        CountDownLatch complete = new CountDownLatch(1);
        try {
            instrumentation.runOnMainSync(
                    () -> {
                        GameApi board = GameApi.forAnalysis();
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
                        GameApi board = game.get();
                        check(board.stepBack(), "Cannot step back in the game");
                        analysis.selectPosition();
                        Node root = board.getCurrentNode();
                        Analysis.Summary earlier = analysis.summary(root);
                        AnalysisResults.Result rootResult =
                                analysis.result(analysis.position(root));
                        check(
                                earlier.recommendation != null
                                        && earlier.recommendation.uci.equals(rootResult.bestMove),
                                "Stepping back hid the recommendation for the displayed position");
                        check(
                                board.createVariation(root, earlier.recommendation.san) != null,
                                "Cannot play the recommendation from the displayed position");
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

    private static void verifyVariant(Instrumentation instrumentation, String pgn, boolean duck)
            throws Exception {
        AtomicReference<Analysis> owner = new AtomicReference<>();
        GameApi[] game = new GameApi[1];
        CountDownLatch complete = new CountDownLatch(1);
        String preferredEngine = "preferred-oex";
        try {
            instrumentation.runOnMainSync(
                    () -> {
                        GameApi board = GameApi.forAnalysis();
                        check(board.loadPGN(pgn), "Cannot load variant game");
                        game[0] = board;
                        Analysis analysis =
                                new Analysis(
                                        instrumentation.getTargetContext(),
                                        board,
                                        new Analysis.Listener() {
                                            public void onChanged() {
                                                if (owner.get()
                                                        .summary(board.getCurrentNode())
                                                        .ready) {
                                                    complete.countDown();
                                                }
                                            }

                                            public void onEvaluation(Float value) {}
                                        });
                        owner.set(analysis);
                        analysis.resume(preferredEngine, 500, null);
                        check(analysis.isBuiltin(), "Variant Analysis must use the local engine");
                        analysis.selectPosition();
                        analysis.startNextAnalysis();
                    });
            check(complete.await(10, TimeUnit.SECONDS), "Variant Analysis did not complete");
            instrumentation.runOnMainSync(
                    () -> {
                        Analysis analysis = owner.get();
                        GameApi board = game[0];
                        Node current = board.getCurrentNode();
                        AnalysisResults.Result result = analysis.result(analysis.position(current));
                        check(result != null && result.evaluation != null, "Missing evaluation");
                        check(result.preferredMove != null, "Missing recommended turn");
                        Bundle bundle = new Bundle();
                        analysis.save().writeTo(bundle);
                        AnalysisResults.Saved saved = AnalysisResults.Saved.readFrom(bundle);
                        analysis.reset();
                        analysis.resume(preferredEngine, 500, saved);
                        analysis.selectPosition();
                        GameApi.PositionMove restored = analysis.summary(current).recommendation;
                        check(restored != null, "Recommendation did not survive restoration");
                        check(
                                restored.uci.equals(result.preferredMove.uci),
                                "Restoration changed the recommended turn");
                        Node continuation = board.createVariation(current, restored.san);
                        check(continuation != null, "Cannot play the recommended turn");
                        if (duck) {
                            check(
                                    restored.duckMove >= 0
                                            && continuation.getEntry().duckMove == restored.duckMove
                                            && board.getDuckPos() == restored.duckMove
                                            && !board.hasPendingDuckMove(),
                                    "Recommendation lost its duck placement");
                        } else {
                            check(board.getState() == BoardConstants.HILL_WIN, "Missed hill win");
                            analysis.selectPosition();
                            check(
                                    AnalysisResults.terminalWinner(analysis.position(continuation))
                                            == BoardConstants.WHITE,
                                    "Hill result did not identify the winner");
                        }
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
