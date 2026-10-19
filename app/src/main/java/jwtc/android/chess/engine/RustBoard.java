package jwtc.android.chess.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import jwtc.chess.Move;
import jwtc.chess.Pos;
import jwtc.chess.board.BoardConstants;
import jwtc.chess.board.BoardPosition;
import jwtc.chess.board.EcoHash;

/** Java-owned board snapshots. Rust validates moves; no live position is stored in native code. */
public final class RustBoard {
    public static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    // Board-sharing screens observe the same Java state; game trees remain owned by GameApi.
    private static final RustBoard shared = new RustBoard();
    public static RustBoard shared() { return shared; }
    private final List<Frame> frames = new ArrayList<>();
    private RustSearchRequest.Variant variant;
    private String initialFen;
    private int promotion = BoardConstants.QUEEN;
    private List<LegalMove> legal;

    private static final class Frame {
        final String fen, coordinate;
        final int move, duck;
        final long hash;
        final RustBoardResult result;
        final boolean pending;
        Frame(String fen, String coordinate, int move, int duck, long hash, RustBoardResult result, boolean pending) {
            this.fen = fen; this.coordinate = coordinate; this.move = move; this.duck = duck;
            this.hash = hash; this.result = result; this.pending = pending;
        }
    }

    public static final class LegalMove {
        public final int packed;
        public final String coordinate, san;
        LegalMove(int packed, String coordinate, String san) {
            this.packed = packed; this.coordinate = coordinate; this.san = san;
        }
    }

    public RustBoard() { initFEN(START); }
    private Frame current() { return frames.get(frames.size() - 1); }
    public synchronized RustSearchRequest.Variant variant() { return variant; }
    public synchronized String initialFen() { return initialFen; }
    public synchronized String[] history() {
        List<String> history = new ArrayList<>();
        for (int i = 1; i < frames.size(); i++) if (!frames.get(i).pending) history.add(frames.get(i).coordinate);
        return history.toArray(new String[0]);
    }
    public synchronized RustBoardResult query() { return current().result; }
    public synchronized String toFEN() { return current().fen; }
    public synchronized int getNumBoard() { return frames.size() - 1; }
    public synchronized int getTurn() { return new BoardPosition(toFEN()).white ? BoardConstants.WHITE : BoardConstants.BLACK; }
    public synchronized int getVariant() { return variant == RustSearchRequest.Variant.DUCK ? BoardConstants.VARIANT_DUCK : BoardConstants.VARIANT_DEFAULT; }
    public synchronized int getMyMove() { return current().move; }
    public synchronized int getMyDuckPos() { return current().duck; }
    public synchronized int getDuckPos() { return new BoardPosition(toFEN()).duck(); }
    public synchronized long getHashKey() { return current().hash; }
    public synchronized int getState() {
        RustBoardResult result = query();
        if (result == null) return BoardConstants.INVALID;
        return RustBoardResult.boardState(result.gameState);
    }
    public synchronized int isEnded() { return getState() == BoardConstants.PLAY || getState() == BoardConstants.CHECK ? 0 : 1; }
    public synchronized int isLegalPosition() {
        if (query() == null) return 0;
        String placement = toFEN().split(" ")[0];
        return placement.indexOf('K') >= 0 && placement.indexOf('k') >= 0 ? 1 : 0;
    }
    public synchronized int pieceAt(int colour, int square) {
        char c = new BoardPosition(toFEN()).squares[square];
        return BoardPosition.pieceType(c, colour);
    }
    public synchronized int getNumCaptured(int colour, int piece) {
        return query() == null || piece < 0 || piece >= 5 ? 0 : query().captured[colour * 5 + piece];
    }
    private int right(char c) { return new BoardPosition(toFEN()).castling.indexOf(c) < 0 ? 0 : 1; }
    public synchronized int getWhiteCanCastleLong() { return right('Q'); }
    public synchronized int getWhiteCanCastleShort() { return right('K'); }
    public synchronized int getBlackCanCastleLong() { return right('q'); }
    public synchronized int getBlackCanCastleShort() { return right('k'); }
    public synchronized int getEnpassantPosition() { return new BoardPosition(toFEN()).ep; }

    /** Structurally valid but illegal setup positions can be displayed and edited. */
    public synchronized boolean initFEN(String fen) {
        final BoardPosition position;
        try { position = new BoardPosition(fen); }
        catch (IllegalArgumentException ex) { return false; }
        RustSearchRequest.Variant selected = position.duck() >= 0 ? RustSearchRequest.Variant.DUCK
            : position.chess960() ? RustSearchRequest.Variant.CHESS_960 : RustSearchRequest.Variant.STANDARD;
        load(position.fen(), selected);
        return true;
    }
    public synchronized boolean initFEN(String fen, RustSearchRequest.Variant selected) {
        try { load(new BoardPosition(fen).fen(), selected); return true; }
        catch (IllegalArgumentException ex) { return false; }
    }
    private void load(String fen, RustSearchRequest.Variant selected) {
        RustBoardResult result;
        try { result = RustBoardNative.query(selected, fen, new String[0]); }
        catch (IllegalArgumentException ex) { result = null; }
        variant = selected;
        if (result != null) fen = result.fen;
        initialFen = fen;
        frames.clear();
        frames.add(new Frame(fen, "", 0, new BoardPosition(fen).duck(), EcoHash.initial(fen), result, false));
        legal = null;
    }
    public synchronized void newGame() { newGame(BoardConstants.VARIANT_DEFAULT); }
    public synchronized void newGame(int variant) {
        initFEN(variant == BoardConstants.VARIANT_DUCK ? START.replace("8/8/8/8", "8/7$/8/8") : START);
    }
    public synchronized int initRandomFisher(int seed) {
        if (seed < 0) seed = ThreadLocalRandom.current().nextInt(960);
        load(BoardPosition.chess960Fen(seed), RustSearchRequest.Variant.CHESS_960);
        return seed;
    }
    public synchronized List<LegalMove> legalMoves() {
        if (legal != null) return legal;
        LinkedHashMap<String, LegalMove> moves = new LinkedHashMap<>();
        if (query() != null && !current().pending) {
            BoardPosition position = new BoardPosition(toFEN());
            String[] history = history();
            for (String turn : RustBoardResult.words(RustBoardNative.legalMoves(variant, initialFen, history).moves)) {
                String coordinate = turn.split("@")[0];
                if (moves.containsKey(coordinate)) continue;
                String san = RustBoardNative.san(variant, initialFen, history, coordinate).san;
                moves.put(coordinate, new LegalMove(position.packedMove(coordinate, san), coordinate, san));
            }
        }
        legal = java.util.Collections.unmodifiableList(new ArrayList<>(moves.values()));
        return legal;
    }
    public synchronized int getMoveArraySize() { return legalMoves().size(); }
    public synchronized int getMoveArrayAt(int i) { return legalMoves().get(i).packed; }
    public synchronized void setPromo(int piece) { promotion = piece; }
    public synchronized LegalMove resolveMove(int from, int to, int promotionPiece) {
        for (LegalMove move : legalMoves()) {
            if (Move.getFrom(move.packed) == from && Move.getTo(move.packed) == to
                && (!Move.isPromotionMove(move.packed) || Move.getPromotionPiece(move.packed) == promotionPiece)) return move;
        }
        // A king dragged onto its rook (or farther along its rank) requests castling.
        if (from != to && Pos.row(from) == Pos.row(to) && pieceAt(getTurn(), from) == BoardConstants.KING) {
            for (LegalMove move : legalMoves()) if (Move.getFrom(move.packed) == from
                && (to > from && Move.isOO(move.packed) || to < from && Move.isOOO(move.packed))) return move;
        }
        return null;
    }
    public synchronized int requestMove(int from, int to) {
        LegalMove resolved = resolveMove(from, to, promotion);
        return resolved == null ? 0 : move(resolved.packed);
    }
    public synchronized int move(int packed) {
        for (LegalMove move : legalMoves()) {
            if (move.packed != packed) continue;
            RustBoardResult result = RustBoardNative.applyMove(variant, initialFen, history(), move.coordinate);
            boolean pending = variant == RustSearchRequest.Variant.DUCK && result.gameState == 0;
            frames.add(new Frame(result.fen, move.coordinate, packed, -1,
                EcoHash.afterMove(getHashKey(), toFEN(), result.fen, packed), result, pending));
            legal = null;
            return 1;
        }
        return 0;
    }
    public synchronized int requestDuckMove(int square) {
        if (square < 0 || square >= 64 || variant != RustSearchRequest.Variant.DUCK) return 0;
        if (!current().pending) return 0;
        Frame pending = current();
        String coordinate = pending.coordinate + "@" + Pos.toString(square);
        final RustBoardResult result;
        try { result = RustBoardNative.applyMove(variant, initialFen, history(), coordinate); }
        catch (IllegalArgumentException ex) { return 0; }
        frames.set(frames.size() - 1, new Frame(result.fen, coordinate, pending.move, square, pending.hash, result, false));
        legal = null;
        return 1;
    }
    public synchronized void undo() {
        if (frames.size() > 1) frames.remove(frames.size() - 1);
        legal = null;
    }
    public synchronized int isAmbiguousCastle(int from, int to) {
        boolean ordinary = false, castle = false;
        for (LegalMove m : legalMoves()) if (Move.getFrom(m.packed) == from && Move.getTo(m.packed) == to) {
            if (Move.isOO(m.packed) || Move.isOOO(m.packed)) castle = true;
            else ordinary = true;
        }
        return castle && (ordinary || from == to) ? 1 : 0;
    }
    public synchronized int doCastleMove(int from, int to) {
        for (LegalMove m : legalMoves()) if (Move.getFrom(m.packed) == from && Move.getTo(m.packed) == to
            && (Move.isOO(m.packed) || Move.isOOO(m.packed))) return move(m.packed);
        return 0;
    }
    public synchronized int[] getAttackerPositionsTo(int square, int colour) {
        if (query() == null) return new int[0];
        String[] positions = RustBoardResult.words(RustBoardNative.attackers(variant, toFEN(), new String[0],
            colour == BoardConstants.WHITE, Pos.toString(square)).attackers);
        int[] result = new int[positions.length];
        for (int i = 0; i < positions.length; i++) result[i] = BoardPosition.square(positions[i]);
        return result;
    }
    public synchronized void reset() { load("8/8/8/8/8/8/8/8 w - - 0 1", RustSearchRequest.Variant.STANDARD); }
    private void edit(BoardPosition position) { load(position.fen(), variant); }
    public synchronized void putPiece(int square, int piece, int colour) {
        BoardPosition position = new BoardPosition(toFEN());
        char c = "pnbrqk".charAt(piece);
        position.squares[square] = colour == BoardConstants.WHITE ? Character.toUpperCase(c) : c;
        edit(position);
    }
    public synchronized void removePiece(int colour, int square) {
        if (pieceAt(colour, square) == BoardConstants.FIELD) return;
        BoardPosition position = new BoardPosition(toFEN()); position.squares[square] = 0; edit(position);
    }
    public synchronized void setTurn(int turn) {
        BoardPosition position = new BoardPosition(toFEN()); position.white = turn == BoardConstants.WHITE; edit(position);
    }
    public synchronized void setCastlingsEPAnd50(int q, int k, int bq, int bk, int ep, int halfmove) {
        BoardPosition position = new BoardPosition(toFEN());
        position.castling = (k != 0 ? "K" : "") + (q != 0 ? "Q" : "") + (bk != 0 ? "k" : "") + (bq != 0 ? "q" : "");
        position.ep = ep; position.halfmove = halfmove;
        if (variant != RustSearchRequest.Variant.DUCK) variant = position.chess960() ? RustSearchRequest.Variant.CHESS_960 : RustSearchRequest.Variant.STANDARD;
        edit(position);
    }
    public synchronized void commitBoard() { edit(new BoardPosition(toFEN())); }
    public synchronized void setVariant(int value) {
        BoardPosition position = new BoardPosition(toFEN());
        if (value != BoardConstants.VARIANT_DUCK && position.duck() >= 0) position.squares[position.duck()] = 0;
        load(position.fen(), value == BoardConstants.VARIANT_DUCK ? RustSearchRequest.Variant.DUCK
            : position.chess960() ? RustSearchRequest.Variant.CHESS_960 : RustSearchRequest.Variant.STANDARD);
    }
    public synchronized int placeDuck(int square) {
        BoardPosition position = new BoardPosition(toFEN());
        if (square < 0 || square >= 64 || position.squares[square] != 0 || variant != RustSearchRequest.Variant.DUCK) return 0;
        if (position.duck() >= 0) position.squares[position.duck()] = 0;
        position.squares[square] = '$'; edit(position); return 1;
    }
}
