package jwtc.android.chess.services;

import android.content.res.Resources;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Collections;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jwtc.android.chess.R;
import jwtc.android.chess.helpers.PGNHelper;
import jwtc.chess.JNI;
import jwtc.chess.GameTree;
import jwtc.chess.GameTree.Node;
import jwtc.chess.PGNTokenizer;
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
    private static Pattern patMove;
    private static Pattern patCastling;
    private static Pattern patGameResult;
    private static Pattern patTag;

    public static final int MAX_PGN_SIZE = 500000;

    static {
        try {
            patMove = Pattern.compile("(K|Q|R|B|N)?(a|b|c|d|e|f|g|h)?(1|2|3|4|5|6|7|8)?(x)?(a|b|c|d|e|f|g|h)(1|2|3|4|5|6|7|8)(=Q|=R|=B|=N)?(@[a-h][1-8])?(\\+|#)?([\\?\\!]*)?[\\s]*");
            patCastling = Pattern.compile("(O\\-O\\-O|O\\-O)(@[a-h][1-8])?(\\+|#)?([\\?\\!]*)?");
            patGameResult = Pattern.compile("((\\*)|(1-0)|(0-1)|(1/2-1/2))");
            patTag = Pattern.compile(PGNHelper.regexPgnTag);

        } catch (Exception e) {
        }
    }

    public final HashMap<String, String> pgnTags; //
    private GameTree gameTree;
    private Node currentNode;
    private PGNEntry pendingDuckMove;
    private int finalState = -1;
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

    /** Call after the native board has been initialized, never merely to clear a move list. */
    protected final void resetPGNHistory() {
        resetPGNHistory(jni.toFEN());
    }

    private void resetPGNHistory(String initialFen) {
        gameTree = new GameTree(initialFen, jni.getState(), jni.getTurn());
        currentNode = gameTree.getRoot();
        pendingDuckMove = null;
        finalState = -1;
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
        return pendingDuckMove == null && currentNode == gameTree.mainLineEnd() ? finalState : -1;
    }

    public boolean setFinalState(int state) {
        if (pendingDuckMove != null || currentNode != gameTree.mainLineEnd()) return false;
        finalState = state;
        importedResult = null;
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
        finalState = -1;
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
        Date d = Calendar.getInstance().getTime();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd");

        pgnTags.clear();
        pgnTags.put("Event", "?");
        pgnTags.put("Site", "?");
        pgnTags.put("Round", "?");
        pgnTags.put("White", "?");
        pgnTags.put("Black", "?");
        pgnTags.put("Date", formatter.format(d));

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
        GameTree oldTree = gameTree;
        Node oldNode = currentNode;
        PGNEntry oldPending = pendingDuckMove;
        int oldFinalState = finalState;
        String oldResult = importedResult;
        boolean oldChanged = treeChanged;
        HashMap<String, String> oldTags = new HashMap<>(pgnTags);
        loadingPgn = true;
        try {
            pgnTags.clear();
            PGNTokenizer lexer = new PGNTokenizer(s);
            PGNTokenizer.Token token = lexer.next();
            while (token.kind == PGNTokenizer.Kind.TAG) {
                Matcher tag = Pattern.compile("\\[([A-Za-z0-9_]+)\\s+\"((?:\\\\.|[^\"\\\\])*)\"\\s*\\]").matcher(token.text);
                if (!tag.matches()) throw PGNTokenizer.error("Invalid tag", token.offset);
                pgnTags.put(tag.group(1), tag.group(2).replace("\\\"", "\"").replace("\\\\", "\\"));
                token = lexer.next();
            }
            if (pgnTags.containsKey("FEN")) {
                if (!jni.initFEN(pgnTags.get("FEN"))) throw new IllegalArgumentException("Invalid FEN");
            } else {
                jni.newGame("Duck".equalsIgnoreCase(pgnTags.get("Variant"))
                    ? BoardConstants.VARIANT_DUCK : BoardConstants.VARIANT_DEFAULT);
            }
            resetPGNHistory(pgnTags.getOrDefault("FEN", jni.toFEN()));
            parseMovetext(lexer, token);
            if (!navigateTo(gameTree.mainLineEnd())) throw new IllegalArgumentException("Cannot restore main line");
            String headerResult = pgnTags.get("Result");
            if (headerResult != null && !patGameResult.matcher(headerResult).matches())
                throw new IllegalArgumentException("Invalid Result tag");
            if (importedResult != null && headerResult != null && !importedResult.equals(headerResult))
                throw new IllegalArgumentException("Result tag and movetext disagree");
            if (importedResult == null) importedResult = headerResult;
            if (jni.isEnded() == 0) {
                if ("1-0".equals(importedResult)) finalState = BoardConstants.BLACK_RESIGNED;
                else if ("0-1".equals(importedResult)) finalState = BoardConstants.WHITE_RESIGNED;
                else if ("1/2-1/2".equals(importedResult)) finalState = BoardConstants.DRAW_AGREEMENT;
            }
        } catch (IllegalArgumentException ex) {
            lastPgnError = ex.getMessage();
            gameTree = oldTree;
            finalState = oldFinalState;
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

    public static Matcher getMoveMatcher(String sMove) {
        return patMove.matcher(sMove);
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
        match = patCastling.matcher(sMove.trim().replace('0', 'O'));
        return match.matches() && requestMove(match, match.group(1), "");
    }

    public void resetForfeitTime() {
        if (finalState == BoardConstants.WHITE_FORFEIT_TIME || finalState == BoardConstants.BLACK_FORFEIT_TIME
            || finalState == BoardConstants.WHITE_RESIGNED || finalState == BoardConstants.BLACK_RESIGNED) {
            finalState = -1;
            importedResult = null;
            dispatchGameResumed();
            dispatchState();
        }
    }

    public static String moveToSpeechString(Resources resources, String sMove, int move, boolean useLongMove) {
        if (resources == null) {
            resources = Resources.getSystem();
        }
        StringBuilder sMoveSpeech = new StringBuilder();

        // check regular move
        Matcher matchToken = getMoveMatcher(sMove);
        if (matchToken.matches()) {
            // 1            2                 3                 4   5                6                7             8             9       10
            // (K|Q|R|B|N)?(a|b|c|d|e|f|g|h)?(1|2|3|4|5|6|7|8)?(x)?(a|b|c|d|e|f|g|h)(1|2|3|4|5|6|7|8)(=Q|=R|=B|=N)?(@[a-h][1-8])?(\\+|#)?([\\?\\!]*)?[\\s]*")
            String piece = matchToken.group(1);
            if (piece != null) {
                piece = getPieceName(resources, piece);
            } else {
                piece = getString(resources, R.string.piece_pawn, "Pawn");
            }
            sMoveSpeech.append(piece).append(" ");
            String sFile = matchToken.group(2);
            String sRow = matchToken.group(3);

            if (useLongMove) {
                sMoveSpeech.append(Pos.toString(Move.getFrom(move)).toUpperCase()).append(" ");
            } else {

                if (sFile != null) {
                    sMoveSpeech.append(sFile.toUpperCase(Locale.ROOT)).append(" ");
                }

                if (sRow != null) {
                    sMoveSpeech.append(sRow).append(" ");
                }
            }

            if (matchToken.group(4) != null) {
                sMoveSpeech.append(getString(resources, R.string.tts_move_takes, "takes")).append(" ");
            }
            sFile = matchToken.group(5);
            sRow = matchToken.group(6);
            if (sFile != null && sRow != null) {
                sMoveSpeech.append(getString(
                    resources,
                    R.string.tts_move_square,
                    "%1$s%2$s",
                    sFile.toUpperCase(Locale.ROOT),
                    sRow
                )).append(" ");
            }

            if (Move.isEP(move)) {
                sMoveSpeech.append(getString(resources, R.string.tts_move_en_passant, "en passant")).append(" ");
            }

            String sPromote = matchToken.group(7);
            if (sPromote != null && sPromote.length() > 1) {
                sMoveSpeech.append(getString(
                    resources,
                    R.string.tts_move_promotes_to,
                    "promotes to %1$s",
                    getPieceName(resources, sPromote.substring(1))
                )).append(" ");
            }
            // ignore Duck for now

            String sSpecial = matchToken.group(9);
            if (sSpecial != null) {
                if (sSpecial.equals("+")) {
                    sMoveSpeech.append(getString(resources, R.string.state_check, "check")).append(" ");
                } else if (sSpecial.equals("#")) {
                    sMoveSpeech.append(getString(resources, R.string.tts_move_checkmate, "checkmate")).append(" ");
                }
            }
        } else if (sMove.contains("O-O-O")) {
            sMoveSpeech.append(getString(resources, R.string.tts_move_castle_long, "castle long")).append(" ");
            if (sMove.contains("+")) {
                sMoveSpeech.append(getString(resources, R.string.state_check, "check")).append(" ");
            }
            if (sMove.contains("#")) {
                sMoveSpeech.append(getString(resources, R.string.tts_move_checkmate, "checkmate")).append(" ");
            }
        } else if (sMove.contains("O-O")) {
            sMoveSpeech.append(getString(resources, R.string.tts_move_castle_short, "castle short")).append(" ");
            if (sMove.contains("+")) {
                sMoveSpeech.append(getString(resources, R.string.state_check, "check")).append(" ");
            }
            if (sMove.contains("#")) {
                sMoveSpeech.append(getString(resources, R.string.tts_move_checkmate, "checkmate")).append(" ");
            }
        } else {
            Log.d(TAG, "Did not parse move " + sMove);
        }

        String spoken = sMoveSpeech.toString().trim();
        Log.d(TAG, "TTS " + sMove + " => " + spoken);

        return spoken;
    }

    protected static String getPieceName(Resources resources, String piece) {
        switch (piece) {
            case "K":
                return getString(resources, R.string.piece_king, "King");
            case "Q":
                return getString(resources, R.string.piece_queen, "Queen");
            case "R":
                return getString(resources, R.string.piece_rook, "Rook");
            case "B":
                return getString(resources, R.string.piece_bishop, "Bishop");
            case "N":
                return getString(resources, R.string.piece_knight, "Knight");
            default:
                return "";
        }
    }

    private static String getString(Resources resources, int resourceId, String fallback) {
        if (resources != null) {
            try {
                return resources.getString(resourceId);
            } catch (Resources.NotFoundException ignored) {
            }
        }
        return fallback;
    }

    private static String getString(Resources resources, int resourceId, String fallback, Object... args) {
        if (resources != null) {
            try {
                return resources.getString(resourceId, args);
            } catch (Resources.NotFoundException ignored) {
            }
        }
        return String.format(Locale.getDefault(), fallback, args);
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

    public static void loadPGNHead(String s, HashMap<String, String> tagsMap) {
        s = PGNHelper.cleanPgnString(s);
        Matcher matcher = patTag.matcher(s);

        tagsMap.clear();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = matcher.group(2);
            if (name != null && value != null) {
                tagsMap.put(name, value);
            }
        }
    }

    private static final class VariationFrame {
        final Node resume;
        final Node branchPoint;
        boolean hasMove;
        String leadingComment = "";
        VariationFrame(Node resume) { this.resume = resume; this.branchPoint = resume.getParent(); }
    }

    private void parseMovetext(PGNTokenizer lexer, PGNTokenizer.Token first) {
        Deque<VariationFrame> stack = new ArrayDeque<>();
        boolean ended = false;
        for (PGNTokenizer.Token token = first; token.kind != PGNTokenizer.Kind.END; token = lexer.next()) {
            if (ended && token.kind != PGNTokenizer.Kind.COMMENT)
                throw PGNTokenizer.error("Unexpected token after result", token.offset);
            switch (token.kind) {
                case NUMBER: break;
                case COMMENT:
                    if (!stack.isEmpty() && !stack.peek().hasMove) {
                        VariationFrame frame = stack.peek();
                        frame.leadingComment = joinComments(frame.leadingComment, token.text);
                    } else if (currentNode == gameTree.getRoot()) {
                        gameTree.setRootComment(joinComments(gameTree.getRootComment(), token.text));
                    } else {
                        gameTree.setAnnotation(currentNode, joinComments(currentNode.getEntry().sAnnotation, token.text));
                    }
                    break;
                case OPEN:
                    if (stack.size() >= 128 || currentNode.getParent() == null
                        || !stack.isEmpty() && !stack.peek().hasMove)
                        throw PGNTokenizer.error("Variation must follow a move (maximum nesting 128)", token.offset);
                    VariationFrame frame = new VariationFrame(currentNode);
                    stack.push(frame);
                    if (!navigateTo(frame.branchPoint)) throw PGNTokenizer.error("Cannot enter variation", token.offset);
                    break;
                case CLOSE:
                    if (stack.isEmpty() || !stack.peek().hasMove)
                        throw PGNTokenizer.error("Unmatched or empty variation", token.offset);
                    if (!navigateTo(stack.pop().resume)) throw PGNTokenizer.error("Cannot exit variation", token.offset);
                    break;
                case NAG:
                    if (!stack.isEmpty() && !stack.peek().hasMove)
                        throw PGNTokenizer.error("NAG must follow a move", token.offset);
                    try { gameTree.addNag(currentNode, Integer.parseInt(token.text)); }
                    catch (IllegalArgumentException ex) { throw PGNTokenizer.error("Invalid NAG", token.offset); }
                    break;
                case SYMBOL:
                    if (patGameResult.matcher(token.text).matches()) {
                        if (!stack.isEmpty()) throw PGNTokenizer.error("Result inside variation", token.offset);
                        importedResult = token.text;
                        ended = true;
                        break;
                    }
                    String san = token.text.replaceAll("[!?]+$", "");
                    String glyph = token.text.substring(san.length());
                    String[] glyphs = {"", "!", "?", "!!", "??", "!?", "?!"};
                    int nag = java.util.Arrays.asList(glyphs).indexOf(glyph);
                    if (nag < 0 || !applyPGNMove(san))
                        throw PGNTokenizer.error("Illegal or unsupported move: " + token.text, token.offset);
                    if (nag > 0) gameTree.addNag(currentNode, nag);
                    if (!stack.isEmpty() && !stack.peek().hasMove) {
                        stack.peek().hasMove = true;
                        gameTree.setLeadingComment(currentNode, stack.peek().leadingComment);
                    }
                    break;
                default: throw PGNTokenizer.error("Unexpected token: " + token.text, token.offset);
            }
        }
        if (!stack.isEmpty()) throw new IllegalArgumentException("Unclosed variation at end of PGN");
    }

    private static String joinComments(String first, String second) {
        return first.isEmpty() ? second : first + "\n" + second;
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
            finalState = -1;
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
        int state = finalState == -1 ? end.getBoardState() : finalState;
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
        String[] roster = {"Event", "Site", "Date", "Round", "White", "Black", "Result"};
        StringBuilder out = new StringBuilder();
        for (String key : roster) appendTag(out, key, pgnTags.getOrDefault(key, "?"));
        ArrayList<String> keys = new ArrayList<>(pgnTags.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            if (!java.util.Arrays.asList(roster).contains(key)) appendTag(out, key, pgnTags.get(key));
        }
        return out.append('\n').append(exportMovesPGN()).append(' ').append(gameResult()).append('\n').toString();
    }

    private static void appendTag(StringBuilder out, String name, String value) {
        if (value != null) out.append('[').append(name).append(" \"")
            .append(value.replace("\\", "\\\\").replace("\"", "\\\"").replace('\n', ' ').replace('\r', ' '))
            .append("\"]\n");
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
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(lTime);
        Date d = cal.getTime();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd");
        setPGNTag("Date", formatter.format(d));
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
        return PGNHelper.getDate(s);
    }

    public boolean hasAnyPieceOnPosition(int pos) {
        return pos == jni.getDuckPos() ||
            jni.pieceAt(BoardConstants.WHITE, pos) != BoardConstants.FIELD ||
            jni.pieceAt(BoardConstants.BLACK, pos) != BoardConstants.FIELD;
    }

}
