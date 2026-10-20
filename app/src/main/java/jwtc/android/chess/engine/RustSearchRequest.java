package jwtc.android.chess.engine;

import jwtc.android.chess.services.GameApi;
import jwtc.chess.Move;
import jwtc.chess.Pos;

/** Immutable coordinate-based input. Packed moves never cross the Rust boundary. */
public final class RustSearchRequest {
    public enum Variant { STANDARD, CHESS_960, DUCK, KING_OF_THE_HILL }
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

    public static RustSearchRequest fromSnapshot(long id, GameApi.PositionSnapshot position,
                                          int depth, int millis, boolean quiescence, Variant variant) {
        int[] packed = position.getHistory();
        int[] ducks = position.getDuckHistory();
        String[] coordinates = new String[packed.length];
        boolean whiteToMoveFirst = position.initialFen.split("\\s+")[1].equals("w");
        int[] whiteFiles = variant == Variant.CHESS_960 ? chess960Files(position.initialFen, true) : null;
        int[] blackFiles = variant == Variant.CHESS_960 ? chess960Files(position.initialFen, false) : null;
        for (int i = 0; i < packed.length; i++) {
            int move = packed[i];
            boolean whiteMoved = i % 2 == 0 == whiteToMoveFirst;
            coordinates[i] = toRustCoordinate(move, whiteMoved ? whiteFiles : blackFiles, ducks[i]);
        }
        return new RustSearchRequest(id, variant, position.initialFen,
            coordinates, depth, millis, quiescence);
    }

    /**
     * The Rust-boundary coordinate for one packed move: king-to-original-rook for Chess 960
     * castling (see {@link #chess960Files}), unchanged otherwise (including Standard castling,
     * which stays king-to-destination).
     */
    public static String toRustCoordinate(int move, int[] files, int duck) {
        return toRustCoordinate(move, files) + (duck < 0 ? "" : "@" + Pos.toString(duck));
    }

    public static String toRustCoordinate(int move, int[] files) {
        int from = Move.getFrom(move);
        String base = Pos.toString(from) + Pos.toString(Move.getTo(move))
            + (Move.isPromotionMove(move) ? "pnbrqk".charAt(Move.getPromotionPiece(move)) : "");
        if (files == null || !(Move.isOO(move) || Move.isOOO(move))) return base;
        int rookFile = Move.isOO(move) ? files[2] : files[1];
        int rookSquare = Pos.fromColAndRow(rookFile, Pos.row(from));
        return Pos.toString(from) + Pos.toString(rookSquare);
    }

    /**
     * The reverse of {@link #toRustCoordinate}: rewrites a Chess 960 king-to-rook result back to
     * the king-to-destination form {@link GameApi.PositionMove#uci} uses, so it can be resolved
     * via {@link GameApi.PositionSnapshot#findMove}. Leaves every other move unchanged.
     * <p>
     * {@code backRank} (the row {@code files}' king/rook squares were found on, top-down: 0 for
     * Black, 7 for White) is required: on a fixed back rank, a file uniquely identifies a square,
     * so pairing it with {@code files[0]} (the king's file) establishes that {@code from} is
     * actually the king's own square, not merely any piece sharing its file on some other rank —
     * which a king can reach only by stepping onto the tracked rook's own square, impossible for
     * an ordinary (non-castling) move.
     */
    static String fromRustCoordinate(String uci, int[] files, int backRank) {
        if (files == null || uci.length() < 4) return uci;
        int from, to;
        try {
            from = Pos.fromString(uci.substring(0, 2));
            to = Pos.fromString(uci.substring(2, 4));
        } catch (Exception e) {
            return uci;
        }
        if (Pos.row(from) != backRank || Pos.col(from) != files[0] || Pos.row(to) != backRank) return uci;
        int toFile = Pos.col(to);
        int destinationFile = toFile == files[2] ? 6 : toFile == files[1] ? 2 : -1;
        if (destinationFile < 0) return uci;
        return Pos.toString(from) + Pos.toString(Pos.fromColAndRow(destinationFile, backRank));
    }

    /**
     * The king file and the queen-side/king-side rook files (each -1 if absent) for one colour,
     * read from a FEN's piece-placement field. The outermost rook on each side of the king is the
     * one Chess 960 castling tracks, matching the native reference engine's setCastlingsEPAnd50.
     */
    public static int[] chess960Files(String fen, boolean white) {
        String[] ranks = fen.split("\\s+")[0].split("/");
        String rank = expandRank(white ? ranks[7] : ranks[0]);
        char king = white ? 'K' : 'k';
        char rook = white ? 'R' : 'r';
        int kingFile = rank.indexOf(king);
        int queenside = -1;
        for (int file = kingFile - 1; file >= 0; file--) {
            if (rank.charAt(file) == rook) queenside = file;
        }
        int kingside = -1;
        for (int file = kingFile + 1; file < 8; file++) {
            if (rank.charAt(file) == rook) kingside = file;
        }
        return new int[] { kingFile, queenside, kingside };
    }

    private static String expandRank(String rank) {
        StringBuilder expanded = new StringBuilder();
        for (int i = 0; i < rank.length(); i++) {
            char c = rank.charAt(i);
            if (Character.isDigit(c)) {
                for (int n = 0; n < c - '0'; n++) expanded.append('.');
            } else {
                expanded.append(c);
            }
        }
        return expanded.toString();
    }
}
