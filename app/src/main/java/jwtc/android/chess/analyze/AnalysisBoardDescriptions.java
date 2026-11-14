package jwtc.android.chess.analyze;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jwtc.android.chess.engine.RustBoard;
import jwtc.android.chess.engine.RustSearchRequest;
import jwtc.chess.board.BoardConstants;

/** Prepares optional square descriptions from a detached Position. */
final class AnalysisBoardDescriptions {
    private static final int[] EMPTY = new int[0];
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(task -> new Thread(task, "AnalysisBoardDescriptions"));
    private final Runnable onReady;
    private volatile Position requested;
    private int[][][] attackers;
    private boolean working;
    private boolean closed;

    private static final class Position {
        final RustSearchRequest.Variant variant;
        final String fen;

        Position(RustSearchRequest.Variant variant, String fen) {
            this.variant = variant;
            this.fen = fen;
        }
    }

    AnalysisBoardDescriptions(Runnable onReady) {
        this.onReady = onReady;
    }

    void show(RustSearchRequest.Variant variant, String fen) {
        if (requested != null && requested.variant == variant && requested.fen.equals(fen)) {
            return;
        }
        attackers = null;
        requested = new Position(variant, fen);
        start();
    }

    int[] attackersFor(int square, int color) {
        return attackers == null ? EMPTY : attackers[color][square];
    }

    void clear() {
        requested = null;
        attackers = null;
    }

    void close() {
        closed = true;
        clear();
        worker.shutdownNow();
    }

    private void start() {
        if (closed || working || requested == null) {
            return;
        }
        Position position = requested;
        working = true;
        worker.execute(
                () -> {
                    int[][][] result = calculate(position);
                    main.post(() -> deliver(position, result));
                });
    }

    private void deliver(Position position, int[][][] result) {
        working = false;
        if (requested != position) {
            start();
        } else if (result != null) {
            attackers = result;
            onReady.run();
        }
    }

    private int[][][] calculate(Position position) {
        int[][][] result = new int[2][64][];
        try {
            for (int square = 0; square < 64; square++) {
                if (requested != position) {
                    return null;
                }
                for (int color = BoardConstants.BLACK; color <= BoardConstants.WHITE; color++) {
                    result[color][square] =
                            RustBoard.attackersForPosition(
                                    position.variant, position.fen, square, color);
                }
            }
        } catch (IllegalArgumentException invalid) {
            Log.d("AnalysisBoard", "Cannot describe attacks for this Position", invalid);
            return null;
        }
        return result;
    }
}
