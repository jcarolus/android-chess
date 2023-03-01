package jwtc.chess;

import android.util.Log;

import jwtc.chess.board.BoardConstants;
import jwtc.chess.board.ChessBoard;

public class JNI {
    private static final String TAG = "JNI";
    private static volatile JNI instance;

    private JNI() {}

    // guarantee singleton instance
    public static JNI getInstance() {
        JNI result = instance;
        if (result != null) {
            return result;
        }
        synchronized(JNI.class) {
            if (instance == null) {
                instance = new JNI();
            }
            return instance;
        }
    }

    public void newGame() {
        newGame(BoardConstants.VARIANT_DEFAULT);
    }

    public void newGame(int variant) {
        reset();

        if (variant == BoardConstants.VARIANT_DEFAULT) {
            newGameFromFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        } else {
            newGameFromFEN("rnbqkbnr/pppppppp/8/7$/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        }
    }


    public final boolean initFEN(final String sFEN) {
        return newGameFromFEN(sFEN) != 0;
    }

    protected boolean isPosFree(int pos) {
        return (pieceAt(ChessBoard.BLACK, pos) == ChessBoard.FIELD &&
                pieceAt(ChessBoard.WHITE, pos) == ChessBoard.FIELD);
    }

    protected int getAvailableCol(int colNum) {
        int col = 0, i = 0, pos;
        do {
            pos = Pos.fromColAndRow(col, 0);
            if (isPosFree(pos)) {
                i++;
            }
            col++;
        } while (i <= colNum && col < 9);

        col--;
        return col;
    }

    protected int getFirstAvailableCol() {
        int col = 0, pos;
        do {
            pos = Pos.fromColAndRow(col, 0);
            if (isPosFree(pos)) {
                return col;
            }
            col++;
        } while (col < 8);
        return col;
    }


    /*
0 NNxxx
1 NxNxx
2 NxxNx
3 NxxxN
4 xNNxx
5 xNxNx
6 xNxxN
7 xxNNx
8 xxNxN
9 xxxNN
    */
    public int initRandomFisher(int n) {

        reset();

        int[][] NN = {
                {0, 1},
                {0, 2},
                {0, 3},
                {0, 4},
                {1, 2},
                {1, 3},
                {1, 4},
                {2, 3},
                {2, 4},
                {3, 4}
        };

        int Bw, Bb, Q, N1, N2;

        int col, col2, pos, ret = 0;

        if (n >= 0) {

            Bw = n % 4;
            n = (int) Math.floor(n / 4.0);

            Bb = n % 4;
            n = (int) Math.floor(n / 4.0);

            Q = n % 6;
            n = (int) Math.floor(n / 6.0);

            n = (n % 10);

            N1 = NN[n][0];
            N2 = NN[n][1];

        } else {

            Bw = (int) (Math.random() * 3);
            Bb = (int) (Math.random() * 3);

            Q = (int) (Math.random() * 5);

            n = (int) (Math.random() * 8);

            N1 = NN[n][0];
            N2 = NN[n][1];

        }

        ret = (96 * (5 - (((3 - N1) * (4 - N1)) / 2) + N2)) + (16 * Q) + (4 * Bb) + Bw;

        Log.i("Chess960", "Bw " + Bw + " Bb " + Bb + " Q " + Q + " n " + n + " N1 " + N1 + " N2 " + N2);
        // white square bishop
        col = 1 + 2 * Bw;
        Log.i("Chess960", "Bw col " + col);
        pos = Pos.fromColAndRow(col, 0);
        putPiece(pos, ChessBoard.BISHOP, ChessBoard.BLACK);
        pos = Pos.fromColAndRow(col, 7);
        putPiece(pos, ChessBoard.BISHOP, ChessBoard.WHITE);

        // black-square bishop
        col = 2 * Bb;
        Log.i("Chess960", "Bb col " + col);
        pos = Pos.fromColAndRow(col, 0);
        putPiece(pos, ChessBoard.BISHOP, ChessBoard.BLACK);
        pos = Pos.fromColAndRow(col, 7);
        putPiece(pos, ChessBoard.BISHOP, ChessBoard.WHITE);

        // queen
        col = getAvailableCol(Q);
        Log.i("Chess960", "Q col " + col);
        pos = Pos.fromColAndRow(col, 0);
        putPiece(pos, ChessBoard.QUEEN, ChessBoard.BLACK);
        pos = Pos.fromColAndRow(col, 7);
        putPiece(pos, ChessBoard.QUEEN, ChessBoard.WHITE);

        // knight 1
        col = getAvailableCol(N1);
        col2 = getAvailableCol(N2);
        Log.i("Chess960", "N1 col " + col + " N2 " + col2);
        pos = Pos.fromColAndRow(col, 0);
        putPiece(pos, ChessBoard.KNIGHT, ChessBoard.BLACK);
        pos = Pos.fromColAndRow(col, 7);
        putPiece(pos, ChessBoard.KNIGHT, ChessBoard.WHITE);

        // knight 2
        pos = Pos.fromColAndRow(col2, 0);
        putPiece(pos, ChessBoard.KNIGHT, ChessBoard.BLACK);
        pos = Pos.fromColAndRow(col2, 7);
        putPiece(pos, ChessBoard.KNIGHT, ChessBoard.WHITE);

        // ROOK A
        col = getFirstAvailableCol();
        Log.i("Chess960", "R1 col " + col);
        pos = Pos.fromColAndRow(col, 0);
        putPiece(pos, ChessBoard.ROOK, ChessBoard.BLACK);
        pos = Pos.fromColAndRow(col, 7);
        putPiece(pos, ChessBoard.ROOK, ChessBoard.WHITE);

        // KING
        col = getFirstAvailableCol();
        Log.i("Chess960", "K col " + col);
        pos = Pos.fromColAndRow(col, 0);
        putPiece(pos, ChessBoard.KING, ChessBoard.BLACK);
        pos = Pos.fromColAndRow(col, 7);
        putPiece(pos, ChessBoard.KING, ChessBoard.WHITE);
        // ROOK H
        col = getFirstAvailableCol();
        Log.i("Chess960", "R2 col " + col);
        pos = Pos.fromColAndRow(col, 0);
        putPiece(pos, ChessBoard.ROOK, ChessBoard.BLACK);
        pos = Pos.fromColAndRow(col, 7);
        putPiece(pos, ChessBoard.ROOK, ChessBoard.WHITE);

        //
        putPiece(ChessBoard.a7, ChessBoard.PAWN, ChessBoard.BLACK);
        putPiece(ChessBoard.b7, ChessBoard.PAWN, ChessBoard.BLACK);
        putPiece(ChessBoard.c7, ChessBoard.PAWN, ChessBoard.BLACK);
        putPiece(ChessBoard.d7, ChessBoard.PAWN, ChessBoard.BLACK);
        putPiece(ChessBoard.e7, ChessBoard.PAWN, ChessBoard.BLACK);
        putPiece(ChessBoard.f7, ChessBoard.PAWN, ChessBoard.BLACK);
        putPiece(ChessBoard.g7, ChessBoard.PAWN, ChessBoard.BLACK);
        putPiece(ChessBoard.h7, ChessBoard.PAWN, ChessBoard.BLACK);

        putPiece(ChessBoard.a2, ChessBoard.PAWN, ChessBoard.WHITE);
        putPiece(ChessBoard.b2, ChessBoard.PAWN, ChessBoard.WHITE);
        putPiece(ChessBoard.c2, ChessBoard.PAWN, ChessBoard.WHITE);
        putPiece(ChessBoard.d2, ChessBoard.PAWN, ChessBoard.WHITE);
        putPiece(ChessBoard.e2, ChessBoard.PAWN, ChessBoard.WHITE);
        putPiece(ChessBoard.f2, ChessBoard.PAWN, ChessBoard.WHITE);
        putPiece(ChessBoard.g2, ChessBoard.PAWN, ChessBoard.WHITE);
        putPiece(ChessBoard.h2, ChessBoard.PAWN, ChessBoard.WHITE);

        setCastlingsEPAnd50(1, 1, 1, 1, -1, 0);

        commitBoard();

        return ret;
    }

    public native void destroy();

    public native void setVariant(int variant);

    public native int requestMove(int from, int to);

    public native int requestDuckMove(int duckPos);

    public native int move(int move);

    public native void undo();

    public native void reset();

    public native int newGameFromFEN(String sFEN);

    public native void putPiece(int pos, int piece, int turn);

    public native void searchMove(int secs, int quiescentOn);

    public native void searchDepth(int depth, int quiescentOn);

    public native int getMove();

    public native int getDuckMove();

    public native int getBoardValue();

    public native int peekSearchDone();

    public native int peekSearchBestMove(int ply);

    public native int peekSearchBestDuckMove(int ply);

    public native int peekSearchBestValue();

    public native int peekSearchDepth();

    public native int getEvalCount();

    public native void setPromo(int piece);

    public native int getState();

    public native int isEnded();

    public native void setCastlingsEPAnd50(int wccl, int wccs, int bccl, int bccs, int ep, int r50);

    public native int getNumBoard();

    public native int getTurn();

    public native void commitBoard();

    public native void setTurn(int turn);

    //public native int[] getMoveArray();
    public native int getMoveArraySize();

    public native int getMoveArrayAt(int i);

    public native int pieceAt(int turn, int pos);

    public native int getDuckPos();

    public native int getMyDuckPos();

    public native String getMyMoveToString();

    public native int getMyMove();

    public native int isLegalPosition();

    public native int isAmbiguousCastle(int from, int to);

    public native int doCastleMove(int from, int to);

    public native String toFEN();

    public native void removePiece(int turn, int pos);

    public native long getHashKey();

    public native void loadDB(String sFile, int depth);

    public native void interrupt();

    public native int getNumCaptured(int turn, int piece);

    static {
        System.loadLibrary("chess-jni");
    }
}
