package jwtc.android.chess.services;

import android.content.res.Resources;
import android.util.Log;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.regex.Matcher;

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
    protected JNI jni;
    public static final int MAX_PGN_SIZE = 500000;

    public final HashMap<String, String> pgnTags; //
    private GameTree gameTree;
    private Node currentNode;
    private PGNEntry pendingDuckMove;
    private String importedResult;
    private String lastPgnError;
    private boolean loadingPgn;
    private boolean treeChanged;

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
        public final int turn;
        public final int state;
        public final List<PositionMove> moves;

        private PositionSnapshot(String fen, int turn, int state, List<PositionMove> moves) {
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

    /** Main-thread only. Inspect a recorded position without dispatching navigation or changing the tree. */
    public PositionSnapshot getPositionSnapshot(Node node) {
        if (pendingDuckMove != null || !gameTree.contains(node)) return null;
        Node original = currentNode;
        try {
            if (!navigateTo(node)) return null;
            List<PositionMove> moves = new ArrayList<>();
            int count = jni.getMoveArraySize();
            jni.scratchSyncFromCurrent();
            for (int i = 0; i < count; i++) {
                int move = jni.getMoveArrayAt(i);
                if (jni.scratchMove(move) == 0) continue;
                try {
                    moves.add(new PositionMove(move, jni.scratchGetMyMoveToString()));
                } finally {
                    jni.scratchUndo();
                }
            }
            return new PositionSnapshot(jni.toFEN(), jni.getTurn(), jni.getState(), moves);
        } finally {
            if (!navigateTo(original)) restoreBoard(original, null);
            // JNI's move array is shared; restore it along with the board.
            jni.getMoveArraySize();
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
        jni = JNI.getInstance();
        jni.newGame();
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
        return getFinalState() != -1 || jni.isEnded() != 0;
    }

    public int getState() {
        int finalState = getFinalState();
        if (finalState != -1) {
            return finalState;
        }
        return jni.getState();
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

        if (jni.requestMove(from, to) == 0) {
            dispatchIllegalMove();
            return false;
        }

        final int move = jni.getMyMove();

        addPGNEntry(jni.getNumBoard(), jni.getMyMoveToString(), "", move, -1);

        dispatchMove(move);

        return true;
    }

    public boolean requestMoveCastle(int from, int to) {
        if (pendingDuckMove != null || isEnded()) {
            return false;
        }

        if (jni.doCastleMove(from, to) == 0) {
            dispatchIllegalMove();
            return false;
        }
        final int move = jni.getMyMove();

        addPGNEntry(jni.getNumBoard(), jni.getMyMoveToString(), "", move, -1);

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
        if (pendingDuckMove != null || jni.move(move) == 0) return false;
        if (duckMove != -1 && jni.requestDuckMove(duckMove) == 0) {
            jni.undo();
            return false;
        }
        addPGNEntry(jni.getNumBoard(), jni.getMyMoveToString(), "", jni.getMyMove(), duckMove);
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

    public String getFEN() {
        return jni.toFEN();
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

    private synchronized boolean requestMove(Matcher match, String castle, String annotation) {
        int candidate = 0, matches = 0;
        String duck = match.group(castle == null ? 8 : 2);
        final int destination, duckSquare;
        try {
            destination = castle == null ? Pos.fromString(match.group(5) + match.group(6)) : -1;
            duckSquare = duck == null ? -1 : Pos.fromString(duck.substring(1));
        } catch (Exception ex) {
            return false;
        }
        // A SAN token is a complete half-move. Reject missing/invalid duck placements atomically below.
        for (int i = 0; i < jni.getMoveArraySize(); i++) {
            int move = jni.getMoveArrayAt(i);
            if (castle != null) {
                if (!("O-O".equals(castle) && Move.isOO(move)
                    || "O-O-O".equals(castle) && Move.isOOO(move))) continue;
            } else {
                String pieceName = match.group(1);
                int piece = pieceName == null ? BoardConstants.PAWN
                    : "K".equals(pieceName) ? BoardConstants.KING
                    : "Q".equals(pieceName) ? BoardConstants.QUEEN
                    : "R".equals(pieceName) ? BoardConstants.ROOK
                    : "B".equals(pieceName) ? BoardConstants.BISHOP : BoardConstants.KNIGHT;
                int from = Move.getFrom(move);
                if (jni.pieceAt(jni.getTurn(), from) != piece
                    || Move.getTo(move) != destination) continue;
                if ((match.group(4) != null) != Move.isHIT(move)) continue;
                if (match.group(2) != null && !match.group(2).equals(Pos.colToString(from))) continue;
                if (match.group(3) != null && !match.group(3).equals(Pos.rowToString(from))) continue;
                String promotion = match.group(7);
                if ((promotion != null) != Move.isPromotionMove(move)) continue;
                if (promotion != null) {
                    int promoted = "=Q".equals(promotion) ? BoardConstants.QUEEN
                        : "=R".equals(promotion) ? BoardConstants.ROOK
                        : "=B".equals(promotion) ? BoardConstants.BISHOP : BoardConstants.KNIGHT;
                    if (Move.getPromotionPiece(move) != promoted) continue;
                }
            }
            candidate = move;
            matches++;
        }
        if (matches != 1 || jni.move(candidate) == 0) return false;
        if (duck != null && jni.requestDuckMove(duckSquare) == 0
            || duck == null && jni.getVariant() == BoardConstants.VARIANT_DUCK && jni.isEnded() == 0) {
            jni.undo();
            return false;
        }
        addPGNEntry(jni.getNumBoard(), jni.getMyMoveToString(), annotation, jni.getMyMove(), duckSquare);
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
