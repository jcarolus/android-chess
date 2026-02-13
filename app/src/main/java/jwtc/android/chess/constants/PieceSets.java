package jwtc.android.chess.constants;

import jwtc.android.chess.R;
import jwtc.chess.board.BoardConstants;

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
        PIECES[ALPHA][BoardConstants.BLACK][BoardConstants.PAWN] = R.drawable.ic_pieces_alpha_bp;
        PIECES[ALPHA][BoardConstants.BLACK][BoardConstants.KNIGHT] = R.drawable.ic_pieces_alpha_bn;
        PIECES[ALPHA][BoardConstants.BLACK][BoardConstants.BISHOP] = R.drawable.ic_pieces_alpha_bb;
        PIECES[ALPHA][BoardConstants.BLACK][BoardConstants.ROOK] = R.drawable.ic_pieces_alpha_br;
        PIECES[ALPHA][BoardConstants.BLACK][BoardConstants.QUEEN] = R.drawable.ic_pieces_alpha_bq;
        PIECES[ALPHA][BoardConstants.BLACK][BoardConstants.KING] = R.drawable.ic_pieces_alpha_bk;
        PIECES[ALPHA][BoardConstants.WHITE][BoardConstants.PAWN] = R.drawable.ic_pieces_alpha_wp;
        PIECES[ALPHA][BoardConstants.WHITE][BoardConstants.KNIGHT] = R.drawable.ic_pieces_alpha_wn;
        PIECES[ALPHA][BoardConstants.WHITE][BoardConstants.BISHOP] = R.drawable.ic_pieces_alpha_wb;
        PIECES[ALPHA][BoardConstants.WHITE][BoardConstants.ROOK] = R.drawable.ic_pieces_alpha_wr;
        PIECES[ALPHA][BoardConstants.WHITE][BoardConstants.QUEEN] = R.drawable.ic_pieces_alpha_wq;
        PIECES[ALPHA][BoardConstants.WHITE][BoardConstants.KING] = R.drawable.ic_pieces_alpha_wk;

        PIECES[MERIDA][BoardConstants.BLACK][BoardConstants.PAWN] = R.drawable.ic_pieces_merida_bp;
        PIECES[MERIDA][BoardConstants.BLACK][BoardConstants.KNIGHT] = R.drawable.ic_pieces_merida_bn;
        PIECES[MERIDA][BoardConstants.BLACK][BoardConstants.BISHOP] = R.drawable.ic_pieces_merida_bb;
        PIECES[MERIDA][BoardConstants.BLACK][BoardConstants.ROOK] = R.drawable.ic_pieces_merida_br;
        PIECES[MERIDA][BoardConstants.BLACK][BoardConstants.QUEEN] = R.drawable.ic_pieces_merida_bq;
        PIECES[MERIDA][BoardConstants.BLACK][BoardConstants.KING] = R.drawable.ic_pieces_merida_bk;
        PIECES[MERIDA][BoardConstants.WHITE][BoardConstants.PAWN] = R.drawable.ic_pieces_merida_wp;
        PIECES[MERIDA][BoardConstants.WHITE][BoardConstants.KNIGHT] = R.drawable.ic_pieces_merida_wn;
        PIECES[MERIDA][BoardConstants.WHITE][BoardConstants.BISHOP] = R.drawable.ic_pieces_merida_wb;
        PIECES[MERIDA][BoardConstants.WHITE][BoardConstants.ROOK] = R.drawable.ic_pieces_merida_wr;
        PIECES[MERIDA][BoardConstants.WHITE][BoardConstants.QUEEN] = R.drawable.ic_pieces_merida_wq;
        PIECES[MERIDA][BoardConstants.WHITE][BoardConstants.KING] = R.drawable.ic_pieces_merida_wk;

        PIECES[LEIPZIG][BoardConstants.BLACK][BoardConstants.PAWN] = R.drawable.ic_pieces_leipzig_bp;
        PIECES[LEIPZIG][BoardConstants.BLACK][BoardConstants.KNIGHT] = R.drawable.ic_pieces_leipzig_bn;
        PIECES[LEIPZIG][BoardConstants.BLACK][BoardConstants.BISHOP] = R.drawable.ic_pieces_leipzig_bb;
        PIECES[LEIPZIG][BoardConstants.BLACK][BoardConstants.ROOK] = R.drawable.ic_pieces_leipzig_br;
        PIECES[LEIPZIG][BoardConstants.BLACK][BoardConstants.QUEEN] = R.drawable.ic_pieces_leipzig_bq;
        PIECES[LEIPZIG][BoardConstants.BLACK][BoardConstants.KING] = R.drawable.ic_pieces_leipzig_bk;
        PIECES[LEIPZIG][BoardConstants.WHITE][BoardConstants.PAWN] = R.drawable.ic_pieces_leipzig_wp;
        PIECES[LEIPZIG][BoardConstants.WHITE][BoardConstants.KNIGHT] = R.drawable.ic_pieces_leipzig_wn;
        PIECES[LEIPZIG][BoardConstants.WHITE][BoardConstants.BISHOP] = R.drawable.ic_pieces_leipzig_wb;
        PIECES[LEIPZIG][BoardConstants.WHITE][BoardConstants.ROOK] = R.drawable.ic_pieces_leipzig_wr;
        PIECES[LEIPZIG][BoardConstants.WHITE][BoardConstants.QUEEN] = R.drawable.ic_pieces_leipzig_wq;
        PIECES[LEIPZIG][BoardConstants.WHITE][BoardConstants.KING] = R.drawable.ic_pieces_leipzig_wk;

        PIECES[CALIFORNIA][BoardConstants.BLACK][BoardConstants.PAWN] = R.drawable.ic_pieces_california_bp;
        PIECES[CALIFORNIA][BoardConstants.BLACK][BoardConstants.KNIGHT] = R.drawable.ic_pieces_california_bn;
        PIECES[CALIFORNIA][BoardConstants.BLACK][BoardConstants.BISHOP] = R.drawable.ic_pieces_california_bb;
        PIECES[CALIFORNIA][BoardConstants.BLACK][BoardConstants.ROOK] = R.drawable.ic_pieces_california_br;
        PIECES[CALIFORNIA][BoardConstants.BLACK][BoardConstants.QUEEN] = R.drawable.ic_pieces_california_bq;
        PIECES[CALIFORNIA][BoardConstants.BLACK][BoardConstants.KING] = R.drawable.ic_pieces_california_bk;
        PIECES[CALIFORNIA][BoardConstants.WHITE][BoardConstants.PAWN] = R.drawable.ic_pieces_california_wp;
        PIECES[CALIFORNIA][BoardConstants.WHITE][BoardConstants.KNIGHT] = R.drawable.ic_pieces_california_wn;
        PIECES[CALIFORNIA][BoardConstants.WHITE][BoardConstants.BISHOP] = R.drawable.ic_pieces_california_wb;
        PIECES[CALIFORNIA][BoardConstants.WHITE][BoardConstants.ROOK] = R.drawable.ic_pieces_california_wr;
        PIECES[CALIFORNIA][BoardConstants.WHITE][BoardConstants.QUEEN] = R.drawable.ic_pieces_california_wq;
        PIECES[CALIFORNIA][BoardConstants.WHITE][BoardConstants.KING] = R.drawable.ic_pieces_california_wk;

        PIECES[COMPANION][BoardConstants.BLACK][BoardConstants.PAWN] = R.drawable.ic_pieces_companion_bp;
        PIECES[COMPANION][BoardConstants.BLACK][BoardConstants.KNIGHT] = R.drawable.ic_pieces_companion_bn;
        PIECES[COMPANION][BoardConstants.BLACK][BoardConstants.BISHOP] = R.drawable.ic_pieces_companion_bb;
        PIECES[COMPANION][BoardConstants.BLACK][BoardConstants.ROOK] = R.drawable.ic_pieces_companion_br;
        PIECES[COMPANION][BoardConstants.BLACK][BoardConstants.QUEEN] = R.drawable.ic_pieces_companion_bq;
        PIECES[COMPANION][BoardConstants.BLACK][BoardConstants.KING] = R.drawable.ic_pieces_companion_bk;
        PIECES[COMPANION][BoardConstants.WHITE][BoardConstants.PAWN] = R.drawable.ic_pieces_companion_wp;
        PIECES[COMPANION][BoardConstants.WHITE][BoardConstants.KNIGHT] = R.drawable.ic_pieces_companion_wn;
        PIECES[COMPANION][BoardConstants.WHITE][BoardConstants.BISHOP] = R.drawable.ic_pieces_companion_wb;
        PIECES[COMPANION][BoardConstants.WHITE][BoardConstants.ROOK] = R.drawable.ic_pieces_companion_wr;
        PIECES[COMPANION][BoardConstants.WHITE][BoardConstants.QUEEN] = R.drawable.ic_pieces_companion_wq;
        PIECES[COMPANION][BoardConstants.WHITE][BoardConstants.KING] = R.drawable.ic_pieces_companion_wk;

        PIECES[CHESSNUT][BoardConstants.BLACK][BoardConstants.PAWN] = R.drawable.ic_pieces_chessnut_bp;
        PIECES[CHESSNUT][BoardConstants.BLACK][BoardConstants.KNIGHT] = R.drawable.ic_pieces_chessnut_bn;
        PIECES[CHESSNUT][BoardConstants.BLACK][BoardConstants.BISHOP] = R.drawable.ic_pieces_chessnut_bb;
        PIECES[CHESSNUT][BoardConstants.BLACK][BoardConstants.ROOK] = R.drawable.ic_pieces_chessnut_br;
        PIECES[CHESSNUT][BoardConstants.BLACK][BoardConstants.QUEEN] = R.drawable.ic_pieces_chessnut_bq;
        PIECES[CHESSNUT][BoardConstants.BLACK][BoardConstants.KING] = R.drawable.ic_pieces_chessnut_bk;
        PIECES[CHESSNUT][BoardConstants.WHITE][BoardConstants.PAWN] = R.drawable.ic_pieces_chessnut_wp;
        PIECES[CHESSNUT][BoardConstants.WHITE][BoardConstants.KNIGHT] = R.drawable.ic_pieces_chessnut_wn;
        PIECES[CHESSNUT][BoardConstants.WHITE][BoardConstants.BISHOP] = R.drawable.ic_pieces_chessnut_wb;
        PIECES[CHESSNUT][BoardConstants.WHITE][BoardConstants.ROOK] = R.drawable.ic_pieces_chessnut_wr;
        PIECES[CHESSNUT][BoardConstants.WHITE][BoardConstants.QUEEN] = R.drawable.ic_pieces_chessnut_wq;
        PIECES[CHESSNUT][BoardConstants.WHITE][BoardConstants.KING] = R.drawable.ic_pieces_chessnut_wk;

        PIECES[KOSAL][BoardConstants.BLACK][BoardConstants.PAWN] = R.drawable.ic_pieces_kosal_bp;
        PIECES[KOSAL][BoardConstants.BLACK][BoardConstants.KNIGHT] = R.drawable.ic_pieces_kosal_bn;
        PIECES[KOSAL][BoardConstants.BLACK][BoardConstants.BISHOP] = R.drawable.ic_pieces_kosal_bb;
        PIECES[KOSAL][BoardConstants.BLACK][BoardConstants.ROOK] = R.drawable.ic_pieces_kosal_br;
        PIECES[KOSAL][BoardConstants.BLACK][BoardConstants.QUEEN] = R.drawable.ic_pieces_kosal_bq;
        PIECES[KOSAL][BoardConstants.BLACK][BoardConstants.KING] = R.drawable.ic_pieces_kosal_bk;
        PIECES[KOSAL][BoardConstants.WHITE][BoardConstants.PAWN] = R.drawable.ic_pieces_kosal_wp;
        PIECES[KOSAL][BoardConstants.WHITE][BoardConstants.KNIGHT] = R.drawable.ic_pieces_kosal_wn;
        PIECES[KOSAL][BoardConstants.WHITE][BoardConstants.BISHOP] = R.drawable.ic_pieces_kosal_wb;
        PIECES[KOSAL][BoardConstants.WHITE][BoardConstants.ROOK] = R.drawable.ic_pieces_kosal_wr;
        PIECES[KOSAL][BoardConstants.WHITE][BoardConstants.QUEEN] = R.drawable.ic_pieces_kosal_wq;
        PIECES[KOSAL][BoardConstants.WHITE][BoardConstants.KING] = R.drawable.ic_pieces_kosal_wk;

        PIECES[STAUNTY][BoardConstants.BLACK][BoardConstants.PAWN] = R.drawable.ic_pieces_staunty_bp;
        PIECES[STAUNTY][BoardConstants.BLACK][BoardConstants.KNIGHT] = R.drawable.ic_pieces_staunty_bn;
        PIECES[STAUNTY][BoardConstants.BLACK][BoardConstants.BISHOP] = R.drawable.ic_pieces_staunty_bb;
        PIECES[STAUNTY][BoardConstants.BLACK][BoardConstants.ROOK] = R.drawable.ic_pieces_staunty_br;
        PIECES[STAUNTY][BoardConstants.BLACK][BoardConstants.QUEEN] = R.drawable.ic_pieces_staunty_bq;
        PIECES[STAUNTY][BoardConstants.BLACK][BoardConstants.KING] = R.drawable.ic_pieces_staunty_bk;
        PIECES[STAUNTY][BoardConstants.WHITE][BoardConstants.PAWN] = R.drawable.ic_pieces_staunty_wp;
        PIECES[STAUNTY][BoardConstants.WHITE][BoardConstants.KNIGHT] = R.drawable.ic_pieces_staunty_wn;
        PIECES[STAUNTY][BoardConstants.WHITE][BoardConstants.BISHOP] = R.drawable.ic_pieces_staunty_wb;
        PIECES[STAUNTY][BoardConstants.WHITE][BoardConstants.ROOK] = R.drawable.ic_pieces_staunty_wr;
        PIECES[STAUNTY][BoardConstants.WHITE][BoardConstants.QUEEN] = R.drawable.ic_pieces_staunty_wq;
        PIECES[STAUNTY][BoardConstants.WHITE][BoardConstants.KING] = R.drawable.ic_pieces_staunty_wk;
    }
}
