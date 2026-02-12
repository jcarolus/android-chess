package jwtc.android.chess.services;

import android.content.res.Resources;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jwtc.android.chess.helpers.PGNHelper;
import jwtc.chess.JNI;
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
    private static Pattern patMoveNum;
    private static Pattern patMoveDots;
    private static Pattern patAnnotation;
    private static Pattern patMove;
    private static Pattern patCastling;
    private static Pattern patGameResult;
    private static Pattern patTag;

    public static final int MAX_PGN_SIZE = 500000;

    static {
        try {
            patMoveNum = Pattern.compile("(\\d+)\\.");
            patAnnotation = Pattern.compile("\\{([^\\{]*)\\}");
            patMove = Pattern.compile("(K|Q|R|B|N)?(a|b|c|d|e|f|g|h)?(1|2|3|4|5|6|7|8)?(x)?(a|b|c|d|e|f|g|h)(1|2|3|4|5|6|7|8)(=Q|=R|=B|=N)?(@[a-h][1-8])?(\\+|#)?([\\?\\!]*)?[\\s]*");
            patCastling = Pattern.compile("(O\\-O|O\\-O\\-O)(@[a-h][1-8])?(\\+|#)?([\\?\\!]*)?");
            patGameResult = Pattern.compile("((\\*)|(1-0)|(0-1)|(1/2-1/2))");
            patTag = Pattern.compile(PGNHelper.regexPgnTag);
            patMoveDots = Pattern.compile("\\.\\.");

        } catch (Exception e) {
        }
    }

    public final HashMap<String, String> pgnTags; //
    protected final ArrayList<PGNEntry> pgnMoves;

    public GameApi() {
        jni = JNI.getInstance();
        jni.reset();
        pgnTags = new HashMap<String, String>();
        pgnMoves = new ArrayList<PGNEntry>();
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
        int size = pgnMoves.size();
        if (size > 0 && jni.getNumBoard() == size) {
            return pgnMoves.get(size - 1).finalState;
        }
        return -1;
    }

    public boolean setFinalState(int state) {
        int size = pgnMoves.size();
        if (size > 0 && jni.getNumBoard() == size) {
            pgnMoves.get(size - 1).finalState = state;
            dispatchState();
            return true;
        }
        return false;
    }

    public boolean requestMove(int from, int to) {
        Log.i(TAG, "requestMove");
        if (isEnded())
            return false;

        if (jni.requestMove(from, to) == 0) {
            return false;
        }

        final int move = jni.getMyMove();

        addPGNEntry(jni.getNumBoard(), jni.getMyMoveToString(), "", move, -1);

        dispatchMove(move);

        return true;
    }

    public boolean requestMoveCastle(int from, int to) {
        if (isEnded()) {
            return false;
        }

        if (jni.doCastleMove(from, to) == 0) {
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
        if (move(move, "", true)) {
            Log.d(TAG, "Performed move " + Move.toDbgString(move));
            if (duckMove != -1) {
                if (this.requestDuckMove(duckMove)) {
                    Log.d(TAG, "Performed duck move " + Pos.toString(duckMove));
                } else {
                    Log.d(TAG, "Not duck moved " + Pos.toString(duckMove));
                }
            }
            dispatchMove(move);
        } else {
            Log.d(TAG, "Not moved " + Move.toDbgString(move));
        }
    }

    public void undoMove() {
//        Log.d(TAG, "undoMove");
        jni.undo();
        dispatchState();
    }

    public void nextMove() {
        jumpToBoardNum(jni.getNumBoard() + 1);
    }


    public void jumpToBoardNum(int toNumBoard) {
        Log.d(TAG, "jumptoMove " + toNumBoard + ", " + pgnMoves.size());

        if (toNumBoard <= pgnMoves.size() && toNumBoard >= 0) {
            int currentNumBoard = jni.getNumBoard();
            if (toNumBoard > currentNumBoard) {
                while (toNumBoard > currentNumBoard) {
                    int res = jni.move(pgnMoves.get(currentNumBoard).move);
                    Log.d(TAG, "jni.move " + res);
                    Log.d(TAG, "duck at " + pgnMoves.get(currentNumBoard).duckMove);
                    if (pgnMoves.get(currentNumBoard).duckMove != -1) {
                        jni.requestDuckMove(pgnMoves.get(currentNumBoard).duckMove);
                    }
                    currentNumBoard++;
                }
            } else {
                while (toNumBoard < currentNumBoard) {
                    jni.undo();
                    currentNumBoard--;
                }
            }
            dispatchState();
        }
    }

    public int getPGNSize() {
        return pgnMoves.size();
    }

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
        pgnTags.put("White", Resources.getSystem().getString(android.R.string.unknownName));
        pgnTags.put("Black", Resources.getSystem().getString(android.R.string.unknownName));
        pgnTags.put("Date", formatter.format(d));

        pgnMoves.clear();

        jni.newGame(variant);

        if (variant == BoardConstants.VARIANT_DUCK) {
            pgnTags.put("Setup", "1");
            pgnTags.put("FEN", jni.toFEN());
        }

        dispatchState();
    }


    public boolean initFEN(String sFEN, boolean resetHead) {

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

            pgnMoves.clear();

            dispatchState();
            return true;
        }
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

        pgnMoves.clear();

        dispatchState();
        return ret;
    }

    public boolean loadPGN(String s) {
        jni.newGame();

        if (s.length() > MAX_PGN_SIZE) {
            Log.d(TAG, "PGN larger than max " + s.length());
            return false;
        }

        int finalState = -1;
        loadPGNHead(s, pgnTags);

        if (pgnTags.containsKey("FEN")) {
            String sFEN = pgnTags.get("FEN");
            if (sFEN != null) {
                initFEN(sFEN, false);
            }
        }

        if (loadPGNMoves(s)) {
            if (pgnTags.containsKey("Result")) {
                String value = pgnTags.get("Result");
                if (value != null && jni.isEnded() == 0) {
                    Matcher match = patGameResult.matcher(value);
                    if (match.matches()) {
                        String result = match.group(1);
                        if (result != null) {
                            if (result.equals("1/2-1/2")) {
                                finalState = BoardConstants.DRAW_AGREEMENT;
                            } else if (result.equals("1-0")) {
                                // @TODO once we can sum the move durations, we can also forfeit on time.
                                finalState = BoardConstants.BLACK_RESIGNED;
                            } else if (result.equals("0-1")) {
                                finalState = BoardConstants.WHITE_RESIGNED;
                            }
                            Log.d(TAG, "Overriding game state " + result + " => " + finalState);
                        }
                    }
                }
            }

            int size = pgnMoves.size();
            if (size > 0) {
                pgnMoves.get(size - 1).finalState = finalState;
            }
            dispatchState();
            return true;
        }
        return false;
    }

    public static Matcher getMoveMatcher(String sMove) {
        return patMove.matcher(sMove);
    }

    public boolean requestMove(String sMove) {
        Matcher matchToken = getMoveMatcher(sMove);
        String sAnnotation = "";
        if (matchToken.matches()) {
            Log.d(TAG, "requestMove MATCHES " + sMove);
            if (requestMove(matchToken, null, sAnnotation)) {
                final int move = jni.getMyMove();
                dispatchMove(move);
                return true;
            }
        } else {
            matchToken = patCastling.matcher(sMove);
            if (matchToken.matches()) {
                if (requestMove(matchToken, matchToken.group(1), sAnnotation)) {
                    final int move = jni.getMyMove();
                    dispatchMove(move);
                    return true;
                }
            }
        }
        Log.d(TAG, "requestMove " + sMove);
        return false;
    }

    public void resetForfeitTime() {
        int size = pgnMoves.size();
        if (size > 0) {
            int finalState = pgnMoves.get(size - 1).finalState;
            // any forfeit or resigns can be reset here
            if (finalState == BoardConstants.WHITE_FORFEIT_TIME ||
                finalState == BoardConstants.BLACK_FORFEIT_TIME ||
                finalState == BoardConstants.WHITE_RESIGNED ||
                finalState == BoardConstants.BLACK_RESIGNED) {
                pgnMoves.get(size - 1).finalState = -1;
                dispatchState();
            }
        }
    }

    public static String moveToSpeechString(String sMove, int move) {
        String sMoveSpeech = "";

        // check regular move
        Matcher matchToken = getMoveMatcher(sMove);
        if (matchToken.matches()) {
            // 1            2                 3                 4   5                6                7             8             9       10
            // (K|Q|R|B|N)?(a|b|c|d|e|f|g|h)?(1|2|3|4|5|6|7|8)?(x)?(a|b|c|d|e|f|g|h)(1|2|3|4|5|6|7|8)(=Q|=R|=B|=N)?(@[a-h][1-8])?(\\+|#)?([\\?\\!]*)?[\\s]*")
            String piece = matchToken.group(1);
            if (piece != null) {
                piece = getPieceName(piece);
            } else {
                piece = "Pawn ";
            }
            sMoveSpeech += piece;
            String sFile = matchToken.group(2);
            if (sFile != null) {
                sMoveSpeech += sFile.toUpperCase() + " ";
            }
            String sRow = matchToken.group(3);
            if (sRow != null) {
                sMoveSpeech += sRow + " ";
            }
            if (matchToken.group(4) != null) {
                sMoveSpeech += "takes ";
            }
            sFile = matchToken.group(5);
            sRow = matchToken.group(6);
            if (sFile != null && sRow != null) {
                sMoveSpeech += sFile.toUpperCase() + sRow + " ";
            }
            if (Move.isEP(move)) {
                sMoveSpeech += "(en Passant) ";
            }

            String sPromote = matchToken.group(7);
            if (sPromote != null && sPromote.length() > 1) {
                sMoveSpeech += "promotes to " + getPieceName(sPromote.substring(1));
            }
            // ignore Duck for now

            String sSpecial = matchToken.group(9);
            if (sSpecial != null) {
                if (sSpecial.equals("+")) {
                    sMoveSpeech += "check ";
                } else if (sSpecial.equals("#")) {
                    sMoveSpeech += "checkmate ";
                }
            }
        } else if (sMove.contains("O-O-O")) {
            sMoveSpeech += "Castle long ";
            if (sMove.contains("+")) {
                sMoveSpeech += "check ";
            }
            if (sMove.contains("#")) {
                sMoveSpeech += "checkmate ";
            }
        } else if (sMove.contains("O-O")) {
            sMoveSpeech += "Castle short ";
            if (sMove.contains("+")) {
                sMoveSpeech += "check ";
            }
            if (sMove.contains("#")) {
                sMoveSpeech += "checkmate ";
            }
        } else {
            Log.d(TAG, "Did not parse move " + sMove);
        }

        Log.d(TAG, "TTS " + sMove + " => " + sMoveSpeech);

        return sMoveSpeech;
    }

    protected static String getPieceName(String piece) {
        switch (piece) {
            case "K":
                return "King ";
            case "Q":
                return "Queen ";
            case "R":
                return "Rook ";
            case "B":
                return "Bishop ";
            case "N":
                return "Knight ";
            default:
                return "";
        }
    }

    protected void dispatchMove(final int move) {
//        Log.d(TAG, "dispatchMove " + move);

        for (GameListener listener : listeners) {
            listener.OnMove(move);
        }
    }

    protected void dispatchDuckMove(final int duckMove) {
        Log.d(TAG, "dispatchDuckMove " + duckMove);

        for (GameListener listener : listeners) {
            listener.OnDuckMove(duckMove);
        }
    }

    protected void dispatchState() {
//        Log.d(TAG, "dispatchState");

        for (GameListener listener : listeners) {
            listener.OnState();
        }
    }

    private boolean move(int move, String sAnnotation, boolean bUpdate) {
//        Log.i(TAG, "move " + move);
        if (jni.move(move) == 0) {
            return false;
        }
        addPGNEntry(jni.getNumBoard(), jni.getMyMoveToString(), sAnnotation, jni.getMyMove(), -1);

        return true;
    }

    private boolean moveDuck(int duckMove) {
        if (jni.requestDuckMove(duckMove) == 0) {
            return false;
        }

        int index = jni.getNumBoard() - 1;
        if (index >= 0 && index < pgnMoves.size()) {
            Log.d(TAG, " set duckmove " + index + " " + Pos.toString(duckMove));
            pgnMoves.get(index).duckMove = duckMove;
        }
        return true;
    }

    private synchronized final boolean requestMove(final Matcher matchToken, final String sCastle, final String sAnnotation) {

        boolean bMatch = false;
        int size = jni.getMoveArraySize();
        int move, duckMove;

        if (sCastle != null) {
            for (int i = 0; i < size; i++) {
                bMatch = false;
                move = jni.getMoveArrayAt(i);
                if (Move.isOO(move) && sCastle.equals("O-O")) {
                    bMatch = true;
                }
                if (Move.isOOO(move) && sCastle.equals("O-O-O")) {
                    bMatch = true;
                }
                if (bMatch) {
                    if (move(move, "", false)) {
                        int numBoard = jni.getNumBoard() - 2;
                        if (numBoard >= 0 && sAnnotation != null) {
                            setAnnotation(numBoard, sAnnotation);
                        }

                        String sDuck = matchToken.group(2);
                        if (sDuck != null) {
                            sDuck = sDuck.substring(1);
                            try {
                                duckMove = Pos.fromString(sDuck);
                                if (!moveDuck(duckMove)) {
                                    return false;
                                }
                            } catch (Exception e) {
                                return false;
                            }
                        }

                        return true;
                    } else {
                        return false;
                    }
                }
            }
        } else {
            String sPiece = matchToken.group(1);
            String sDistFile = matchToken.group(2);
            String sDistRank = matchToken.group(3);
            String sTakes = matchToken.group(4);
            String sFile = matchToken.group(5);
            String sRank = matchToken.group(6);
            String sPromote = matchToken.group(7);
            String sDuck = matchToken.group(8);

            if (sFile == null) {
                return false;
            }
            if (sRank == null) {
                return false;
            }
            int movePiece = BoardConstants.PAWN;
            if (sPiece != null) {
                if (sPiece.equals("K"))
                    movePiece = BoardConstants.KING;
                else if (sPiece.equals("Q"))
                    movePiece = BoardConstants.QUEEN;
                else if (sPiece.equals("R"))
                    movePiece = BoardConstants.ROOK;
                else if (sPiece.equals("B"))
                    movePiece = BoardConstants.BISHOP;
                else if (sPiece.equals("N"))
                    movePiece = BoardConstants.KNIGHT;
                else {

                    return false;
                }
            }

            int moveTo, from, to, piece, t = jni.getTurn();
            try {
                moveTo = Pos.fromString(sFile + sRank);
            } catch (Exception ex) {
                return false;
            }

            for (int i = 0; i < size; i++) {
                bMatch = false;
                move = jni.getMoveArrayAt(i);
                from = Move.getFrom(move);
                to = Move.getTo(move);

                if (sPromote != null) {
                    piece = Move.getPromotionPiece(move);
                    if (false == (sPromote.equals("=Q") && piece == BoardConstants.QUEEN ||
                        sPromote.equals("=R") && piece == BoardConstants.ROOK ||
                        sPromote.equals("=B") && piece == BoardConstants.BISHOP ||
                        sPromote.equals("=N") && piece == BoardConstants.KNIGHT))
                        continue;
                }

                piece = jni.pieceAt(t, from);

                //if(Move.toDbgString(move).equals("[h5-f5]"))
                //	System.out.println("#");


                if (piece == movePiece && to == moveTo) {
                    if (sDistFile != null) {
                        //System.out.println("#" + sDistFile + " - " + Move.toDbgString(move));
                        if (Pos.colToString(from).equals(sDistFile))
                            bMatch = true;
                    } else if (sDistRank != null) {
                        if (Pos.rowToString(from).equals(sDistRank))
                            bMatch = true;
                    } else {
                        bMatch = true;
                    }
                }
                if (bMatch) {

                    if (move(move, "", false)) {
                        int numBoard = jni.getNumBoard() - 2;
                        if (numBoard >= 0 && sAnnotation != null) {
                            setAnnotation(numBoard, sAnnotation);
                        }

                        if (sDuck != null) {
                            sDuck = sDuck.substring(1);
                            try {
                                duckMove = Pos.fromString(sDuck);
                                if (!moveDuck(duckMove)) {
                                    return false;
                                }
                            } catch (Exception e) {
                                return false;
                            }
                        }

                        return true;
                    } else {
                        return false;
                    }
                }

            }
        }
        return false;
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

    private boolean removeComment(StringBuffer s) throws Exception {
        int iOpen = s.indexOf("("), iClose = s.indexOf(")"), iNextOpen;
        if (iOpen >= 0 && iClose >= 0) {

            iNextOpen = s.indexOf("(", iOpen + 1);
            while (iNextOpen >= 0 && iNextOpen < iClose) {
                iOpen = iNextOpen;
                iNextOpen = s.indexOf("(", iNextOpen + 1);
            }
            if (iOpen > iClose) {
                throw new Exception("Open bracket after closing bracket: " + iOpen + ", " + iClose);
            }

            s.delete(iOpen, iClose + 1);
            return true;
        }
        if (iOpen >= 0) {
            throw new Exception("No closing bracket for comment");
        }
        if (iClose >= 0) {
            throw new Exception("No opening bracket for comment");
        }
        return false;
    }

    private boolean loadPGNMoves(String s) {
        pgnMoves.clear();

        s = s.replaceAll(PGNHelper.regexPgnTag, "");
        s = PGNHelper.cleanPgnString(s);

        // Log.d(TAG, "loadPgnMoves " + s);

        int cursor = 0;
        Matcher matchMoveNumber = patMoveNum.matcher(s);
        Matcher matchAnnotation = patAnnotation.matcher(s);
        Matcher matchMove = patMove.matcher(s);
        Matcher matchCastling = patCastling.matcher(s);
        Matcher matchMoveDots = patMoveDots.matcher(s);

        NextMatch nextMoveNumber = new NextMatch(matchMoveNumber);
        NextMatch nextAnnotation = new NextMatch(matchAnnotation);
        NextMatch nextMove = new NextMatch(matchMove);
        NextMatch nextCastling = new NextMatch(matchCastling);
        NextMatch nextMoveDots = new NextMatch(matchMoveDots);

        NextMatch best;
        do {
            best = null;
            nextMoveNumber.seek(cursor);
            nextAnnotation.seek(cursor);
            nextMove.seek(cursor);
            nextCastling.seek(cursor);
            nextMoveDots.seek(cursor);

            if (nextMoveNumber.has) {
                best = nextMoveNumber;
            }
            if (nextAnnotation.has && (best == null || nextAnnotation.start < best.start)) {
                best = nextAnnotation;
            }
            if (nextMove.has && (best == null || nextMove.start < best.start)) {
                best = nextMove;
            }
            if (nextCastling.has && (best == null || nextCastling.start < best.start)) {
                best = nextCastling;
            }
            if (nextMoveDots.has && (best == null || nextMoveDots.start < best.start)) {
                best = nextMoveDots;
            }

            if (best != null) {
                if (best == nextAnnotation) {
                    String sAnnotation = best.matcher.group(1);
                    if (sAnnotation != null && !sAnnotation.isEmpty()) {
                        setAnnotation(jni.getNumBoard() - 1, sAnnotation);
                    }
                } else if (best == nextMove) {
                    requestMove(best.matcher, null, null);
                } else if (best == nextCastling) {
                    requestMove(best.matcher, best.matcher.group(1), null);
                }
                cursor = best.end;
            }

        } while (best != null);

        return true;
    }


    public void addPGNEntry(int ply, String sMove, String sAnnotation, int move, int duckMove) {
        // Log.d(TAG, "addPGNEntry " + ply + ": " + sMove + " @ " + Pos.toString(duckMove) + " = " + duckMove);
        while (ply >= 0 && pgnMoves.size() >= ply) {
            pgnMoves.remove(pgnMoves.size() - 1);
        }
        pgnMoves.add(new PGNEntry(sMove, sAnnotation, move, duckMove));
    }

    public void setAnnotation(int i, String sAnno) {
        if (pgnMoves.size() > i)
            // Log.d(TAG, "set annotation " + sAnno);
            pgnMoves.get(i).sAnnotation = sAnno;
    }

    public String exportFullPGN() {

        String result = "*";
        int turn = jni.getTurn();
        int state = getState();
        switch (state) {
            case BoardConstants.DRAW_50:
            case BoardConstants.DRAW_AGREEMENT:
            case BoardConstants.DRAW_MATERIAL:
            case BoardConstants.DRAW_REPEAT:
                result = "1/2-1/2";
                break;
            case BoardConstants.MATE:
                result = turn == BoardConstants.WHITE ? "0-1" : "1-0";
                break;
            case BoardConstants.BLACK_RESIGNED:
            case BoardConstants.BLACK_FORFEIT_TIME:
                result = "1-0";
                break;
            case BoardConstants.WHITE_RESIGNED:
            case BoardConstants.WHITE_FORFEIT_TIME:
                result = "0-1";
                break;
            case BoardConstants.PLAY:
                result = "*";
                break;
        }
        pgnTags.put("Result", result);
        pgnTags.put("PlyCount", Integer.toString(pgnMoves.size()));

        // to make sure the order of the `Seven Tag Roster`
        String[] arrHead = {
            "Event", "Site", "Date", "Round", "White", "Black", "Result",
            // and the others we support
            "EventDate", "Variant", "Setup", "FEN", "PlyCount", "TimeControl", "Time"};

        String s = "", key;
        for (int i = 0; i < arrHead.length; i++) {
            key = arrHead[i];
            if (pgnTags.containsKey(key)) {
                String value = pgnTags.get(key).replace("\"", "\\\"");
                s += "[" + key + " \"" + value + "\"]\n";
            }
        }

        s += exportMovesPGN();
        s += "\n";

        Log.d(TAG, "exportFullPGN " + s);

        return s;
    }

    public String exportMovesPGN() {
        return exportMovesPGNFromPly(1);
    }

    public String exportMovesPGNFromPly(int iPly) {
        String s = "";
        if (iPly > 0) {
            iPly--;
        }
        if (iPly < 0) {
            iPly = 0;
        }

        for (int i = iPly; i < pgnMoves.size(); i++) {
            if ((i - iPly) % 2 == 0) {
                s += ((i - iPly) / 2 + 1) + ". ";
            }
            s += pgnMoves.get(i).sMove;
            if (pgnMoves.get(i).duckMove != -1) {
                s += "@" + Pos.toString(pgnMoves.get(i).duckMove);
            }
            s += " ";

            // TODO this was commented? bug?
            if (pgnMoves.get(i).sAnnotation.length() > 0) {
                s += " {" + pgnMoves.get(i).sAnnotation + "}\n ";
            }
        }

        return s;
    }

    public ArrayList<PGNEntry> getPGNEntries() {
        return pgnMoves;
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

    private static class NextMatch {
        final Matcher matcher;
        int start = -1;
        int end = -1;
        boolean has = false;

        NextMatch(Matcher matcher) {
            this.matcher = matcher;
        }

        // Ensure this match candidate is positioned at/after cursor.
        void seek(int cursor) {
            // If we already have a cached match but it's behind the cursor, advance.
            while (has && start < cursor) {
                has = matcher.find();
                if (has) {
                    start = matcher.start();
                    end = matcher.end();
                }
            }

            // If we don't have a cached match yet, find the first one at/after cursor.
            if (!has) {
                matcher.region(cursor, matcher.regionEnd());
                has = matcher.find();
                if (has) {
                    start = matcher.start();
                    end = matcher.end();
                }
            }
        }
    }

}
