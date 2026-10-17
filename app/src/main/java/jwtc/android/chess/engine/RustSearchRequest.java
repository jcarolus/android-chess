package jwtc.android.chess.engine;

import jwtc.android.chess.services.GameApi;
import jwtc.chess.Move;
import jwtc.chess.Pos;

/** Immutable coordinate-based input. Packed moves never cross the Rust boundary. */
public final class RustSearchRequest {
    public enum Variant { STANDARD, CHESS_960, DUCK }
    public final long searchId;
    public final Variant variant;
    public final String startingPosition;
    private final String[] history;
    public final int depth;
    public final int timeMillis;
    public final boolean quiescence;

    public RustSearchRequest(long searchId, Variant variant, String startingPosition,
                             String[] history, int depth, int timeMillis, boolean quiescence) {
        if (variant == null || startingPosition == null || history == null) {
            throw new IllegalArgumentException("Missing Rust search input");
        }
        if (!(depth >= 1 && depth <= 15 && timeMillis == 0 || depth == 0 && timeMillis > 0)) {
            throw new IllegalArgumentException("Expected depth 1..15 or a positive time budget");
        }
        this.searchId = searchId;
        this.variant = variant;
        this.startingPosition = startingPosition;
        this.history = history.clone();
        this.depth = depth;
        this.timeMillis = timeMillis;
        this.quiescence = quiescence;
    }

    public String[] getHistory() { return history.clone(); }

    static RustSearchRequest fromSnapshot(long id, GameApi.PositionSnapshot position,
                                          int depth, int millis, boolean quiescence) {
        int[] packed = position.getHistory();
        String[] coordinates = new String[packed.length];
        for (int i = 0; i < packed.length; i++) {
            int move = packed[i];
            coordinates[i] = Pos.toString(Move.getFrom(move)) + Pos.toString(Move.getTo(move))
                + (Move.isPromotionMove(move) ? "pnbrqk".charAt(Move.getPromotionPiece(move)) : "");
        }
        return new RustSearchRequest(id, Variant.STANDARD, position.initialFen,
            coordinates, depth, millis, quiescence);
    }
}
