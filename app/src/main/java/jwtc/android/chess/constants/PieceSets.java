package jwtc.android.chess.constants;

import jwtc.android.chess.R;
import jwtc.chess.board.BoardConstants;
import jwtc.chess.board.ChessBoard;

public class PieceSets {
    public static int[][][] PIECES = new int[1][2][6]; // set:color:piece
    public static final int ALPHA = 0;
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
    }
}
