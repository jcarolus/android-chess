package jwtc.android.chess.constants;

import jwtc.android.chess.R;
import jwtc.chess.board.BoardConstants;
import jwtc.chess.board.ChessBoard;

public class PieceSets {
    public static int[][][] PIECES = new int[3][2][6]; // set:color:piece
    public static final int ALPHA = 0;
    public static final int MERIDA = 1;
    public static final int LEIPZIG = 2;

    public static int selectedSet = 0;

    static {
        PIECES[ALPHA][ChessBoard.BLACK][BoardConstants.PAWN] = R.drawable.ic_pieces_alpha_bp;
        PIECES[ALPHA][ChessBoard.BLACK][BoardConstants.KNIGHT] = R.drawable.ic_pieces_alpha_bn;
        PIECES[ALPHA][ChessBoard.BLACK][BoardConstants.BISHOP] = R.drawable.ic_pieces_alpha_bb;
        PIECES[ALPHA][ChessBoard.BLACK][BoardConstants.ROOK] = R.drawable.ic_pieces_alpha_br;
        PIECES[ALPHA][ChessBoard.BLACK][BoardConstants.QUEEN] = R.drawable.ic_pieces_alpha_bq;
        PIECES[ALPHA][ChessBoard.BLACK][BoardConstants.KING] = R.drawable.ic_pieces_alpha_bk;
        PIECES[ALPHA][ChessBoard.WHITE][BoardConstants.PAWN] = R.drawable.ic_pieces_alpha_wp;
        PIECES[ALPHA][ChessBoard.WHITE][BoardConstants.KNIGHT] = R.drawable.ic_pieces_alpha_wn;
        PIECES[ALPHA][ChessBoard.WHITE][BoardConstants.BISHOP] = R.drawable.ic_pieces_alpha_wb;
        PIECES[ALPHA][ChessBoard.WHITE][BoardConstants.ROOK] = R.drawable.ic_pieces_alpha_wr;
        PIECES[ALPHA][ChessBoard.WHITE][BoardConstants.QUEEN] = R.drawable.ic_pieces_alpha_wq;
        PIECES[ALPHA][ChessBoard.WHITE][BoardConstants.KING] = R.drawable.ic_pieces_alpha_wk;

        PIECES[MERIDA][ChessBoard.BLACK][BoardConstants.PAWN] = R.drawable.ic_pieces_merida_bp;
        PIECES[MERIDA][ChessBoard.BLACK][BoardConstants.KNIGHT] = R.drawable.ic_pieces_merida_bn;
        PIECES[MERIDA][ChessBoard.BLACK][BoardConstants.BISHOP] = R.drawable.ic_pieces_merida_bb;
        PIECES[MERIDA][ChessBoard.BLACK][BoardConstants.ROOK] = R.drawable.ic_pieces_merida_br;
        PIECES[MERIDA][ChessBoard.BLACK][BoardConstants.QUEEN] = R.drawable.ic_pieces_merida_bq;
        PIECES[MERIDA][ChessBoard.BLACK][BoardConstants.KING] = R.drawable.ic_pieces_merida_bk;
        PIECES[MERIDA][ChessBoard.WHITE][BoardConstants.PAWN] = R.drawable.ic_pieces_merida_wp;
        PIECES[MERIDA][ChessBoard.WHITE][BoardConstants.KNIGHT] = R.drawable.ic_pieces_merida_wn;
        PIECES[MERIDA][ChessBoard.WHITE][BoardConstants.BISHOP] = R.drawable.ic_pieces_merida_wb;
        PIECES[MERIDA][ChessBoard.WHITE][BoardConstants.ROOK] = R.drawable.ic_pieces_merida_wr;
        PIECES[MERIDA][ChessBoard.WHITE][BoardConstants.QUEEN] = R.drawable.ic_pieces_merida_wq;
        PIECES[MERIDA][ChessBoard.WHITE][BoardConstants.KING] = R.drawable.ic_pieces_merida_wk;

        PIECES[LEIPZIG][ChessBoard.BLACK][BoardConstants.PAWN] = R.drawable.ic_pieces_leipzig_bp;
        PIECES[LEIPZIG][ChessBoard.BLACK][BoardConstants.KNIGHT] = R.drawable.ic_pieces_leipzig_bn;
        PIECES[LEIPZIG][ChessBoard.BLACK][BoardConstants.BISHOP] = R.drawable.ic_pieces_leipzig_bb;
        PIECES[LEIPZIG][ChessBoard.BLACK][BoardConstants.ROOK] = R.drawable.ic_pieces_leipzig_br;
        PIECES[LEIPZIG][ChessBoard.BLACK][BoardConstants.QUEEN] = R.drawable.ic_pieces_leipzig_bq;
        PIECES[LEIPZIG][ChessBoard.BLACK][BoardConstants.KING] = R.drawable.ic_pieces_leipzig_bk;
        PIECES[LEIPZIG][ChessBoard.WHITE][BoardConstants.PAWN] = R.drawable.ic_pieces_leipzig_wp;
        PIECES[LEIPZIG][ChessBoard.WHITE][BoardConstants.KNIGHT] = R.drawable.ic_pieces_leipzig_wn;
        PIECES[LEIPZIG][ChessBoard.WHITE][BoardConstants.BISHOP] = R.drawable.ic_pieces_leipzig_wb;
        PIECES[LEIPZIG][ChessBoard.WHITE][BoardConstants.ROOK] = R.drawable.ic_pieces_leipzig_wr;
        PIECES[LEIPZIG][ChessBoard.WHITE][BoardConstants.QUEEN] = R.drawable.ic_pieces_leipzig_wq;
        PIECES[LEIPZIG][ChessBoard.WHITE][BoardConstants.KING] = R.drawable.ic_pieces_leipzig_wk;
    }
}
