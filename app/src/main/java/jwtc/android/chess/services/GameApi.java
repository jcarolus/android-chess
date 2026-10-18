package jwtc.android.chess.services;

import android.content.res.Resources;
import android.util.Log;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Set;
import java.util.regex.Matcher;

import jwtc.android.chess.engine.RustBoardNative;
import jwtc.android.chess.engine.RustBoardResult;
import jwtc.android.chess.engine.RustSearchRequest;
import jwtc.chess.GameRecord;
import jwtc.chess.JNI;
import jwtc.chess.GameTree;
import jwtc.chess.GameTree.Node;
import jwtc.chess.Pgn;
import jwtc.chess.PgnDate;
import jwtc.chess.PgnDocument;
import jwtc.chess.PgnSyntaxError;
import jwtc.chess.Move;
import jwtc.chess.PGNEntry;
import jwtc.chess.Pos;
import jwtc.chess.board.BoardConstants;

/*
Wraps the JNI Java Native Interface
Implements PGN functionality
Deals with some parts of move notation
 */
public class GameApi {
    private static final String TAG = "GameApi";
    protected ArrayList<GameListener> listeners = new ArrayList<>();
    private JNI jni;
    public static final int MAX_PGN_SIZE = 500000;

    public final HashMap<String, String> pgnTags; //
    private GameTree gameTree;
    private Node currentNode;
    private PGNEntry pendingDuckMove;
    private int promotionPiece = BoardConstants.QUEEN;
    private String importedResult;
    private String lastPgnError;
    private boolean loadingPgn;
    private boolean treeChanged;
    private boolean overwriteContinuation;

    /** Local play replaces future moves; analysis and imports retain variations. */
    public void setOverwriteContinuation(boolean overwrite) { overwriteContinuation = overwrite; }

    public boolean hasContinuationToOverwrite() {
        return pendingDuckMove == null && currentNode.getNext() != null;
    }

    public boolean hasPendingDuckMove() { return pendingDuckMove != null; }
    public boolean isDuckVariant() { return jni.getVariant() == BoardConstants.VARIANT_DUCK; }

    public Node getRootNode() { return gameTree.getRoot(); }
    public Node getCurrentNode() { return currentNode; }
    public int getCurrentPly() { return currentNode.getPly() + (pendingDuckMove == null ? 0 : 1); }
    public boolean isOnMainLine() { return gameTree.isMainLine(currentNode); }
    public boolean isAtLineEnd() { return pendingDuckMove == null && currentNode.getNext() == null; }
    public List<Node> getMainLineNodes() { return gameTree.mainLine(); }
    /** The selected path followed by its preferred continuation, for linear history controls. */
    public List<Node> getCurrentLineNodes() {
        List<Node> line = gameTree.pathTo(currentNode);
        for (Node node = currentNode.getNext(); node != null; node = node.getNext()) line.add(node);
        return Collections.unmodifiableList(line);
    }
    public String getMoveNumber(Node node) { return gameTree.moveNumber(node); }
    public String getLastPgnError() { return lastPgnError; }

    public List<Node> getContinuations(Node position) {
        return gameTree.contains(position) ? position.getChildren() : Collections.emptyList();
    }

    /** Immutable input for asynchronous analysis, including notation for legal recommendations. */
    public static final class PositionSnapshot {
        public final String fen;
        public final String initialFen;
        public final String positionCommand;
        private final int[] history;
        private final int[] duckHistory;

        public int[] getHistory() { return history.clone(); }
        public int[] getDuckHistory() { return duckHistory.clone(); }
        public final int turn;
        public final int state;
        public final List<PositionMove> moves;

        private PositionSnapshot(String fen, int turn, int state, List<PositionMove> moves,
                                 String initialFen, List<Node> path, String positionCommand) {
            this.initialFen = initialFen;
            history = new int[path.size()];
            duckHistory = new int[path.size()];
            for (int i = 0; i < path.size(); i++) {
                history[i] = path.get(i).getEntry().move;
                duckHistory[i] = path.get(i).getEntry().duckMove;
            }
            this.positionCommand = positionCommand;
            this.fen = fen;
            this.turn = turn;
            this.state = state;
            this.moves = Collections.unmodifiableList(moves);
        }

        public PositionMove findMove(String uci) {
            for (PositionMove move : moves) if (move.uci.equals(uci)) return move;
            return null;
        }
    }

    /** Main-thread only; both placement and history must match before cloning the live board. */
    public boolean isCurrentPosition(PositionSnapshot position) {
        return pendingDuckMove == null && position.fen.equals(jni.toFEN())
            && position.positionCommand.equals(gameTree.positionCommand(currentNode));
    }

    public static final class PositionMove {
        public final int move;
        public final String uci;
        public final String san;

        private PositionMove(int move, String san) {
            this.move = move;
            this.san = san;
            this.uci = Pos.toString(Move.getFrom(move)) + Pos.toString(Move.getTo(move))
                + (Move.isPromotionMove(move) ? "pnbrqk".charAt(Move.getPromotionPiece(move)) : "");
        }
    }

    private static final String[] NO_RUST_HISTORY = new String[0];

    /** {@link RustBoardResult#gameState} (Play/Check/Mate/Stalemate/DrawMaterial/DrawFiftyMove/DrawRepetition), indexed by its ordinal, mapped to {@link BoardConstants}'s legacy numbering. */
    private static final int[] LEGACY_STATE_FOR_RUST_CODE = {
        BoardConstants.PLAY, BoardConstants.CHECK, BoardConstants.MATE, BoardConstants.STALEMATE,
        BoardConstants.DRAW_MATERIAL, BoardConstants.DRAW_50, BoardConstants.DRAW_REPEAT,
    };

    static int legacyGameState(int rustGameStateCode) {
        return LEGACY_STATE_FOR_RUST_CODE[rustGameStateCode];
    }

    /** The {@link BoardConstants} piece at a square owned by {@code turn}, or {@link BoardConstants#FIELD}. */
    static int legacyPieceAt(char rustPieceChar, int turn) {
        if (rustPieceChar == 0 || Character.isUpperCase(rustPieceChar) != (turn == BoardConstants.WHITE)) {
            return BoardConstants.FIELD;
        }
        switch (Character.toLowerCase(rustPieceChar)) {
            case 'p': return BoardConstants.PAWN;
            case 'n': return BoardConstants.KNIGHT;
            case 'b': return BoardConstants.BISHOP;
            case 'r': return BoardConstants.ROOK;
            case 'q': return BoardConstants.QUEEN;
            case 'k': return BoardConstants.KING;
            default: return BoardConstants.FIELD; // the duck marker '$'
        }
    }

    RustSearchRequest.Variant rustVariant() {
        return isDuckVariant() ? RustSearchRequest.Variant.DUCK
            : isChess960Variant() ? RustSearchRequest.Variant.CHESS_960 : RustSearchRequest.Variant.STANDARD;
    }

    /**
     * The current position's query result, sourced from the live legacy board's FEN, or
     * {@code null} for a position Rust's {@code from_fen} rejects. Board setup freely leaves the
     * native board in such states (no king, two kings of one colour) while pieces are added and
     * removed one at a time; callers fall back to the legacy engine for exactly those positions.
     */
    private RustBoardResult rustQueryOrNull() {
        try {
            return RustBoardNative.query(rustVariant(), jni.toFEN(), NO_RUST_HISTORY);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Each legal move's UCI and SAN, sourced from the Rust board API; paired with the matching
     * legacy packed move, since move application itself still goes through the legacy engine.
     * A Duck turn's coordinate carries a duck square too ({@code e2e4@d5}); only the piece-move
     * part is kept here, once per distinct piece move.
     */
    private List<PositionMove> rustLegalMoves(String fen, RustSearchRequest.Variant variant) {
        int[] files = variant == RustSearchRequest.Variant.CHESS_960
            ? RustSearchRequest.chess960Files(fen, jni.getTurn() == BoardConstants.WHITE) : null;
        Map<String, Integer> packedByCoordinate = new HashMap<>();
        int count = jni.getMoveArraySize();
        for (int i = 0; i < count; i++) {
            int move = jni.getMoveArrayAt(i);
            packedByCoordinate.put(RustSearchRequest.toRustCoordinate(move, files), move);
        }
        RustBoardResult legal = RustBoardNative.legalMoves(variant, fen, NO_RUST_HISTORY);
        List<PositionMove> moves = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String coordinate : RustBoardResult.words(legal.moves)) {
            String pieceMove = pieceMoveOf(coordinate);
            if (!seen.add(pieceMove)) continue;
            Integer packed = packedByCoordinate.get(pieceMove);
            if (packed == null) continue;
            String san = RustBoardNative.san(variant, fen, NO_RUST_HISTORY, pieceMove).san;
            moves.add(new PositionMove(packed, san));
        }
        return moves;
    }

    private static String pieceMoveOf(String coordinate) {
        int at = coordinate.indexOf('@');
        return at < 0 ? coordinate : coordinate.substring(0, at);
    }

    /**
     * The sole legal move from {@code from} to {@code to} for the currently selected promotion
     * piece, sourced from the Rust board API's legal-move list; null when none matches.
     */
    private PositionMove resolveLegalMove(int from, int to, String fen, RustSearchRequest.Variant variant) {
        for (PositionMove candidate : rustLegalMoves(fen, variant)) {
            int move = candidate.move;
            if (Move.getFrom(move) != from || Move.getTo(move) != to) continue;
            if (Move.isPromotionMove(move) && Move.getPromotionPiece(move) != promotionPiece) continue;
            return candidate;
        }
        return null;
    }

    /**
     * Resolves a from/to move via the Rust board API and applies it there, then commits the
     * same already-resolved move onto the live board so the rest of GameApi (ply bookkeeping,
     * undo, search) keeps working from it. Null when no legal move matches.
     */
    private PositionMove applyRustMove(int from, int to) {
        String fen = jni.toFEN();
        RustSearchRequest.Variant variant = rustVariant();
        PositionMove resolved = resolveLegalMove(from, to, fen, variant);
        if (resolved == null) return null;
        int[] files = variant == RustSearchRequest.Variant.CHESS_960
            ? RustSearchRequest.chess960Files(fen, jni.getTurn() == BoardConstants.WHITE) : null;
        String coordinate = RustSearchRequest.toRustCoordinate(resolved.move, files);
        RustBoardResult applied = RustBoardNative.applyMove(variant, fen, NO_RUST_HISTORY, coordinate);
        if (jni.move(resolved.move) == 0) return null;
        if (!applied.fen.equals(jni.toFEN()))
            Log.w(TAG, "Rust and legacy engine disagreed after " + coordinate
                + ": " + applied.fen + " vs " + jni.toFEN());
        return resolved;
    }

    /** Generate piece-move SAN from the preceding position, undoing the move if Rust rejects it. */
    private String appliedMoveSan(String previousFen, RustSearchRequest.Variant variant) {
        int[] files = variant == RustSearchRequest.Variant.CHESS_960
            ? RustSearchRequest.chess960Files(previousFen, previousFen.split("\\s+")[1].equals("w")) : null;
        String coordinate = RustSearchRequest.toRustCoordinate(jni.getMyMove(), files);
        try {
            return RustBoardNative.san(variant, previousFen, NO_RUST_HISTORY, coordinate).san;
        } catch (IllegalArgumentException ex) {
            jni.undo();
            return null;
        }
    }

    /** Main-thread only. Inspect a recorded position without dispatching navigation or changing the tree. */
    public PositionSnapshot getPositionSnapshot(Node node) {
        if (pendingDuckMove != null || !gameTree.contains(node)) return null;
        Node original = currentNode;
        try {
            if (!navigateTo(node)) return null;
            String fen = jni.toFEN();
            List<PositionMove> moves = rustLegalMoves(fen, rustVariant());
            return new PositionSnapshot(fen, jni.getTurn(), jni.getState(), moves,
                gameTree.getInitialFen(), gameTree.pathTo(node), gameTree.positionCommand(node));
        } finally {
            if (!navigateTo(original)) restoreBoard(original, null);
        }
    }

    /** Call after the native board has been initialized, never merely to clear a move list. */
    protected final void resetPGNHistory() {
        resetPGNHistory(jni.toFEN());
    }

    private void resetPGNHistory(String initialFen) {
        gameTree = new GameTree(initialFen, jni.getState(), jni.getTurn());
        currentNode = gameTree.getRoot();
        pendingDuckMove = null;
        importedResult = null;
        treeChanged = true;
    }

    public GameApi() {
        this(false);
    }

    /** @param preservePosition keep the shared native board instead of starting a new game */
    public GameApi(boolean preservePosition) {
        jni = JNI.getInstance();
        if (!preservePosition) {
            jni.newGame();
        }
        pgnTags = new HashMap<String, String>();
        resetPGNHistory();
    }

    public void addListener(GameListener listener) {
        this.listeners.add(listener);
    }

    public void removeListener(GameListener listener) {
        this.listeners.remove(listener);
    }

    public String getOpponentPlayerName(int myTurn) {
        return getPGNHeadProperty(myTurn == BoardConstants.BLACK ? "White" : "Black");
    }

    public String getMyPlayerName(int myTurn) {
        return getPGNHeadProperty(myTurn == BoardConstants.WHITE ? "White" : "Black");
    }

    public boolean isEnded() {
        return getFinalState() != -1 || isPositionEnded();
    }

    private static final int SQUARE_A1 = Pos.fromColAndRow(0, 7);
    private static final int SQUARE_E1 = Pos.fromColAndRow(4, 7);
    private static final int SQUARE_H1 = Pos.fromColAndRow(7, 7);
    private static final int SQUARE_A8 = Pos.fromColAndRow(0, 0);
    private static final int SQUARE_E8 = Pos.fromColAndRow(4, 0);
    private static final int SQUARE_H8 = Pos.fromColAndRow(7, 0);

    /**
     * Whether this Game is Standard chess, as opposed to Duck or Chess 960. Duck is tracked by
     * the native board variant rather than the Variant tag, which only carries "Duck" for a game
     * loaded from PGN. Chess 960 has no native variant of its own and the Variant tag is only set
     * by {@link #newGameRandomFischer}, so a Chess 960 position reached through a raw FEN (e.g. a
     * Setup handoff) is recognized instead by a castling right whose king or rook sits off its
     * classical home square; a Standard game can never retain a right once either has moved.
     */
    public boolean isStandardVariant() {
        if (jni.getVariant() == BoardConstants.VARIANT_DUCK) return false;
        String variant = pgnTags.get("Variant");
        if (variant != null && !variant.equalsIgnoreCase("Standard")) return false;
        return (jni.getWhiteCanCastleShort() == 0 || isKingAndRookAt(BoardConstants.WHITE, SQUARE_E1, SQUARE_H1))
            && (jni.getWhiteCanCastleLong() == 0 || isKingAndRookAt(BoardConstants.WHITE, SQUARE_E1, SQUARE_A1))
            && (jni.getBlackCanCastleShort() == 0 || isKingAndRookAt(BoardConstants.BLACK, SQUARE_E8, SQUARE_H8))
            && (jni.getBlackCanCastleLong() == 0 || isKingAndRookAt(BoardConstants.BLACK, SQUARE_E8, SQUARE_A8));
    }

    private boolean isKingAndRookAt(int turn, int kingSquare, int rookSquare) {
        return jni.pieceAt(turn, kingSquare) == BoardConstants.KING
            && jni.pieceAt(turn, rookSquare) == BoardConstants.ROOK;
    }

    /** Whether this Game is Chess 960, as opposed to Standard or Duck (see {@link #isStandardVariant}). */
    public boolean isChess960Variant() {
        return jni.getVariant() != BoardConstants.VARIANT_DUCK && !isStandardVariant();
    }

    public int getState() {
        int finalState = getFinalState();
        if (finalState != -1) {
            return finalState;
        }
        return getEngineState();
    }

    /** Returns the native engine state without the recorded game's final state. */
    public int getEngineState() {
        RustBoardResult result = rustQueryOrNull();
        return result == null ? jni.getState() : legacyGameState(result.gameState);
    }

    public int getFinalState() {
        return isAtLineEnd() ? currentNode.getFinalState() : -1;
    }

    public boolean setFinalState(int state) {
        if (!isAtLineEnd() || !gameTree.setFinalState(currentNode, state)) return false;
        if (isOnMainLine()) importedResult = null;
        if (state == BoardConstants.WHITE_FORFEIT_TIME) dispatchPlayerForfeitedOnTime(BoardConstants.WHITE);
        else if (state == BoardConstants.BLACK_FORFEIT_TIME) dispatchPlayerForfeitedOnTime(BoardConstants.BLACK);
        dispatchState();
        return true;
    }

    public boolean requestMove(int from, int to) {
        Log.i(TAG, "requestMove");
        if (pendingDuckMove != null || isEnded()) {
            return false;
        }

        PositionMove resolved = applyRustMove(from, to);
        if (resolved == null) {
            dispatchIllegalMove();
            return false;
        }

        final int move = jni.getMyMove();

        addPGNEntry(jni.getNumBoard(), resolved.san, "", move, -1);

        dispatchMove(move);

        return true;
    }

    /**
     * Applies and records a server move without notifying listeners or checking the recorded
     * final state. A null promotion leaves the engine's promotion selection unchanged.
     * Returns the applied move, or zero if the engine rejects it.
     */
    public int applyServerMove(int from, int to, Integer promo) {
        if (promo != null) setPromo(promo);
        PositionMove resolved = applyRustMove(from, to);
        if (resolved == null) return 0;
        int move = jni.getMyMove();
        addPGNEntry(jni.getNumBoard(), resolved.san, "", move, -1);
        return move;
    }

    public boolean requestMoveCastle(int from, int to) {
        if (pendingDuckMove != null || isEnded()) {
            return false;
        }

        String previousFen = jni.toFEN();
        RustSearchRequest.Variant variant = rustVariant();
        if (jni.doCastleMove(from, to) == 0) {
            dispatchIllegalMove();
            return false;
        }
        final int move = jni.getMyMove();

        String san = appliedMoveSan(previousFen, variant);
        if (san == null) return false;
        addPGNEntry(jni.getNumBoard(), san, "", move, -1);

        dispatchMove(move);

        return true;
    }

    public boolean requestDuckMove(int duckPos) {
//        Log.d(TAG, " requestDuckMove " + Pos.toString(duckPos));
        if (!moveDuck(duckPos)) {
            // @TODO is this needed/possible? Prevent from moving on top of a piece should be enough
            // dispatchIllegalMove();
            return false;
        }

        dispatchDuckMove(duckPos);

        return true;
    }

    public boolean isPromotionMove(int from, int to) {
        if (jni.pieceAt(BoardConstants.WHITE, from) == BoardConstants.PAWN &&
            BoardConstants.ROW_TURN[BoardConstants.WHITE][from] == 6 &&
            BoardConstants.ROW_TURN[BoardConstants.WHITE][to] == 7 &&
            jni.getTurn() == BoardConstants.WHITE
            ||
            jni.pieceAt(BoardConstants.BLACK, from) == BoardConstants.PAWN &&
                BoardConstants.ROW_TURN[BoardConstants.BLACK][from] == 6 &&
                BoardConstants.ROW_TURN[BoardConstants.BLACK][to] == 7 &&
                jni.getTurn() == BoardConstants.BLACK) {
            return true;
        }
        return false;
    }

    public void move(int move, int duckMove) {
        if (applyMove(move, duckMove)) dispatchMove(move);
    }

    /** Applies and records a move without notifications; used by importers and engine batches. */
    public boolean applyMove(int move, int duckMove) {
        if (pendingDuckMove != null) return false;
        String previousFen = jni.toFEN();
        RustSearchRequest.Variant variant = rustVariant();
        if (jni.move(move) == 0) return false;
        String san = appliedMoveSan(previousFen, variant);
        if (san == null) return false;
        if (duckMove != -1 && jni.requestDuckMove(duckMove) == 0) {
            jni.undo();
            return false;
        }
        addPGNEntry(jni.getNumBoard(), san, "", jni.getMyMove(), duckMove);
        return true;
    }

    public void undoMove() { stepBack(); }
    public void nextMove() { stepForward(); }

    public boolean stepBack() {
        if (pendingDuckMove != null) {
            jni.undo();
            pendingDuckMove = null;
            dispatchNavigation();
            return true;
        }
        return currentNode.getParent() != null && goTo(currentNode.getParent());
    }

    /** Remove a rejected puzzle move. Unlike stepBack, this deliberately discards its subtree. */
    protected final boolean discardLastMove() {
        if (pendingDuckMove != null || currentNode.getParent() == null) return false;
        Node rejected = currentNode;
        if (!navigateTo(rejected.getParent())) return false;
        gameTree.removeContinuation(rejected);
        treeChanged = true;
        return true;
    }

    public boolean stepForward() {
        return currentNode.getNext() != null && goTo(currentNode.getNext());
    }

    public boolean stepInto(Node continuation) {
        return continuation != null && continuation.getParent() == currentNode && goTo(continuation);
    }

    public boolean goTo(Node target) {
        if (pendingDuckMove != null || !navigateTo(target)) return false;
        dispatchNavigation();
        return true;
    }

    private boolean navigateTo(Node target) {
        if (!gameTree.contains(target)) return false;
        Node original = currentNode;
        Node common = currentNode, other = target;
        while (common.getPly() > other.getPly()) common = common.getParent();
        while (other.getPly() > common.getPly()) other = other.getParent();
        while (common != other) { common = common.getParent(); other = other.getParent(); }
        while (currentNode != common) {
            int ply = jni.getNumBoard();
            jni.undo();
            if (jni.getNumBoard() != ply - 1) return false;
            currentNode = currentNode.getParent();
        }
        Deque<Node> path = new ArrayDeque<>();
        for (Node node = target; node != common; node = node.getParent()) path.push(node);
        while (!path.isEmpty()) {
            Node node = path.pop();
            if (!replay(node.getEntry())) {
                restoreBoard(original, null);
                return false;
            }
            currentNode = node;
        }
        return true;
    }

    private boolean replay(PGNEntry entry) {
        if (jni.move(entry.move) == 0) return false;
        if (entry.duckMove != -1 && jni.requestDuckMove(entry.duckMove) == 0) {
            jni.undo();
            return false;
        }
        return true;
    }

    /** Rebuild this game's native board after another screen may have used the shared JNI. */
    public void restoreCurrentBoard() {
        restoreBoard(currentNode, pendingDuckMove);
    }

    private void restoreBoard(Node target, PGNEntry pending) {
        if (!jni.initFEN(gameTree.getInitialFen())) throw new IllegalStateException("Cannot restore starting board");
        currentNode = gameTree.getRoot();
        for (Node node : gameTree.pathTo(target)) {
            if (!replay(node.getEntry())) throw new IllegalStateException("Cannot restore recorded move");
            currentNode = node;
        }
        pendingDuckMove = pending;
        if (pending != null && !replay(pending)) throw new IllegalStateException("Cannot restore pending duck move");
    }

    /** Returns to the preferred sibling of the nearest variation ancestor. */
    public boolean returnToParentLine() {
        for (Node node = currentNode; node.getParent() != null; node = node.getParent()) {
            if (node.getParent().getNext() != node) return goTo(node.getParent().getNext());
        }
        return false;
    }

    public boolean returnToMainLine() {
        Node target = currentNode;
        for (Node node = currentNode; node.getParent() != null; node = node.getParent()) {
            if (node.getParent().getNext() != node) target = node.getParent().getNext();
        }
        return target != currentNode && goTo(target);
    }

    /** SAN includes the duck square for duck chess. Failure preserves the original position. */
    public Node createVariation(Node branchPoint, String firstMove) {
        if (pendingDuckMove != null || !gameTree.contains(branchPoint)) return null;
        Node original = currentNode;
        if (!navigateTo(branchPoint)) return null;
        if (!applyPGNMove(firstMove)) {
            navigateTo(original);
            return null;
        }
        dispatchNavigation();
        return currentNode;
    }

    public boolean deleteVariation(Node variationRoot) {
        if (pendingDuckMove != null || !gameTree.contains(variationRoot)
            || variationRoot.getParent() == null || variationRoot.getParent().getNext() == variationRoot) return false;
        for (Node node = currentNode; node != null; node = node.getParent()) {
            if (node == variationRoot && !navigateTo(variationRoot.getParent())) return false;
        }
        gameTree.deleteVariation(variationRoot);
        treeChanged = true;
        dispatchNavigation();
        return true;
    }

    public boolean promoteVariation(Node variationRoot) {
        if (pendingDuckMove != null || !gameTree.promoteVariation(variationRoot)) return false;
        importedResult = null;
        treeChanged = true;
        dispatchNavigation();
        return true;
    }

    private void dispatchNavigation() {
        dispatchHistoryPositionChanged(getCurrentPly());
        dispatchState();
    }

    /** Compatibility navigation: board numbers always refer to the main line. */
    public void jumpToBoardNum(int toNumBoard) { goTo(gameTree.mainLineAt(toNumBoard)); }
    public int getPGNSize() { return gameTree.mainLineEnd().getPly(); }
    public boolean isAtEndOfPGN() { return pendingDuckMove == null && currentNode == gameTree.mainLineEnd(); }

    public synchronized boolean isLegalMove(int from, int to) {
        int checkMove = Move.makeMove(from, to);
        int size = jni.getMoveArraySize();
        int move;
        for (int i = 0; i < size; i++) {
            move = jni.getMoveArrayAt(i);
            if (Move.equalPositions(checkMove, move)) {
                return true;
            }
        }
        return false;
    }

    public void newGame() {
        newGame(BoardConstants.VARIANT_DEFAULT);
    }

    public void newGame(int variant) {

        pgnTags.clear();
        pgnTags.put("Event", "?");
        pgnTags.put("Site", "?");
        pgnTags.put("Round", "?");
        pgnTags.put("White", "?");
        pgnTags.put("Black", "?");
        pgnTags.put("Date", PgnDate.format(new Date()));

        jni.newGame(variant);
        resetPGNHistory();

        if (variant == BoardConstants.VARIANT_DUCK) {
            pgnTags.put("Setup", "1");
            pgnTags.put("FEN", jni.toFEN());
        }

        dispatchNewGameStarted(variant);
        dispatchState();
    }


    public boolean initFEN(String sFEN, boolean resetHead) {
        Node oldNode = currentNode;
        PGNEntry oldPending = pendingDuckMove;
        if (jni.initFEN(sFEN)) {

            if (resetHead) {
                pgnTags.clear();
                pgnTags.put("Event", "?");
                pgnTags.put("Site", "?");
                pgnTags.put("Round", "?");
                pgnTags.put("White", Resources.getSystem().getString(android.R.string.unknownName));
                pgnTags.put("Black", Resources.getSystem().getString(android.R.string.unknownName));
            }
            pgnTags.put("Setup", "1");
            pgnTags.put("FEN", sFEN);

            resetPGNHistory(sFEN);

            dispatchState();
            dispatchGameLoaded();
            return true;
        }
        restoreBoard(oldNode, oldPending);
        return false;
    }

    public int getNumBoard() {
        return jni.getNumBoard();
    }

    public int getNumCaptured(int turnAt, int piece) {
        RustBoardResult result = rustQueryOrNull();
        if (result == null) return jni.getNumCaptured(turnAt, piece);
        // captured[] holds 5 pieces (pawn/knight/bishop/rook/queen) per side, Black (turn 0) then White (turn 1).
        return result.captured[turnAt * 5 + piece];
    }

    /** No Rust equivalent exists for the legacy Zobrist hash, the ECO opening book's lookup key. */
    public long getHashKey() {
        return jni.getHashKey();
    }

    /** Captures the live position before dispatching the search worker; zero means busy or invalid limits. */
    public long prepareSearch(int ply, int msecs, int quiescentOn) {
        return jni.prepareSearch(ply, msecs, quiescentOn);
    }

    public void runSearch(long searchId) {
        jni.runSearch(searchId);
    }

    public boolean isSearchCurrent(long searchId) {
        return jni.isSearchCurrent(searchId);
    }

    public SearchResult getSearchResult(long searchId) {
        return SearchResult.decode(jni.getSearchResult(searchId));
    }

    /** Decoded view of the native engine's packed {@code getSearchResult} array. */
    public static final class SearchResult {
        private static final int PV_LENGTH = 20;
        private static final int PV_OFFSET = 5;
        private static final int DUCK_PV_OFFSET = PV_OFFSET + PV_LENGTH;
        private static final int HAS_EVALUATION_INDEX = DUCK_PV_OFFSET + PV_LENGTH;
        private static final int COMPLETED_DEPTH_INDEX = HAS_EVALUATION_INDEX + 1;

        public final int move;
        public final int duckMove;
        public final int score;
        public final int depth;
        public final int evalCount;
        public final int[] principalVariation;
        public final int[] principalDuckVariation;
        public final boolean hasEvaluation;
        public final int completedDepth;

        private SearchResult(int move, int duckMove, int score, int depth, int evalCount,
                             int[] principalVariation, int[] principalDuckVariation,
                             boolean hasEvaluation, int completedDepth) {
            this.move = move;
            this.duckMove = duckMove;
            this.score = score;
            this.depth = depth;
            this.evalCount = evalCount;
            this.principalVariation = principalVariation;
            this.principalDuckVariation = principalDuckVariation;
            this.hasEvaluation = hasEvaluation;
            this.completedDepth = completedDepth;
        }

        /**
         * Decodes the raw array: move, duck, value, depth, evaluations, 20 PV moves, 20 duck
         * moves, evaluation-available flag, completed depth.
         */
        public static SearchResult decode(int[] raw) {
            int pvLength = Math.max(0, Math.min(raw[3], PV_LENGTH));
            return new SearchResult(raw[0], raw[1], raw[2], raw[3], raw[4],
                Arrays.copyOfRange(raw, PV_OFFSET, PV_OFFSET + pvLength),
                Arrays.copyOfRange(raw, DUCK_PV_OFFSET, DUCK_PV_OFFSET + pvLength),
                raw[HAS_EVALUATION_INDEX] != 0, raw[COMPLETED_DEPTH_INDEX]);
        }
    }

    public int getDuckPos() {
        return jni.getDuckPos();
    }

    public int getMyDuckPos() {
        return jni.getMyDuckPos();
    }

    /** Selects the piece {@link #requestMove} and {@link #applyServerMove} promote a pawn to. */
    public void setPromo(int piece) {
        promotionPiece = piece;
    }

    /** Raw board edits leave PGN history, tags and listeners untouched. */
    public void reset() {
        jni.reset();
    }

    public void putPiece(int pos, int piece, int color) {
        jni.putPiece(pos, piece, color);
    }

    public void removePiece(int turn, int pos) {
        jni.removePiece(turn, pos);
    }

    public void setTurn(int turn) {
        jni.setTurn(turn);
    }

    public void setCastlingsEPAnd50(int wccl, int wccs, int bccl, int bccs, int ep, int r50) {
        jni.setCastlingsEPAnd50(wccl, wccs, bccl, bccs, ep, r50);
    }

    public void commitBoard() {
        jni.commitBoard();
    }

    public int isLegalPosition() {
        return jni.isLegalPosition();
    }

    public int getWhiteCanCastleLong() {
        RustBoardResult result = rustQueryOrNull();
        return result == null ? jni.getWhiteCanCastleLong() : (result.castleWhiteQueenside ? 1 : 0);
    }

    public int getWhiteCanCastleShort() {
        RustBoardResult result = rustQueryOrNull();
        return result == null ? jni.getWhiteCanCastleShort() : (result.castleWhiteKingside ? 1 : 0);
    }

    public int getBlackCanCastleLong() {
        RustBoardResult result = rustQueryOrNull();
        return result == null ? jni.getBlackCanCastleLong() : (result.castleBlackQueenside ? 1 : 0);
    }

    public int getBlackCanCastleShort() {
        RustBoardResult result = rustQueryOrNull();
        return result == null ? jni.getBlackCanCastleShort() : (result.castleBlackKingside ? 1 : 0);
    }

    public int getEnpassantPosition() {
        return jni.getEnpassantPosition();
    }

    public void setVariant(int variant) {
        jni.setVariant(variant);
    }

    /** Places the duck directly, without recording a move or dispatching events. */
    public int placeDuck(int pos) {
        return jni.requestDuckMove(pos);
    }

    /** Checks only the native position, ignoring the recorded game's final state. */
    public boolean isPositionEnded() {
        int state = getEngineState();
        return state != BoardConstants.PLAY && state != BoardConstants.CHECK;
    }

    /** Resets the native board to standard chess without changing PGN data or dispatching events. */
    public void resetToStandardPositionWithoutHistory() {
        jni.newGame();
    }

    /** Loads the native board without changing PGN data or dispatching events. */
    public boolean loadFENWithoutHistory(String fen) {
        return jni.initFEN(fen);
    }

    public int pieceAt(int turn, int pos) {
        try {
            char piece = RustBoardNative.pieceAt(rustVariant(), jni.toFEN(), NO_RUST_HISTORY, Pos.toString(pos)).piece;
            return legacyPieceAt(piece, turn);
        } catch (IllegalArgumentException ex) {
            // Board setup can leave the native board in a state Rust's from_fen rejects.
            return jni.pieceAt(turn, pos);
        }
    }

    public int getMyMove() {
        return jni.getMyMove();
    }

    public String getMyMoveToString() {
        PGNEntry entry = pendingDuckMove != null ? pendingDuckMove : currentNode.getEntry();
        return entry == null ? "" : entry.sMove;
    }

    public int[] getAttackerPositionsTo(int pos, int byTurn) {
        RustBoardResult result;
        try {
            result = RustBoardNative.attackers(rustVariant(), jni.toFEN(), NO_RUST_HISTORY,
                byTurn == BoardConstants.WHITE, Pos.toString(pos));
        } catch (IllegalArgumentException ex) {
            // Board setup can leave the native board in a state Rust's from_fen rejects.
            return jni.getAttackerPositionsTo(pos, byTurn);
        }
        String[] squares = RustBoardResult.words(result.attackers);
        int[] positions = new int[squares.length];
        for (int i = 0; i < squares.length; i++) {
            try {
                positions[i] = Pos.fromString(squares[i]);
            } catch (Exception ex) {
                throw new IllegalStateException("Invalid square from Rust board API: " + squares[i], ex);
            }
        }
        return positions;
    }

    public int isAmbiguousCastle(int from, int to) {
        return jni.isAmbiguousCastle(from, to);
    }

    public int getMoveArraySize() {
        return jni.getMoveArraySize();
    }

    public int getMoveArrayAt(int index) {
        return jni.getMoveArrayAt(index);
    }

    public String getFEN() {
        RustBoardResult result = rustQueryOrNull();
        return result == null ? jni.toFEN() : result.fen;
    }

    public int newGameRandomFischer(int seed) {

        int ret = jni.initRandomFisher(seed);

        pgnTags.clear();
        pgnTags.put("Event", "?");
        pgnTags.put("Site", "?");
        pgnTags.put("Round", "?");
        pgnTags.put("White", Resources.getSystem().getString(android.R.string.unknownName));
        pgnTags.put("Black", Resources.getSystem().getString(android.R.string.unknownName));

        pgnTags.put("Variant", "Fischerandom");
        pgnTags.put("Setup", "1");
        pgnTags.put("FEN", jni.toFEN());

        resetPGNHistory();

        dispatchNewGameStarted(jni.getVariant());
        dispatchState();
        return ret;
    }

    public boolean loadPGN(String s) {
        lastPgnError = null;
        if (s == null || s.length() > MAX_PGN_SIZE) {
            lastPgnError = "PGN is missing or exceeds the size limit";
            return false;
        }
        PgnDocument document;
        try {
            document = Pgn.parse(s);
        } catch (PgnSyntaxError ex) {
            lastPgnError = ex.getMessage();
            return false;
        }
        GameTree oldTree = gameTree;
        Node oldNode = currentNode;
        PGNEntry oldPending = pendingDuckMove;
        String oldResult = importedResult;
        boolean oldChanged = treeChanged;
        HashMap<String, String> oldTags = new HashMap<>(pgnTags);
        loadingPgn = true;
        try {
            pgnTags.clear();
            pgnTags.putAll(document.tags);
            if (pgnTags.containsKey("FEN")) {
                if (!jni.initFEN(pgnTags.get("FEN"))) throw new IllegalArgumentException("Invalid FEN");
            } else {
                jni.newGame("Duck".equalsIgnoreCase(pgnTags.get("Variant"))
                    ? BoardConstants.VARIANT_DUCK : BoardConstants.VARIANT_DEFAULT);
            }
            resetPGNHistory(pgnTags.getOrDefault("FEN", jni.toFEN()));
            gameTree.setRootComment(document.rootComment);
            replayLine(document.mainLine);
            if (!navigateTo(gameTree.mainLineEnd())) throw new IllegalArgumentException("Cannot restore main line");
            importedResult = document.result;
            if (jni.isEnded() == 0) {
                if ("1-0".equals(importedResult)) gameTree.setFinalState(currentNode, BoardConstants.BLACK_RESIGNED);
                else if ("0-1".equals(importedResult)) gameTree.setFinalState(currentNode, BoardConstants.WHITE_RESIGNED);
                else if ("1/2-1/2".equals(importedResult)) gameTree.setFinalState(currentNode, BoardConstants.DRAW_AGREEMENT);
            }
        } catch (IllegalArgumentException ex) {
            lastPgnError = ex.getMessage();
            gameTree = oldTree;
            importedResult = oldResult;
            pgnTags.clear();
            pgnTags.putAll(oldTags);
            treeChanged = oldChanged;
            restoreBoard(oldNode, oldPending);
            return false;
        } finally {
            loadingPgn = false;
        }
        dispatchGameLoaded();
        dispatchState();
        return true;
    }

    /**
     * Loads a stored Game: its PGN, with the tags the user edits taken from the record's columns.
     * Nothing is changed when the PGN cannot be loaded.
     */
    public boolean loadGame(GameRecord record) {
        if (!loadPGN(record.pgn)) return false;
        for (Map.Entry<String, String> tag : record.tagOverrides().entrySet()) {
            setPGNTag(tag.getKey(), tag.getValue());
        }
        return true;
    }

    public static Matcher getMoveMatcher(String sMove) {
        return Pgn.MOVE.matcher(sMove);
    }

    public boolean requestMove(String sMove) {
        if (isEnded() || !applyPGNMove(sMove)) return false;
        dispatchMove(jni.getMyMove());
        return true;
    }

    /** Apply a complete SAN move without dispatching, for import/replay batches. */
    public boolean applyPGNMove(String sMove) {
        if (sMove == null || pendingDuckMove != null) return false;
        Matcher match = getMoveMatcher(sMove.trim());
        if (match.matches()) return requestMove(match, null, "");
        match = Pgn.CASTLING.matcher(sMove.trim().replace('0', 'O'));
        return match.matches() && requestMove(match, match.group(1), "");
    }

    public void resetForfeitTime() {
        int finalState = getFinalState();
        if (finalState == BoardConstants.WHITE_FORFEIT_TIME || finalState == BoardConstants.BLACK_FORFEIT_TIME
            || finalState == BoardConstants.WHITE_RESIGNED || finalState == BoardConstants.BLACK_RESIGNED) {
            gameTree.setFinalState(currentNode, -1);
            if (isOnMainLine()) importedResult = null;
            dispatchGameResumed();
            dispatchState();
        }
    }

    protected void dispatchMove(final int move) {
        dispatchTreeChanged();
//        Log.d(TAG, "dispatchMove " + move);

        for (GameListener listener : listeners) {
            listener.onMoveApplied(move);
        }
    }

    protected void dispatchDuckMove(final int duckMove) {
        dispatchTreeChanged();
        Log.d(TAG, "dispatchDuckMove " + duckMove);

        for (GameListener listener : listeners) {
            listener.onDuckMoveApplied(duckMove);
        }
    }

    protected void dispatchState() {
        dispatchTreeChanged();
//        Log.d(TAG, "dispatchState");

        for (GameListener listener : listeners) {
            listener.OnState();
        }
    }

    protected void dispatchIllegalMove() {
        Log.d(TAG, "dispatchIllegalMove");

        for (GameListener listener : listeners) {
            listener.onIllegalMoveAttempted();
        }
    }

    private void dispatchTreeChanged() {
        if (!treeChanged || loadingPgn) return;
        treeChanged = false;
        for (GameListener listener : listeners) listener.onGameTreeChanged();
    }

    protected void dispatchHistoryPositionChanged(final int boardNumber) {
        dispatchTreeChanged();
        for (GameListener listener : listeners) listener.onPositionChanged(currentNode);
        for (GameListener listener : listeners) {
            listener.onHistoryPositionChanged(boardNumber);
        }
    }

    protected void dispatchNewGameStarted(final int variant) {
        for (GameListener listener : listeners) {
            listener.onNewGameStarted(variant);
        }
    }

    protected void dispatchGameLoaded() {
        for (GameListener listener : listeners) {
            listener.onGameLoaded();
        }
    }

    protected void dispatchGameResumed() {
        for (GameListener listener : listeners) {
            listener.onGameResumed();
        }
    }

    protected void dispatchPlayerResigned(final int color) {
        for (GameListener listener : listeners) {
            listener.onPlayerResigned(color);
        }
    }

    protected void dispatchDrawAgreed() {
        for (GameListener listener : listeners) {
            listener.onDrawAgreed();
        }
    }

    protected void dispatchPlayerForfeitedOnTime(final int color) {
        for (GameListener listener : listeners) {
            listener.onPlayerForfeitedOnTime(color);
        }
    }

    private boolean moveDuck(int duckMove) {
        if (pendingDuckMove == null || jni.requestDuckMove(duckMove) == 0) return false;
        PGNEntry entry = pendingDuckMove;
        pendingDuckMove = null;
        entry.duckMove = duckMove;
        recordEntry(entry);
        return true;
    }

    private static int promotionPieceFromSuffix(String suffix) {
        return "=Q".equals(suffix) ? BoardConstants.QUEEN
            : "=R".equals(suffix) ? BoardConstants.ROOK
            : "=B".equals(suffix) ? BoardConstants.BISHOP : BoardConstants.KNIGHT;
    }

    private static int promotionPieceFromLetter(char letter) {
        return letter == 'q' ? BoardConstants.QUEEN
            : letter == 'r' ? BoardConstants.ROOK
            : letter == 'b' ? BoardConstants.BISHOP : BoardConstants.KNIGHT;
    }

    private static boolean pieceMatches(char fenPieceChar, int piece) {
        switch (Character.toLowerCase(fenPieceChar)) {
            case 'k': return piece == BoardConstants.KING;
            case 'q': return piece == BoardConstants.QUEEN;
            case 'r': return piece == BoardConstants.ROOK;
            case 'b': return piece == BoardConstants.BISHOP;
            case 'n': return piece == BoardConstants.KNIGHT;
            default: return piece == BoardConstants.PAWN;
        }
    }

    /**
     * The square of the single legal move's piece matching a SAN move token's piece letter,
     * destination, capture flag, promotion and disambiguating file/rank - or -1 when zero or
     * more than one legal move matches. Sourced from the Rust board API's legal-move list.
     */
    private int resolveDisambiguatedFrom(Matcher match, int destination, String fen, RustSearchRequest.Variant variant) {
        String pieceName = match.group(1);
        int piece = pieceName == null ? BoardConstants.PAWN
            : "K".equals(pieceName) ? BoardConstants.KING
            : "Q".equals(pieceName) ? BoardConstants.QUEEN
            : "R".equals(pieceName) ? BoardConstants.ROOK
            : "B".equals(pieceName) ? BoardConstants.BISHOP : BoardConstants.KNIGHT;
        boolean capture = match.group(4) != null;
        String promotion = match.group(7);
        Integer promoted = promotion == null ? null : promotionPieceFromSuffix(promotion);
        RustBoardResult legal = RustBoardNative.legalMoves(variant, fen, NO_RUST_HISTORY);
        int candidate = -1, matches = 0;
        Set<String> seen = new HashSet<>();
        for (String coordinate : RustBoardResult.words(legal.moves)) {
            String pieceMove = pieceMoveOf(coordinate);
            if (pieceMove.length() < 4 || !seen.add(pieceMove)) continue;
            int from, to;
            try {
                from = Pos.fromString(pieceMove.substring(0, 2));
                to = Pos.fromString(pieceMove.substring(2, 4));
            } catch (Exception ex) {
                continue;
            }
            if (to != destination) continue;
            boolean isPromotion = pieceMove.length() > 4;
            if ((promoted != null) != isPromotion) continue;
            if (promoted != null && promotionPieceFromLetter(pieceMove.charAt(4)) != promoted) continue;
            char pieceChar = RustBoardNative.pieceAt(variant, fen, NO_RUST_HISTORY, pieceMove.substring(0, 2)).piece;
            if (!pieceMatches(pieceChar, piece)) continue;
            String san = RustBoardNative.san(variant, fen, NO_RUST_HISTORY, pieceMove).san;
            if (san.indexOf('x') >= 0 != capture) continue;
            if (match.group(2) != null && !match.group(2).equals(Pos.colToString(from))) continue;
            if (match.group(3) != null && !match.group(3).equals(Pos.rowToString(from))) continue;
            candidate = from;
            matches++;
        }
        return matches == 1 ? candidate : -1;
    }

    private synchronized boolean requestMove(Matcher match, String castle, String annotation) {
        String duck = match.group(castle == null ? 8 : 2);
        final int destination, duckSquare;
        try {
            destination = castle == null ? Pos.fromString(match.group(5) + match.group(6)) : -1;
            duckSquare = duck == null ? -1 : Pos.fromString(duck.substring(1));
        } catch (Exception ex) {
            return false;
        }
        String fen = jni.toFEN();
        boolean whiteToMove = fen.trim().split("\\s+")[1].equals("w");
        RustSearchRequest.Variant variant = rustVariant();
        int from, to;
        Integer promotionPiece = null;
        if (castle != null) {
            int[] files = RustSearchRequest.chess960Files(fen, whiteToMove);
            if (files[0] < 0) return false;
            from = Pos.fromColAndRow(files[0], whiteToMove ? 7 : 0);
            to = Pos.fromColAndRow("O-O".equals(castle) ? 6 : 2, whiteToMove ? 7 : 0);
        } else {
            from = resolveDisambiguatedFrom(match, destination, fen, variant);
            if (from < 0) return false;
            to = destination;
            String promotion = match.group(7);
            if (promotion != null) promotionPiece = promotionPieceFromSuffix(promotion);
        }
        if (promotionPiece != null) jni.setPromo(promotionPiece);
        // A SAN token is a complete half-move. Reject missing/invalid duck placements atomically below.
        if (jni.requestMove(from, to) == 0) return false;
        String san = appliedMoveSan(fen, variant);
        if (san == null) return false;
        if (duck != null && jni.requestDuckMove(duckSquare) == 0
            || duck == null && jni.getVariant() == BoardConstants.VARIANT_DUCK && jni.isEnded() == 0) {
            jni.undo();
            return false;
        }
        addPGNEntry(jni.getNumBoard(), san, annotation, jni.getMyMove(), duckSquare);
        return true;
    }

    /** Plays a document line from the current position; each variation is an alternative to the move it follows. */
    private void replayLine(List<PgnDocument.Move> line) {
        for (PgnDocument.Move move : line) {
            if (!applyPGNMove(move.san))
                throw new IllegalArgumentException("Illegal or unsupported move: " + move.san + " at character " + move.offset);
            Node played = currentNode;
            if (!move.leadingComment.isEmpty()) gameTree.setLeadingComment(played, move.leadingComment);
            if (!move.comment.isEmpty()) gameTree.setAnnotation(played, move.comment);
            for (int nag : move.nags) gameTree.addNag(played, nag);
            for (List<PgnDocument.Move> variation : move.variations) {
                if (!navigateTo(played.getParent()))
                    throw new IllegalArgumentException("Cannot enter variation at character " + move.offset);
                replayLine(variation);
            }
            if (!move.variations.isEmpty() && !navigateTo(played))
                throw new IllegalArgumentException("Cannot exit variation at character " + move.offset);
        }
    }

    /** Compatibility entry point for subclasses that have just executed exactly one native move. */
    public void addPGNEntry(int ply, String san, String annotation, int move, int duckMove) {
        if (pendingDuckMove != null || ply != currentNode.getPly() + 1 || ply != jni.getNumBoard())
            throw new IllegalStateException("Move must follow the current tree position");
        PGNEntry entry = new PGNEntry(san, annotation == null ? "" : annotation, move, duckMove);
        if (jni.getVariant() == BoardConstants.VARIANT_DUCK && duckMove == -1 && jni.isEnded() == 0)
            pendingDuckMove = entry;
        else recordEntry(entry);
    }

    private void recordEntry(PGNEntry entry) {
        Node old = currentNode;
        int previousChildren = old.getChildren().size();
        if (overwriteContinuation && !loadingPgn) {
            currentNode = gameTree.replaceContinuation(old, entry, jni.getState(), jni.getTurn());
            importedResult = null;
            treeChanged = true;
            return;
        }
        currentNode = gameTree.append(old, entry, jni.getState(), jni.getTurn(), !loadingPgn);
        if (old.getChildren().size() != previousChildren && gameTree.isMainLine(currentNode)) {
            if (!loadingPgn) importedResult = null;
        }
        treeChanged |= old.getChildren().size() != previousChildren;
    }

    public void setAnnotation(Node node, String annotation) {
        gameTree.setAnnotation(node, annotation);
        treeChanged = true;
        dispatchTreeChanged();
    }

    /** Compatibility annotation index is zero-based on the main line. */
    public void setAnnotation(int index, String annotation) {
        Node node = index < 0 ? null : gameTree.mainLineAt(index + 1);
        if (node != null) setAnnotation(node, annotation);
    }

    private String gameResult() {
        if (importedResult != null) return importedResult;
        Node end = gameTree.mainLineEnd();
        int state = end.getFinalState() == -1 ? end.getBoardState() : end.getFinalState();
        switch (state) {
            case BoardConstants.DRAW_50:
            case BoardConstants.DRAW_AGREEMENT:
            case BoardConstants.DRAW_MATERIAL:
            case BoardConstants.DRAW_REPEAT:
            case BoardConstants.STALEMATE: return "1/2-1/2";
            case BoardConstants.MATE: return end.getTurn() == BoardConstants.WHITE ? "0-1" : "1-0";
            case BoardConstants.BLACK_RESIGNED:
            case BoardConstants.BLACK_FORFEIT_TIME: return "1-0";
            case BoardConstants.WHITE_RESIGNED:
            case BoardConstants.WHITE_FORFEIT_TIME: return "0-1";
            default: return "*";
        }
    }

    public String exportFullPGN() {
        pgnTags.put("Result", gameResult());
        pgnTags.put("PlyCount", Integer.toString(getPGNSize()));
        return Pgn.writeTags(pgnTags) + '\n' + exportMovesPGN() + ' ' + gameResult() + '\n';
    }

    /** All lines, without a termination marker (exportFullPGN adds that marker). */
    public String exportMovesPGN() { return gameTree.exportMoves(); }

    /** Legacy practice fragment: main line only, renumbered from 1 as before. */
    public String exportMovesPGNFromPly(int ply) {
        StringBuilder out = new StringBuilder();
        List<Node> line = gameTree.mainLine();
        int start = Math.max(0, ply - 1);
        for (int i = start; i < line.size(); i++) {
            PGNEntry entry = line.get(i).getEntry();
            if ((i - start) % 2 == 0) out.append((i - start) / 2 + 1).append(". ");
            out.append(entry.sMove);
            if (entry.duckMove != -1) out.append('@').append(Pos.toString(entry.duckMove));
            out.append(' ');
            if (!entry.sAnnotation.isEmpty()) out.append('{').append(entry.sAnnotation.replace('}', ']')).append("} ");
        }
        return out.toString();
    }

    /** Detached copies: mutating the returned entries cannot corrupt recorded history. */
    public ArrayList<PGNEntry> getPGNEntries() {
        ArrayList<PGNEntry> entries = new ArrayList<>();
        for (Node node : gameTree.mainLine()) entries.add(node.getEntry());
        return entries;
    }

    public void setPGNTag(String sProp, String sValue) {
        pgnTags.put(sProp, sValue);
    }

    public void setDateLong(long lTime) {
        setPGNTag("Date", PgnDate.format(new Date(lTime)));
    }

    public String getPGNHeadProperty(String sProp) {
        return pgnTags.get(sProp);
    }

    public int getTurn() {
        return jni.getTurn();
    }
    public String getWhite() {
        return getPGNHeadProperty("White");
    }

    public String getBlack() {
        return getPGNHeadProperty("Black");
    }

    public Date getDate() {
        String s = getPGNHeadProperty("Date");
        return PgnDate.parse(s);
    }

    public boolean hasAnyPieceOnPosition(int pos) {
        return pos == jni.getDuckPos() ||
            jni.pieceAt(BoardConstants.WHITE, pos) != BoardConstants.FIELD ||
            jni.pieceAt(BoardConstants.BLACK, pos) != BoardConstants.FIELD;
    }

}
