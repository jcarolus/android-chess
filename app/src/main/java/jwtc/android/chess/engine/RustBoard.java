package jwtc.android.chess.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import jwtc.chess.Move;
import jwtc.chess.Pos;
import jwtc.chess.board.BoardConstants;
import jwtc.chess.board.BoardPosition;

/** Java-owned board snapshots. Rust validates moves; no live position is stored in native code. */
public final class RustBoard {
    public static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    // Board-sharing screens observe the same Java state; game trees remain owned by GameApi.
    private static final RustBoard shared = new RustBoard();

    public static RustBoard shared() {
        return shared;
    }

    private final List<Frame> frames = new ArrayList<>();
    private RustSearchRequest.Variant variant;
    private String initialFen;
    private int promotion = BoardConstants.QUEEN;
    private List<LegalMove> legal;

    private static final class Frame {
        final String fen, coordinate, san;
        // Private read-only snapshot; setup edits always parse a separate position.
        final BoardPosition position;
        final int duckPosition;
        final int move, duck;
        final long hash;
        final RustBoardResult result;
        final boolean pending;

        Frame(
                String fen,
                String coordinate,
                String san,
                int move,
                int duck,
                long hash,
                RustBoardResult result,
                boolean pending) {
            this.fen = fen;
            this.coordinate = coordinate;
            this.san = san;
            this.move = move;
            this.duck = duck;
            this.hash = hash;
            this.result = result;
            this.pending = pending;
            this.position = new BoardPosition(fen);
            this.duckPosition = position.duck();
        }
    }

    public static final class LegalMove {
        public final int packed;
        public final String coordinate, san;

        LegalMove(int packed, String coordinate, String san) {
            this.packed = packed;
            this.coordinate = coordinate;
            this.san = san;
        }
    }

    public RustBoard() {
        initFEN(START);
    }

    private Frame current() {
        return frames.get(frames.size() - 1);
    }

    public synchronized RustSearchRequest.Variant variant() {
        return variant;
    }

    public synchronized String initialFen() {
        return initialFen;
    }

    public synchronized String[] history() {
        List<String> history = new ArrayList<>();
        for (int i = 1; i < frames.size(); i++) {
            if (!frames.get(i).pending) {
                history.add(frames.get(i).coordinate);
            }
        }
        return history.toArray(new String[0]);
    }

    /**
     * Index of the frame Rust calls replay from. Positions before the latest pawn move or capture
     * (halfmove clock 0) can never repeat, so the replay starts there rather than at the initial
     * position; replay cost then depends on the reversible tail, not the length of the game.
     */
    private int replayBase() {
        for (int i = frames.size() - 1; i > 0; i--) {
            Frame frame = frames.get(i);
            if (!frame.pending && frame.result != null && frame.result.halfmoveClock == 0) {
                return i;
            }
        }
        return 0;
    }

    private String replayFen() {
        return frames.get(replayBase()).fen;
    }

    private String[] replayHistory() {
        List<String> history = new ArrayList<>();
        for (int i = replayBase() + 1; i < frames.size(); i++) {
            if (!frames.get(i).pending) {
                history.add(frames.get(i).coordinate);
            }
        }
        return history.toArray(new String[0]);
    }

    public synchronized RustBoardResult query() {
        return current().result;
    }

    public synchronized String toFEN() {
        return current().fen;
    }

    public synchronized int getNumBoard() {
        return frames.size() - 1;
    }

    public synchronized int getTurn() {
        return current().position.white ? BoardConstants.WHITE : BoardConstants.BLACK;
    }

    public synchronized int getVariant() {
        return variant == RustSearchRequest.Variant.KING_OF_THE_HILL
                ? BoardConstants.VARIANT_KING_OF_THE_HILL
                : variant == RustSearchRequest.Variant.DUCK
                        ? BoardConstants.VARIANT_DUCK
                        : BoardConstants.VARIANT_DEFAULT;
    }

    public synchronized int getMyMove() {
        return current().move;
    }

    /** SAN of the move that produced the current position; empty at a starting position. */
    public synchronized String getMySan() {
        return current().san;
    }

    public synchronized int getMyDuckPos() {
        return current().duck;
    }

    public synchronized int getDuckPos() {
        return current().duckPosition;
    }

    public synchronized long getHashKey() {
        return current().hash;
    }

    public synchronized int getState() {
        RustBoardResult result = query();
        if (result == null) {
            return BoardConstants.INVALID;
        }
        return RustBoardResult.boardState(result.gameState);
    }

    public synchronized int isEnded() {
        return getState() == BoardConstants.PLAY || getState() == BoardConstants.CHECK ? 0 : 1;
    }

    public synchronized int isLegalPosition() {
        if (query() == null) {
            return 0;
        }
        String placement = toFEN().split(" ")[0];
        return placement.indexOf('K') >= 0 && placement.indexOf('k') >= 0 ? 1 : 0;
    }

    public synchronized int pieceAt(int colour, int square) {
        char c = current().position.squares[square];
        return BoardPosition.pieceType(c, colour);
    }

    public synchronized int getNumCaptured(int colour, int piece) {
        return query() == null || piece < 0 || piece >= 5
                ? 0
                : query().captured[colour * 5 + piece];
    }

    private int right(char c) {
        return current().position.castling.indexOf(c) < 0 ? 0 : 1;
    }

    public synchronized int getWhiteCanCastleLong() {
        return right('Q');
    }

    public synchronized int getWhiteCanCastleShort() {
        return right('K');
    }

    public synchronized int getBlackCanCastleLong() {
        return right('q');
    }

    public synchronized int getBlackCanCastleShort() {
        return right('k');
    }

    public synchronized int getEnpassantPosition() {
        return current().position.ep;
    }

    /** Structurally valid but illegal setup positions can be displayed and edited. */
    public synchronized boolean initFEN(String fen) {
        final BoardPosition position;
        try {
            position = new BoardPosition(fen);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        RustSearchRequest.Variant selected =
                position.duck() >= 0
                        ? RustSearchRequest.Variant.DUCK
                        : position.chess960()
                                ? RustSearchRequest.Variant.CHESS_960
                                : RustSearchRequest.Variant.STANDARD;
        load(position.fen(), selected);
        return true;
    }

    public synchronized boolean initFEN(String fen, RustSearchRequest.Variant selected) {
        try {
            load(new BoardPosition(fen).fen(), selected);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private void load(String fen, RustSearchRequest.Variant selected) {
        RustBoardResult result;
        try {
            result = RustBoardNative.query(selected, fen, new String[0]);
        } catch (IllegalArgumentException ex) {
            result = null;
        }
        variant = selected;
        if (result != null) {
            fen = result.fen;
        }
        initialFen = fen;
        frames.clear();
        frames.add(
                new Frame(
                        fen,
                        "",
                        "",
                        0,
                        new BoardPosition(fen).duck(),
                        result == null ? 0 : result.hashKey,
                        result,
                        false));
        legal = null;
    }

    public synchronized void newGame() {
        newGame(BoardConstants.VARIANT_DEFAULT);
    }

    public synchronized void newGame(int variant) {
        if (variant == BoardConstants.VARIANT_KING_OF_THE_HILL) {
            load(START, RustSearchRequest.Variant.KING_OF_THE_HILL);
        } else {
            initFEN(
                    variant == BoardConstants.VARIANT_DUCK
                            ? START.replace("8/8/8/8", "8/7$/8/8")
                            : START);
        }
    }

    public synchronized int initRandomFisher(int seed) {
        if (seed < 0) {
            seed = ThreadLocalRandom.current().nextInt(960);
        }
        load(BoardPosition.chess960Fen(seed), RustSearchRequest.Variant.CHESS_960);
        return seed;
    }

    public synchronized List<LegalMove> legalMoves() {
        if (legal != null) {
            return legal;
        }
        if (query() == null || current().pending) {
            legal = java.util.Collections.emptyList();
        } else {
            legal =
                    parseTurns(
                            RustBoardNative.legalTurns(variant, replayFen(), replayHistory()).turns,
                            toFEN());
        }
        return legal;
    }

    /** Parses {@code coordinate=san} pairs for the position {@code fen} into packed legal moves. */
    private static List<LegalMove> parseTurns(String turns, String fen) {
        BoardPosition position = new BoardPosition(fen);
        List<LegalMove> moves = new ArrayList<>();
        for (String pair : RustBoardResult.words(turns)) {
            int separator = pair.indexOf('=');
            String coordinate = pair.substring(0, separator), san = pair.substring(separator + 1);
            moves.add(new LegalMove(position.packedMove(coordinate, san), coordinate, san));
        }
        return java.util.Collections.unmodifiableList(moves);
    }

    public synchronized int getMoveArraySize() {
        return legalMoves().size();
    }

    public synchronized int getMoveArrayAt(int i) {
        return legalMoves().get(i).packed;
    }

    public synchronized void setPromo(int piece) {
        promotion = piece;
    }

    public synchronized LegalMove resolveMove(int from, int to, int promotionPiece) {
        // In Chess 960 a plain king move can share its squares with a castle; the plain move
        // wins, castling is requested by moving the king onto its rook.
        LegalMove castle = null;
        for (LegalMove move : legalMoves()) {
            if (Move.getFrom(move.packed) == from
                    && Move.getTo(move.packed) == to
                    && (!Move.isPromotionMove(move.packed)
                            || Move.getPromotionPiece(move.packed) == promotionPiece)) {
                if (!Move.isOO(move.packed) && !Move.isOOO(move.packed)) {
                    return move;
                }
                castle = move;
            }
        }
        if (castle != null) {
            return castle;
        }
        // A king dragged onto its rook (or farther along its rank) requests castling.
        if (from != to
                && Pos.row(from) == Pos.row(to)
                && pieceAt(getTurn(), from) == BoardConstants.KING) {
            for (LegalMove move : legalMoves()) {
                if (Move.getFrom(move.packed) == from
                        && (to > from && Move.isOO(move.packed)
                                || to < from && Move.isOOO(move.packed))) {
                    return move;
                }
            }
        }
        return null;
    }

    public synchronized int requestMove(int from, int to) {
        LegalMove resolved = resolveMove(from, to, promotion);
        return resolved == null ? 0 : move(resolved.packed);
    }

    public synchronized int move(int packed) {
        for (LegalMove move : legalMoves()) {
            if (move.packed != packed) {
                continue;
            }
            RustBoardResult result =
                    RustBoardNative.applyMove(
                            variant, replayFen(), replayHistory(), move.coordinate);
            // The duck placement still decides whether the opponent is left without moves.
            boolean pending =
                    variant == RustSearchRequest.Variant.DUCK
                            && (result.boardState() == BoardConstants.PLAY
                                    || result.boardState() == BoardConstants.DUCK_STALEMATE_WIN);
            String fen = result.fen;
            frames.add(
                    new Frame(
                            fen,
                            move.coordinate,
                            move.san,
                            packed,
                            -1,
                            result.hashKey,
                            result,
                            pending));
            legal =
                    pending
                            ? java.util.Collections.emptyList()
                            : parseTurns(result.turns, result.fen);
            return 1;
        }
        return 0;
    }

    public synchronized int requestDuckMove(int square) {
        if (square < 0 || square >= 64 || variant != RustSearchRequest.Variant.DUCK) {
            return 0;
        }
        if (!current().pending) {
            return 0;
        }
        Frame pending = current();
        String coordinate = pending.coordinate + "@" + Pos.toString(square);
        final RustBoardResult result;
        try {
            result = RustBoardNative.applyMove(variant, replayFen(), replayHistory(), coordinate);
        } catch (IllegalArgumentException ex) {
            return 0;
        }
        frames.set(
                frames.size() - 1,
                new Frame(
                        result.fen,
                        coordinate,
                        pending.san,
                        pending.move,
                        square,
                        pending.hash,
                        result,
                        false));
        legal = parseTurns(result.turns, result.fen);
        return 1;
    }

    public synchronized void undo() {
        if (frames.size() > 1) {
            frames.remove(frames.size() - 1);
        }
        legal = null;
    }

    public synchronized int isAmbiguousCastle(int from, int to) {
        boolean ordinary = false, castle = false;
        for (LegalMove m : legalMoves()) {
            if (Move.getFrom(m.packed) == from && Move.getTo(m.packed) == to) {
                if (Move.isOO(m.packed) || Move.isOOO(m.packed)) {
                    castle = true;
                } else {
                    ordinary = true;
                }
            }
        }
        return castle && (ordinary || from == to) ? 1 : 0;
    }

    public synchronized int doCastleMove(int from, int to) {
        for (LegalMove m : legalMoves()) {
            if (Move.getFrom(m.packed) == from
                    && Move.getTo(m.packed) == to
                    && (Move.isOO(m.packed) || Move.isOOO(m.packed))) {
                return move(m.packed);
            }
        }
        return 0;
    }

    public synchronized int[] getAttackerPositionsTo(int square, int colour) {
        if (query() == null) {
            return new int[0];
        }
        String[] positions =
                RustBoardResult.words(
                        RustBoardNative.attackers(
                                        variant,
                                        toFEN(),
                                        new String[0],
                                        colour == BoardConstants.WHITE,
                                        Pos.toString(square))
                                .attackers);
        int[] result = new int[positions.length];
        for (int i = 0; i < positions.length; i++) {
            result[i] = BoardPosition.square(positions[i]);
        }
        return result;
    }

    public synchronized void reset() {
        load("8/8/8/8/8/8/8/8 w - - 0 1", RustSearchRequest.Variant.STANDARD);
    }

    private void edit(BoardPosition position) {
        load(position.fen(), variant);
    }

    public synchronized void putPiece(int square, int piece, int colour) {
        BoardPosition position = new BoardPosition(toFEN());
        char c = "pnbrqk".charAt(piece);
        position.squares[square] = colour == BoardConstants.WHITE ? Character.toUpperCase(c) : c;
        edit(position);
    }

    public synchronized void removePiece(int colour, int square) {
        if (pieceAt(colour, square) == BoardConstants.FIELD) {
            return;
        }
        BoardPosition position = new BoardPosition(toFEN());
        position.squares[square] = 0;
        edit(position);
    }

    public synchronized void setTurn(int turn) {
        BoardPosition position = new BoardPosition(toFEN());
        position.white = turn == BoardConstants.WHITE;
        edit(position);
    }

    public synchronized void setCastlingsEPAnd50(
            int q, int k, int bq, int bk, int ep, int halfmove) {
        BoardPosition position = new BoardPosition(toFEN());
        position.castling =
                (k != 0 ? "K" : "")
                        + (q != 0 ? "Q" : "")
                        + (bk != 0 ? "k" : "")
                        + (bq != 0 ? "q" : "");
        position.ep = ep;
        position.halfmove = halfmove;
        if (variant != RustSearchRequest.Variant.DUCK) {
            variant =
                    position.chess960()
                            ? RustSearchRequest.Variant.CHESS_960
                            : RustSearchRequest.Variant.STANDARD;
        }
        edit(position);
    }

    public synchronized void commitBoard() {
        edit(new BoardPosition(toFEN()));
    }

    public synchronized void setVariant(int value) {
        BoardPosition position = new BoardPosition(toFEN());
        if (value != BoardConstants.VARIANT_DUCK && position.duck() >= 0) {
            position.squares[position.duck()] = 0;
        }
        load(
                position.fen(),
                value == BoardConstants.VARIANT_KING_OF_THE_HILL
                        ? RustSearchRequest.Variant.KING_OF_THE_HILL
                        : value == BoardConstants.VARIANT_DUCK
                                ? RustSearchRequest.Variant.DUCK
                                : position.chess960()
                                        ? RustSearchRequest.Variant.CHESS_960
                                        : RustSearchRequest.Variant.STANDARD);
    }

    public synchronized int placeDuck(int square) {
        BoardPosition position = new BoardPosition(toFEN());
        if (square < 0
                || square >= 64
                || position.squares[square] != 0
                || variant != RustSearchRequest.Variant.DUCK) {
            return 0;
        }
        if (position.duck() >= 0) {
            position.squares[position.duck()] = 0;
        }
        position.squares[square] = '$';
        edit(position);
        return 1;
    }
}
