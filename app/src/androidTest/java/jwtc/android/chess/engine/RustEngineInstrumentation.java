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
import jwtc.android.chess.services.EcoService;
import jwtc.android.chess.services.HMap;
import jwtc.chess.JNI;
import org.json.JSONArray;
import org.json.JSONObject;
import jwtc.chess.board.BoardConstants;

/** Device smoke test of the real JNI and EngineSession path, with no Activity. */
public final class RustEngineInstrumentation extends Instrumentation {
    private static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private boolean ecoOnly;
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        ecoOnly = arguments != null && "eco".equals(arguments.getString("suite"));
        start();
    }
    @Override public void onStart() {
        Bundle report = new Bundle();
        try {
            if (ecoOnly) {
                ecoLookupPreservesLivePosition();
                report.putString("stream", "ECO lookup, legacy hash/SAN parity, and live-position preservation passed\n");
                finish(-1, report);
                return;
            }
            boardOwnership();
            boardAdapterParity();
            nativeContract();
            nativeBoardContract();
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
            gameApiSanDisambiguationUsesRustBoardApi();
            ecoLookupPreservesLivePosition();
            report.putString("stream", "Rust engine: replay, progress, timing, stop ordering, abort/restart, navigation, teardown, terminal, errors, Analysis backend selection (Standard, Duck, Chess 960), the stateless board-operations API, GameApi SAN disambiguation, and ECO lookup/non-mutation passed\n");
            finish(-1, report);
        } catch (Throwable error) {
            report.putString("stream", "FAIL: " + error + "\n");
            finish(0, report);
        }
    }
    private void boardAdapterParity() {
        JNI reference = JNI.getInstance();
        String[] starts = { START, "4k3/8/8/8/8/8/8/1R2K1R1 w KQ - 0 1",
            START.replace("8/8/8/8", "8/7$/8/8"), "4k3/P7/8/3pP3/8/8/7p/4K3 w - d6 0 1" };
        java.util.Random random = new java.util.Random(271);
        for (String start : starts) {
            RustBoard board = new RustBoard();
            check(board.initFEN(start) && reference.initFEN(start), "Parity setup failed");
            for (int ply = 0; ply < 60; ply++) {
                check(board.toFEN().equals(reference.toFEN()), "Adapter FEN differs at " + board.toFEN());
                check(board.getState() == reference.getState(), "Adapter state differs at " + board.toFEN());
                check(board.getDuckPos() == reference.getDuckPos() && board.getMyDuckPos() == reference.getMyDuckPos(),
                    "Adapter duck state differs at " + board.toFEN());
                check(board.getHashKey() == reference.getHashKey(), "Adapter ECO hash differs at " + board.toFEN());
                if (board.isEnded() != 0) break;
                java.util.Set<Integer> expected = new java.util.HashSet<>();
                for (int i = 0; i < reference.getMoveArraySize(); i++) expected.add(reference.getMoveArrayAt(i));
                java.util.Set<Integer> actual = new java.util.HashSet<>();
                for (RustBoard.LegalMove move : board.legalMoves()) actual.add(move.packed);
                check(expected.equals(actual), "Packed legal moves differ at " + board.toFEN());
                String[] turns = RustBoardResult.words(RustBoardNative.legalMoves(board.variant(), board.initialFen(), board.history()).moves);
                String turn = turns[random.nextInt(turns.length)];
                String piece = turn.split("@")[0];
                int packed = 0;
                for (RustBoard.LegalMove move : board.legalMoves()) if (move.coordinate.equals(piece)) packed = move.packed;
                check(board.move(packed) != 0 && reference.move(packed) != 0, "Parity move rejected");
                if (turn.contains("@")) {
                    int duck = jwtc.chess.board.BoardPosition.square(turn.split("@")[1]);
                    check(board.requestDuckMove(duck) != 0 && reference.requestDuckMove(duck) != 0, "Parity duck rejected");
                }
            }
        }
    }

    private void boardOwnership() {
        runOnMainSync(() -> {
            GameApi game = new GameApi();
            for (String san : new String[]{"Nf3", "Nf6", "Ng1", "Ng8", "Nf3", "Nf6", "Ng1", "Ng8"})
                check(game.requestMove(san), "Repetition move failed: " + san);
            check(game.getEngineState() == BoardConstants.DRAW_REPEAT, "Queries lost repetition history");
            check(game.stepBack() && game.getEngineState() != BoardConstants.DRAW_REPEAT, "Undo retained repetition draw");
            check(game.stepForward() && game.getEngineState() == BoardConstants.DRAW_REPEAT, "Redo lost repetition draw");
            String fen = game.getFEN();
            JNI.getInstance().newGame();
            check(fen.equals(game.getFEN()) && game.getEngineState() == BoardConstants.DRAW_REPEAT,
                "Legacy board mutation changed Java state");
            GameApi observer = new GameApi(true);
            check(observer.getFEN().equals(fen), "Shared board lost its position");
            game.newGame();
            check(observer.getFEN().equals(START), "Board sharing stopped observing changes");
            check(game.loadPGN("1. e4 e5 (1... c5 2. Nf3) 2. Nf3 *"), "Variation import failed");
            String pgn = game.exportFullPGN();
            fen = game.getFEN();
            check(!game.loadPGN("1. e4 e5 2. Ke4 *"), "Illegal PGN accepted");
            check(fen.equals(game.getFEN()) && pgn.equals(game.exportFullPGN()), "Failed import changed game");
            game.newGameRandomFischer(518);
            check(game.isChess960Variant(), "Chess960 seed 518 lost its variant");
            game.restoreCurrentBoard();
            check(game.isChess960Variant(), "Restoring Chess960 lost its variant");
            check(game.initFEN("4k3/8/8/8/8/8/8/1R2K1R1 w KQ - 0 1", true), "Chess960 setup failed");
            check(game.requestMove("O-O") && game.isChess960Variant(), "Castling lost Chess960 variant");
            game.restoreCurrentBoard();
            check(game.isChess960Variant(), "Restored castled position lost Chess960 variant");
            check(game.initFEN("4k3/8/8/8/8/8/8/R5KR w KQ - 0 1", true), "Stationary king setup failed");
            check(game.isAmbiguousCastle(62, 62) != 0 && game.requestMoveCastle(62, 62), "Stationary king castle failed");
            check(game.initFEN("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1", true), "King-to-rook setup failed");
            check(game.requestMove(60, 63), "King-to-rook castle request failed");
            game.reset();
            check(game.isLegalPosition() == 0 && game.getMoveArraySize() == 0, "Empty setup accepted");
            game.putPiece(60, BoardConstants.KING, BoardConstants.WHITE);
            game.putPiece(4, BoardConstants.KING, BoardConstants.BLACK);
            game.commitBoard();
            check(game.isLegalPosition() == 1, "Valid edited board rejected");
            game.newGame(BoardConstants.VARIANT_DUCK);
            check(game.getMyDuckPos() == game.getDuckPos(), "Starting Duck position appears unfinished");
            String start = game.getFEN();
            check(game.requestMove(52, 36), "Duck piece preview failed");
            check(game.hasPendingDuckMove() && game.getMyDuckPos() == -1 && game.pieceAt(BoardConstants.WHITE, 36) == BoardConstants.PAWN,
                "Duck preview not displayed");
            check(!game.requestDuckMove(36), "Duck placed on occupied square");
            check(game.stepBack() && start.equals(game.getFEN()), "Duck preview undo failed");
            check(game.requestMove(52, 36) && game.requestDuckMove(27), "Duck completion failed");
            fen = game.getFEN();
            check(game.stepBack() && game.stepForward() && fen.equals(game.getFEN()), "Duck replay failed");
        });
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
    /** Round-trips a snapshot through every RustBoardNative entry point, without touching GameApi. */
    private void nativeBoardContract() {
        RustBoardResult query = RustBoardNative.query(RustSearchRequest.Variant.STANDARD, START, new String[0]);
        check(query.turnWhite && query.gameState == 0 && !query.inCheck && query.halfmoveClock == 0
            && query.repetitionCount == 0 && query.castleWhiteKingside && query.castleWhiteQueenside
            && query.castleBlackKingside && query.castleBlackQueenside && query.fen.equals(START),
            "Board query did not reproduce the starting position");
        for (int count : query.captured) check(count == 0, "Opening position reported a capture");

        RustBoardResult legal = RustBoardNative.legalMoves(RustSearchRequest.Variant.STANDARD, START, new String[0]);
        String[] moves = RustBoardResult.words(legal.moves);
        check(moves.length == 20, "Expected 20 legal moves from the starting position, got " + moves.length);
        boolean hasE4 = false;
        for (String move : moves) if (move.equals("e2e4")) hasE4 = true;
        check(hasE4, "Legal moves omitted e2e4");

        RustBoardResult san = RustBoardNative.san(RustSearchRequest.Variant.STANDARD, START, new String[0], "e2e4");
        check(san.san.equals("e4"), "Wrong SAN for e2e4: " + san.san);

        RustBoardResult applied = RustBoardNative.applyMove(RustSearchRequest.Variant.STANDARD, START, new String[0], "e2e4");
        check(!applied.turnWhite && applied.gameState == 0
            && applied.fen.equals("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"),
            "Move application did not advance the position: " + applied.fen);

        RustBoardResult king = RustBoardNative.pieceAt(RustSearchRequest.Variant.STANDARD, START, new String[0], "e1");
        check(king.piece == 'K', "Expected a White king on e1");
        RustBoardResult empty = RustBoardNative.pieceAt(RustSearchRequest.Variant.STANDARD, START, new String[0], "e4");
        check(empty.piece == 0, "Expected e4 to be empty on the starting position");

        String attackersFen = "4k3/8/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0 1";
        RustBoardResult attackers = RustBoardNative.attackers(RustSearchRequest.Variant.STANDARD, attackersFen, new String[0], true, "d3");
        String[] attackerSquares = RustBoardResult.words(attackers.attackers);
        check(attackerSquares.length == 2, "Expected two attackers of d3, got " + attackerSquares.length);

        boolean failed = false;
        try { RustBoardNative.query(RustSearchRequest.Variant.STANDARD, "not a fen", new String[0]); }
        catch (IllegalArgumentException expected) { failed = true; }
        check(failed, "Invalid FEN was accepted");
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
    /**
     * {@code GameApi.requestMove(String)}'s SAN-token disambiguation is Rust-board-backed: a
     * pinned piece sharing a destination with another of the same kind must not count as a
     * second candidate, and Chess 960 castling SAN must still resolve and apply.
     */
    private void gameApiSanDisambiguationUsesRustBoardApi() {
        runOnMainSync(() -> {
            GameApi pinned = new GameApi();
            check(pinned.initFEN("8/8/1N2k3/8/4n3/1n6/8/3KQ3 b - - 0 1", true), "Could not load the pin position");
            check(pinned.requestMove("Nc5"), "Nc5 should resolve to the sole legal, unpinned knight");
            check(pinned.getFEN().startsWith("8/8/1N2k3/2n5/4n3/8/8/3KQ3"), "Wrong knight moved for Nc5: " + pinned.getFEN());

            GameApi capture = new GameApi();
            check(capture.requestMove("e4") && capture.requestMove("d5") && capture.requestMove("exd5"),
                "Capturing SAN token should resolve via Rust's reported capture flag");

            GameApi promotion = new GameApi();
            check(promotion.initFEN("8/P6k/8/8/8/8/7K/8 w - - 0 1", true), "Could not load the promotion position");
            check(promotion.requestMove("a8=Q"), "Promotion SAN token should resolve and apply");
            check(promotion.getFEN().startsWith("Q7/7k"), "Wrong promotion result: " + promotion.getFEN());

            GameApi castle960 = new GameApi();
            check(castle960.initFEN("4k3/8/8/8/8/8/8/1R2K1R1 w KQ - 0 1", true), "Could not load a Chess 960-shaped FEN");
            check(castle960.isChess960Variant(), "Off-square castling rights were not reported as Chess 960");
            check(castle960.requestMove("O-O"), "Chess 960 O-O should resolve via the Rust board API");
            check(castle960.getCurrentPly() == 1, "Castling move was not recorded");
        });
    }
    private void ecoLookupPreservesLivePosition() throws Exception {
        GameApi[] game = new GameApi[1];
        EcoService[] eco = new EcoService[1];
        HMap book = HMap.read(getTargetContext().getAssets(), "hashmap.bin");
        runOnMainSync(() -> {
            game[0] = new GameApi();
            eco[0] = new EcoService(game[0]);
            check(eco[0].getAvailableMoves().isEmpty(), "Unloaded ECO book should be empty");
            eco[0].load(getTargetContext().getAssets());
        });
        long deadline = android.os.SystemClock.uptimeMillis() + 10000;
        boolean[] loaded = {false};
        while (!loaded[0] && android.os.SystemClock.uptimeMillis() < deadline) {
            Thread.sleep(100);
            runOnMainSync(() -> loaded[0] = !eco[0].getAvailableMoves().isEmpty());
        }
        check(loaded[0], "ECO book did not load");
        runOnMainSync(() -> {
            assertEcoMatchesNative(game[0], eco[0], book);
            for (String san : new String[]{"e4", "e5", "Nf3", "Nc6", "Bb5", "a6", "Ba4", "Nf6"}) {
                check(game[0].requestMove(san), "Could not play opening move " + san);
                assertEcoMatchesNative(game[0], eco[0], book);
            }
            check(game[0].requestMove("O-O"), "Could not castle in opening");
            assertEcoMatchesNative(game[0], eco[0], book);
            check(game[0].goTo(game[0].getRootNode()), "Could not navigate to opening root");
            assertEcoMatchesNative(game[0], eco[0], book);
            check(game[0].initFEN(START.replace("0 1", "100 1"), true), "Could not load drawn position");
            assertEcoMatchesNative(game[0], eco[0], book);
            game[0].newGame(BoardConstants.VARIANT_DUCK);
            assertEcoMatchesNative(game[0], eco[0], book);
            check(game[0].requestMove("e4@d5"), "Could not play Duck turn");
            assertEcoMatchesNative(game[0], eco[0], book);
            check(game[0].initFEN("4k3/8/8/8/8/8/8/R5KR w KQ - 0 1", true), "Could not load Chess 960");
            assertEcoMatchesNative(game[0], eco[0], book);
        });
    }

    private void assertEcoMatchesNative(GameApi game, EcoService eco, HMap book) {
        JNI nativeBoard = JNI.getInstance();
        GameApi.PositionSnapshot snapshot = game.getPositionSnapshot(game.getCurrentNode());
        check(nativeBoard.initFEN(snapshot.initialFen), "Reference initial FEN rejected");
        int[] history = snapshot.getHistory(), ducks = snapshot.getDuckHistory();
        for (int i = 0; i < history.length; i++) {
            check(nativeBoard.move(history[i]) != 0, "Reference history rejected");
            if (ducks[i] >= 0) check(nativeBoard.requestDuckMove(ducks[i]) != 0, "Reference duck rejected");
        }
        String fen = game.getFEN();
        check(nativeBoard.getHashKey() == game.getHashKey(), "ECO root/history hash mismatch");
        long hash = game.getHashKey();
        Object node = game.getCurrentNode();
        int ply = game.getCurrentPly();
        ArrayList<Integer> legal = new ArrayList<>();
        for (int i = 0; i < game.getMoveArraySize(); i++) legal.add(game.getMoveArrayAt(i));
        ArrayList<Integer> expectedMoves = new ArrayList<>();
        JSONArray expectedEntries = new JSONArray();
        for (int move : legal) {
            if (nativeBoard.move(move) == 0) continue;
            try {
                String name = book.get(nativeBoard.getHashKey());
                if (name != null) {
                    expectedMoves.add(move);
                    JSONObject entry = new JSONObject();
                    entry.put("name", name);
                    entry.put("move", nativeBoard.getMyMoveToString());
                    expectedEntries.put(entry);
                }
            } catch (Exception error) {
                throw new AssertionError(error);
            } finally {
                nativeBoard.undo();
            }
        }
        check(expectedMoves.equals(eco.getAvailableMoves()), "ECO move/hash mismatch at " + fen);
        check(expectedEntries.toString().equals(eco.getAvailable().toString()), "ECO name/SAN mismatch at " + fen);
        check(expectedMoves.equals(eco.getAvailableMoves()), "Repeated ECO probe changed results");
        check(fen.equals(game.getFEN()) && hash == game.getHashKey(), "ECO probe mutated live position");
        check(node == game.getCurrentNode() && ply == game.getCurrentPly(), "ECO probe changed navigation");
        check(legal.size() == game.getMoveArraySize(), "ECO probe changed legal moves");
        for (int i = 0; i < legal.size(); i++) {
            check(legal.get(i) == game.getMoveArrayAt(i), "ECO probe changed move ordering");
        }
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
