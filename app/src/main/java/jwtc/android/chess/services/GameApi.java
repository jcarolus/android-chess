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

public class GameApi {
    private static final String TAG = "GameApi";
    protected ArrayList<GameListener> listeners = new ArrayList<>();
    protected JNI jni;
    private static Pattern _patNum;
    private static Pattern _patAnnot;
    private static Pattern _patMove;
    private static Pattern _patCastling;

    static {
        try {
            _patNum = Pattern.compile("(\\d+)\\.");
            _patAnnot = Pattern.compile("\\{([^\\{]*)\\}");
            _patMove = Pattern.compile("(K|Q|R|B|N)?(a|b|c|d|e|f|g|h)?(1|2|3|4|5|6|7|8)?(x)?(a|b|c|d|e|f|g|h)(1|2|3|4|5|6|7|8)(=Q|=R|=B|=N)?(@[a-h][1-8])?(\\+|#)?([\\?\\!]*)?[\\s]*");
            _patCastling = Pattern.compile("(O\\-O|O\\-O\\-O)(@[a-h][1-8])?(\\+|#)?([\\?\\!]*)?");
        } catch (Exception e) {}
    }

    protected HashMap<String, String> _mapPGNHead; //
    protected ArrayList<PGNEntry> _arrPGN;

    public GameApi() {
        jni = JNI.getInstance();
        jni.reset();
        _mapPGNHead = new HashMap<String, String>();
        _arrPGN = new ArrayList<PGNEntry>();
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

    public boolean requestMove(int from, int to) {
        Log.i(TAG, "requestMove");
        if (jni.isEnded() != 0)
            return false;

        if (jni.requestMove(from, to) == 0) {
            return false;
        }

        final int move = jni.getMyMove();

        addPGNEntry(jni.getNumBoard() - 1, jni.getMyMoveToString(), "", move, -1);

        dispatchMove(move);

        return true;
    }

    public boolean requestMoveCastle(int from, int to) {
        if (jni.isEnded() != 0) {
            return false;
        }

        if (jni.doCastleMove(from, to) == 0) {
            return false;
        }
        final int move = jni.getMyMove();

        addPGNEntry(jni.getNumBoard() - 1, jni.getMyMoveToString(), "", move, -1);

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
        jumptoMove(jni.getNumBoard());
    }

    public void jumptoMove(int ply) {
        Log.d(TAG, "jumptoMove " + ply);

        if (ply <= _arrPGN.size() && ply >= 0) {
            int boardPly = jni.getNumBoard();
            if (ply >= boardPly) {
                while (ply >= boardPly) {
                    jni.move(_arrPGN.get(boardPly - 1)._move);
                    Log.d(TAG, "duck at " + _arrPGN.get(boardPly - 1)._duckMove);
                    if (_arrPGN.get(boardPly - 1)._duckMove != -1) {
                        jni.requestDuckMove(_arrPGN.get(boardPly - 1)._duckMove);
                    }
                    boardPly++;
                }
            } else {
                while (ply < boardPly) {
                    jni.undo();
                    boardPly--;
                }
            }
            dispatchState();
        }
    }

    public int getPGNSize() {
        return _arrPGN.size();
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

        _mapPGNHead.clear();
        _mapPGNHead.put("Event", "?");
        _mapPGNHead.put("Site", "?");
        _mapPGNHead.put("Round", "?");
        _mapPGNHead.put("White", Resources.getSystem().getString(android.R.string.unknownName));
        _mapPGNHead.put("Black", Resources.getSystem().getString(android.R.string.unknownName));
        _mapPGNHead.put("Date", formatter.format(d));

        _arrPGN.clear();

        jni.newGame(variant);

        if (variant == BoardConstants.VARIANT_DUCK) {
            _mapPGNHead.put("Setup", "1");
            _mapPGNHead.put("FEN", jni.toFEN());
        }

        dispatchState();
    }


    public boolean initFEN(String sFEN, boolean resetHead) {

        if (jni.initFEN(sFEN)) {

            if (resetHead) {
                _mapPGNHead.clear();
                _mapPGNHead.put("Event", "?");
                _mapPGNHead.put("Site", "?");
                _mapPGNHead.put("Round", "?");
                _mapPGNHead.put("White", Resources.getSystem().getString(android.R.string.unknownName));
                _mapPGNHead.put("Black", Resources.getSystem().getString(android.R.string.unknownName));
            }
            _mapPGNHead.put("Setup", "1");
            _mapPGNHead.put("FEN", sFEN);

            _arrPGN.clear();

            dispatchState();
            return true;
        }
        return false;
    }

    public int newGameRandomFischer(int seed) {

        int ret = jni.initRandomFisher(seed);

        _mapPGNHead.clear();
        _mapPGNHead.put("Event", "?");
        _mapPGNHead.put("Site", "?");
        _mapPGNHead.put("Round", "?");
        _mapPGNHead.put("White", Resources.getSystem().getString(android.R.string.unknownName));
        _mapPGNHead.put("Black", Resources.getSystem().getString(android.R.string.unknownName));

        _mapPGNHead.put("Variant", "Fischerandom");
        _mapPGNHead.put("Setup", "1");
        _mapPGNHead.put("FEN", jni.toFEN());

        _arrPGN.clear();

        dispatchState();
        return ret;
    }

    public boolean loadPGN(String s) {
        jni.newGame();

        loadPGNHead(s);

        if(loadPGNMoves(s)) {
            dispatchState();
            return true;
        }
        return false;
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
        addPGNEntry(jni.getNumBoard() - 1, jni.getMyMoveToString(), sAnnotation, jni.getMyMove(), -1);

        return true;
    }

    private boolean moveDuck(int duckMove) {
        if (jni.requestDuckMove(duckMove) == 0) {
            return false;
        }

        int index = jni.getNumBoard() - 2;
        if (index >= 0 && index < _arrPGN.size()) {
            Log.d(TAG, " set duckmove " + index + " " + Pos.toString(duckMove));
            _arrPGN.get(index)._duckMove = duckMove;
        }
        return true;
    }

    private synchronized final boolean requestMove(final String token, final Matcher matchToken, final String sCastle, final String sAnnotation) {

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
                        int numBoard = jni.getNumBoard() - 3;
                        if (numBoard >= 0) {
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
                        int numBoard = jni.getNumBoard() - 3;
                        if (numBoard >= 0) {
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


    private void loadPGNHead(String s) {

        s = s.replaceAll("[\\r\\n]+", " ");
        s = s.replaceAll("  ", " ");
        s = s.trim();
        s = s + " "; // for last token

        Matcher matchToken;
        String token;
        Pattern patTag = Pattern.compile("\\[(\\w+) \"(.*)\"\\]");

        int i = 0, pos;
        while (i < s.length()) {
            pos = s.indexOf(" ", i);

            if (pos > 0) {

                if (s.charAt(i) == '[') {
                    pos = s.indexOf("]", i);
                    if (pos == -1)
                        break;
                    pos++;
                }
            } else {
                break;
            }

            token = s.substring(i, pos);

            i = pos + 1;

            matchToken = patTag.matcher(token);
            if (matchToken.matches()) {
                _mapPGNHead.put(matchToken.group(1), matchToken.group(2));
                if (matchToken.group(1).equals("FEN")) {
                    initFEN(matchToken.group(2), false);
                }
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

    private void removeDoubleSpaces(StringBuffer sb) {
        int iSpace = sb.indexOf("  ");
        while (iSpace >= 0) {
            sb.delete(iSpace, iSpace + 1);
            iSpace = sb.indexOf("  ");
        }
    }

    private boolean loadPGNMoves(String s) {
        _arrPGN.clear();

        s = s.replaceAll("[\\r\\n\\t]+", " ");
        //s = s.replaceAll("\\{[^\\}]*\\}", ""); // remove comments

        StringBuffer sb = new StringBuffer(s);

        removeDoubleSpaces(sb);

        //Log.i("loadPGNMoves", sb.toString());

        try {
            while (removeComment(sb)) {
                ;
            }
        } catch (Exception e) {
            Log.w("loadPGNMoves", "Exception: " + e);
            Log.i("loadPGNMoves", sb.toString());
            return false;
        }

        removeDoubleSpaces(sb);

        s = sb.toString();

        //Log.i("loadPGNMoves", s);

		/*
		// the ( alternative ( move ) )
		Pattern pat = Pattern.compile("(\\([^\\)\\(]*\\))?");
		int i = 0, cnt = 0;
		while(s.indexOf("(") >= 0 && cnt < 200){
			cnt++;
			Matcher m = pat.matcher(s);
			if(m.find())
				s = m.replaceAll("");
			else {
				Log.w("PGN Moves", "Could not find parentheses");
				return false;
			}
		}
		if(s.indexOf(")") >= 0){
			Log.w("PGN Moves", "Still parentheses");
			return false;
		}
		*/

        int i;
        s = s.replaceAll("\\$\\d+", ""); // the $x
        //s = s.replaceAll("  ", " ");
        s = s.trim();
        s = s + " "; // for last token
        try {

            int pos, numMove = 1, tmp, posDot;
            i = 0;

            Matcher matchToken;
            String token, sAnnotation;
            sAnnotation = "";
            while (i < s.length()) {
                pos = s.indexOf(" ", i);
                posDot = s.indexOf(".", i);

                token = "";
                if (pos > 0) {
                    if (s.charAt(i) == '[') {
                        pos = s.indexOf("]", i);
                        if (pos == -1)
                            break;
                        pos++;
                        token = s.substring(i, pos);

                        i = pos + 1;
                    } else if (s.charAt(i) == '{') {
                        pos = s.indexOf("}", i);
                        if (pos == -1)
                            break;
                        pos++;
                        token = s.substring(i, pos);

                        i = pos + 1;
                    } else if (posDot > 0 && posDot < pos) {
                        if (s.length() > posDot + 3 && s.substring(posDot, posDot + 3).equals("...")) {
                            i = posDot + 3;
                            continue;
                        } else {
                            posDot++;
                            token = s.substring(i, posDot);
                            i = posDot;
                        }
                    } else {
                        token = s.substring(i, pos);
                        i = pos + 1;
                    }
                } else {
                    break;
                }
                if (token.equals("..")) {
                    continue;
                }

                matchToken = _patNum.matcher(token);
                if (matchToken.matches()) {
                    tmp = Integer.parseInt(matchToken.group(1));
                    if (tmp == numMove)
                        numMove++;
                    else {
                        break;
                    }
                } else {
                    matchToken = _patAnnot.matcher(token);
                    if (matchToken.matches()) {
                        sAnnotation = matchToken.group(1);
                    } else {

                        matchToken = _patMove.matcher(token);
                        if (matchToken.matches()) {

                            if (requestMove(token, matchToken, null, sAnnotation)) {
                                sAnnotation = "";
                            } else {
                                break;
                            }
                        } else {

                            matchToken = _patCastling.matcher(token);
                            if (matchToken.matches()) {

                                if (requestMove(token, matchToken, matchToken.group(1), sAnnotation)) {
                                    sAnnotation = "";
                                } else {
                                    break;
                                }
                            } else {
                                continue;
                            }
                        }
                    }
                }
            }

            if (sAnnotation.length() != 0) {
                setAnnotation(jni.getNumBoard() - 2, sAnnotation);
            }

        } catch (Exception e) {
            Log.d(TAG, "Error loading PGN");
            //System.out.println("@" + e);
            return false;
        }

        return true;
    }


    public void addPGNEntry(int ply, String sMove, String sAnnotation, int move, int duckMove) {
        Log.d(TAG, "addPGNEntry " + ply + ": " + sMove + " @ " + Pos.toString(duckMove) + " = " + duckMove);
        while (ply >= 0 && _arrPGN.size() >= ply) {
            _arrPGN.remove(_arrPGN.size() - 1);
        }
        _arrPGN.add(new PGNEntry(sMove, sAnnotation, move, duckMove));
    }

    public int getFromOfNextMove() {
        int ply = jni.getNumBoard();
        if (_arrPGN.size() >= ply && ply > 0) {
            PGNEntry p = _arrPGN.get(ply - 1);
            return Move.getFrom(p._move);
        }
        return -1;
    }

    public void setAnnotation(int i, String sAnno) {
        if (_arrPGN.size() > i)
            _arrPGN.get(i)._sAnnotation = sAnno;
    }

    public String exportFullPGN() {
        String[] arrHead = {"Event", "Site", "Date", "Round", "White", "Black", "Result", "EventDate",
                "Variant", "Setup", "FEN", "PlyCount"};

        String s = "", key;
        for (int i = 0; i < arrHead.length; i++) {
            key = arrHead[i];
            if (_mapPGNHead.containsKey(key))
                s += "[" + key + " \"" + _mapPGNHead.get(key) + "\"]\n";
        }

        s += exportMovesPGN();
        s += "\n";
        return s;
    }

    protected String exportMovesPGN() {
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

        for (int i = iPly; i < _arrPGN.size(); i++) {
            if ((i - iPly) % 2 == 0) {
                s += ((i - iPly) / 2 + 1) + ". ";
            }
            s += _arrPGN.get(i)._sMove;
            if (_arrPGN.get(i)._duckMove != -1) {
                s += "@" + Pos.toString(_arrPGN.get(i)._duckMove);
            }
            s += " ";

            // TODO this was commented? bug?
            if (_arrPGN.get(i)._sAnnotation.length() > 0) {
                s += " {" + _arrPGN.get(i)._sAnnotation + "}\n ";
            }
        }

        return s;
    }

    public ArrayList<PGNEntry> getPGNEntries() {
        return _arrPGN;
    }

    public void setPGNHeadProperty(String sProp, String sValue) {
        _mapPGNHead.put(sProp, sValue);
    }

    public void setDateLong(long lTime) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(lTime);
        Date d = cal.getTime();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd");
        setPGNHeadProperty("Date", formatter.format(d));
    }

    public String getPGNHeadProperty(String sProp) {
        return _mapPGNHead.get(sProp);
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

}
