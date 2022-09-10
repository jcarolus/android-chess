package jwtc.android.chess.constants;

import jwtc.android.chess.R;
import jwtc.chess.board.BoardConstants;
import jwtc.chess.board.ChessBoard;

public class PieceSets {
    public static int[][][] PIECES = new int[8][2][6]; // set:color:piece
    public static final int ALPHA = 0;
    public static final int MERIDA = 1;
    public static final int LEIPZIG = 2;
    public static final int CALIFORNIA = 3;
    public static final int COMPANION = 4;
    public static final int CHESSNUT = 5;
    public static final int KOSAL = 6;
    public static final int STAUNTY = 7;

    public static final int BLINDFOLD_SHOW_PIECES = 0;
    public static final int BLINDFOLD_HIDE_PIECES = 1;
    public static final int BLINDFOLD_SHOW_PIECE_LOCATION = 2;

    public static int selectedSet = 0;
    public static int selectedBlindfoldMode = 0;

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

        PIECES[CALIFORNIA][ChessBoard.BLACK][BoardConstants.PAWN] = R.drawable.ic_pieces_california_bp;
        PIECES[CALIFORNIA][ChessBoard.BLACK][BoardConstants.KNIGHT] = R.drawable.ic_pieces_california_bn;
        PIECES[CALIFORNIA][ChessBoard.BLACK][BoardConstants.BISHOP] = R.drawable.ic_pieces_california_bb;
        PIECES[CALIFORNIA][ChessBoard.BLACK][BoardConstants.ROOK] = R.drawable.ic_pieces_california_br;
        PIECES[CALIFORNIA][ChessBoard.BLACK][BoardConstants.QUEEN] = R.drawable.ic_pieces_california_bq;
        PIECES[CALIFORNIA][ChessBoard.BLACK][BoardConstants.KING] = R.drawable.ic_pieces_california_bk;
        PIECES[CALIFORNIA][ChessBoard.WHITE][BoardConstants.PAWN] = R.drawable.ic_pieces_california_wp;
        PIECES[CALIFORNIA][ChessBoard.WHITE][BoardConstants.KNIGHT] = R.drawable.ic_pieces_california_wn;
        PIECES[CALIFORNIA][ChessBoard.WHITE][BoardConstants.BISHOP] = R.drawable.ic_pieces_california_wb;
        PIECES[CALIFORNIA][ChessBoard.WHITE][BoardConstants.ROOK] = R.drawable.ic_pieces_california_wr;
        PIECES[CALIFORNIA][ChessBoard.WHITE][BoardConstants.QUEEN] = R.drawable.ic_pieces_california_wq;
        PIECES[CALIFORNIA][ChessBoard.WHITE][BoardConstants.KING] = R.drawable.ic_pieces_california_wk;

        PIECES[COMPANION][ChessBoard.BLACK][BoardConstants.PAWN] = R.drawable.ic_pieces_companion_bp;
        PIECES[COMPANION][ChessBoard.BLACK][BoardConstants.KNIGHT] = R.drawable.ic_pieces_companion_bn;
        PIECES[COMPANION][ChessBoard.BLACK][BoardConstants.BISHOP] = R.drawable.ic_pieces_companion_bb;
        PIECES[COMPANION][ChessBoard.BLACK][BoardConstants.ROOK] = R.drawable.ic_pieces_companion_br;
        PIECES[COMPANION][ChessBoard.BLACK][BoardConstants.QUEEN] = R.drawable.ic_pieces_companion_bq;
        PIECES[COMPANION][ChessBoard.BLACK][BoardConstants.KING] = R.drawable.ic_pieces_companion_bk;
        PIECES[COMPANION][ChessBoard.WHITE][BoardConstants.PAWN] = R.drawable.ic_pieces_companion_wp;
        PIECES[COMPANION][ChessBoard.WHITE][BoardConstants.KNIGHT] = R.drawable.ic_pieces_companion_wn;
        PIECES[COMPANION][ChessBoard.WHITE][BoardConstants.BISHOP] = R.drawable.ic_pieces_companion_wb;
        PIECES[COMPANION][ChessBoard.WHITE][BoardConstants.ROOK] = R.drawable.ic_pieces_companion_wr;
        PIECES[COMPANION][ChessBoard.WHITE][BoardConstants.QUEEN] = R.drawable.ic_pieces_companion_wq;
        PIECES[COMPANION][ChessBoard.WHITE][BoardConstants.KING] = R.drawable.ic_pieces_companion_wk;

        PIECES[CHESSNUT][ChessBoard.BLACK][BoardConstants.PAWN] = R.drawable.ic_pieces_chessnut_bp;
        PIECES[CHESSNUT][ChessBoard.BLACK][BoardConstants.KNIGHT] = R.drawable.ic_pieces_chessnut_bn;
        PIECES[CHESSNUT][ChessBoard.BLACK][BoardConstants.BISHOP] = R.drawable.ic_pieces_chessnut_bb;
        PIECES[CHESSNUT][ChessBoard.BLACK][BoardConstants.ROOK] = R.drawable.ic_pieces_chessnut_br;
        PIECES[CHESSNUT][ChessBoard.BLACK][BoardConstants.QUEEN] = R.drawable.ic_pieces_chessnut_bq;
        PIECES[CHESSNUT][ChessBoard.BLACK][BoardConstants.KING] = R.drawable.ic_pieces_chessnut_bk;
        PIECES[CHESSNUT][ChessBoard.WHITE][BoardConstants.PAWN] = R.drawable.ic_pieces_chessnut_wp;
        PIECES[CHESSNUT][ChessBoard.WHITE][BoardConstants.KNIGHT] = R.drawable.ic_pieces_chessnut_wn;
        PIECES[CHESSNUT][ChessBoard.WHITE][BoardConstants.BISHOP] = R.drawable.ic_pieces_chessnut_wb;
        PIECES[CHESSNUT][ChessBoard.WHITE][BoardConstants.ROOK] = R.drawable.ic_pieces_chessnut_wr;
        PIECES[CHESSNUT][ChessBoard.WHITE][BoardConstants.QUEEN] = R.drawable.ic_pieces_chessnut_wq;
        PIECES[CHESSNUT][ChessBoard.WHITE][BoardConstants.KING] = R.drawable.ic_pieces_chessnut_wk;

        PIECES[KOSAL][ChessBoard.BLACK][BoardConstants.PAWN] = R.drawable.ic_pieces_kosal_bp;
        PIECES[KOSAL][ChessBoard.BLACK][BoardConstants.KNIGHT] = R.drawable.ic_pieces_kosal_bn;
        PIECES[KOSAL][ChessBoard.BLACK][BoardConstants.BISHOP] = R.drawable.ic_pieces_kosal_bb;
        PIECES[KOSAL][ChessBoard.BLACK][BoardConstants.ROOK] = R.drawable.ic_pieces_kosal_br;
        PIECES[KOSAL][ChessBoard.BLACK][BoardConstants.QUEEN] = R.drawable.ic_pieces_kosal_bq;
        PIECES[KOSAL][ChessBoard.BLACK][BoardConstants.KING] = R.drawable.ic_pieces_kosal_bk;
        PIECES[KOSAL][ChessBoard.WHITE][BoardConstants.PAWN] = R.drawable.ic_pieces_kosal_wp;
        PIECES[KOSAL][ChessBoard.WHITE][BoardConstants.KNIGHT] = R.drawable.ic_pieces_kosal_wn;
        PIECES[KOSAL][ChessBoard.WHITE][BoardConstants.BISHOP] = R.drawable.ic_pieces_kosal_wb;
        PIECES[KOSAL][ChessBoard.WHITE][BoardConstants.ROOK] = R.drawable.ic_pieces_kosal_wr;
        PIECES[KOSAL][ChessBoard.WHITE][BoardConstants.QUEEN] = R.drawable.ic_pieces_kosal_wq;
        PIECES[KOSAL][ChessBoard.WHITE][BoardConstants.KING] = R.drawable.ic_pieces_kosal_wk;

        PIECES[STAUNTY][ChessBoard.BLACK][BoardConstants.PAWN] = R.drawable.ic_pieces_staunty_bp;
        PIECES[STAUNTY][ChessBoard.BLACK][BoardConstants.KNIGHT] = R.drawable.ic_pieces_staunty_bn;
        PIECES[STAUNTY][ChessBoard.BLACK][BoardConstants.BISHOP] = R.drawable.ic_pieces_staunty_bb;
        PIECES[STAUNTY][ChessBoard.BLACK][BoardConstants.ROOK] = R.drawable.ic_pieces_staunty_br;
        PIECES[STAUNTY][ChessBoard.BLACK][BoardConstants.QUEEN] = R.drawable.ic_pieces_staunty_bq;
        PIECES[STAUNTY][ChessBoard.BLACK][BoardConstants.KING] = R.drawable.ic_pieces_staunty_bk;
        PIECES[STAUNTY][ChessBoard.WHITE][BoardConstants.PAWN] = R.drawable.ic_pieces_staunty_wp;
        PIECES[STAUNTY][ChessBoard.WHITE][BoardConstants.KNIGHT] = R.drawable.ic_pieces_staunty_wn;
        PIECES[STAUNTY][ChessBoard.WHITE][BoardConstants.BISHOP] = R.drawable.ic_pieces_staunty_wb;
        PIECES[STAUNTY][ChessBoard.WHITE][BoardConstants.ROOK] = R.drawable.ic_pieces_staunty_wr;
        PIECES[STAUNTY][ChessBoard.WHITE][BoardConstants.QUEEN] = R.drawable.ic_pieces_staunty_wq;
        PIECES[STAUNTY][ChessBoard.WHITE][BoardConstants.KING] = R.drawable.ic_pieces_staunty_wk;
    }
}
