package jwtc.android.chess.constants;

import jwtc.android.chess.R;
import jwtc.chess.board.BoardConstants;

public class PieceSets {
    public static int[][][] PIECES = new int[14][2][6]; // set:color:piece
    public static final int ALPHA = 0;
    public static final int MERIDA = 1;
    public static final int LEIPZIG = 2;
    public static final int CALIFORNIA = 3;
    public static final int COMPANION = 4;
    public static final int CHESSNUT = 5;
    public static final int KOSAL = 6;
    public static final int STAUNTY = 7;
    public static final int ELISA = 8;
    public static final int SHAPES = 9;
    public static final int FIRI = 10;
    public static final int KIWEN_SUWI = 11;
    public static final int SPATIAL = 12;
    public static final int CELTIC = 13;

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
        PIECES[MERIDA][BoardConstants.BLACK][BoardConstants.KNIGHT] =
                R.drawable.ic_pieces_merida_bn;
        PIECES[MERIDA][BoardConstants.BLACK][BoardConstants.BISHOP] =
                R.drawable.ic_pieces_merida_bb;
        PIECES[MERIDA][BoardConstants.BLACK][BoardConstants.ROOK] = R.drawable.ic_pieces_merida_br;
        PIECES[MERIDA][BoardConstants.BLACK][BoardConstants.QUEEN] = R.drawable.ic_pieces_merida_bq;
        PIECES[MERIDA][BoardConstants.BLACK][BoardConstants.KING] = R.drawable.ic_pieces_merida_bk;
        PIECES[MERIDA][BoardConstants.WHITE][BoardConstants.PAWN] = R.drawable.ic_pieces_merida_wp;
        PIECES[MERIDA][BoardConstants.WHITE][BoardConstants.KNIGHT] =
                R.drawable.ic_pieces_merida_wn;
        PIECES[MERIDA][BoardConstants.WHITE][BoardConstants.BISHOP] =
                R.drawable.ic_pieces_merida_wb;
        PIECES[MERIDA][BoardConstants.WHITE][BoardConstants.ROOK] = R.drawable.ic_pieces_merida_wr;
        PIECES[MERIDA][BoardConstants.WHITE][BoardConstants.QUEEN] = R.drawable.ic_pieces_merida_wq;
        PIECES[MERIDA][BoardConstants.WHITE][BoardConstants.KING] = R.drawable.ic_pieces_merida_wk;

        PIECES[LEIPZIG][BoardConstants.BLACK][BoardConstants.PAWN] =
                R.drawable.ic_pieces_leipzig_bp;
        PIECES[LEIPZIG][BoardConstants.BLACK][BoardConstants.KNIGHT] =
                R.drawable.ic_pieces_leipzig_bn;
        PIECES[LEIPZIG][BoardConstants.BLACK][BoardConstants.BISHOP] =
                R.drawable.ic_pieces_leipzig_bb;
        PIECES[LEIPZIG][BoardConstants.BLACK][BoardConstants.ROOK] =
                R.drawable.ic_pieces_leipzig_br;
        PIECES[LEIPZIG][BoardConstants.BLACK][BoardConstants.QUEEN] =
                R.drawable.ic_pieces_leipzig_bq;
        PIECES[LEIPZIG][BoardConstants.BLACK][BoardConstants.KING] =
                R.drawable.ic_pieces_leipzig_bk;
        PIECES[LEIPZIG][BoardConstants.WHITE][BoardConstants.PAWN] =
                R.drawable.ic_pieces_leipzig_wp;
        PIECES[LEIPZIG][BoardConstants.WHITE][BoardConstants.KNIGHT] =
                R.drawable.ic_pieces_leipzig_wn;
        PIECES[LEIPZIG][BoardConstants.WHITE][BoardConstants.BISHOP] =
                R.drawable.ic_pieces_leipzig_wb;
        PIECES[LEIPZIG][BoardConstants.WHITE][BoardConstants.ROOK] =
                R.drawable.ic_pieces_leipzig_wr;
        PIECES[LEIPZIG][BoardConstants.WHITE][BoardConstants.QUEEN] =
                R.drawable.ic_pieces_leipzig_wq;
        PIECES[LEIPZIG][BoardConstants.WHITE][BoardConstants.KING] =
                R.drawable.ic_pieces_leipzig_wk;

        PIECES[CALIFORNIA][BoardConstants.BLACK][BoardConstants.PAWN] =
                R.drawable.ic_pieces_california_bp;
        PIECES[CALIFORNIA][BoardConstants.BLACK][BoardConstants.KNIGHT] =
                R.drawable.ic_pieces_california_bn;
        PIECES[CALIFORNIA][BoardConstants.BLACK][BoardConstants.BISHOP] =
                R.drawable.ic_pieces_california_bb;
        PIECES[CALIFORNIA][BoardConstants.BLACK][BoardConstants.ROOK] =
                R.drawable.ic_pieces_california_br;
        PIECES[CALIFORNIA][BoardConstants.BLACK][BoardConstants.QUEEN] =
                R.drawable.ic_pieces_california_bq;
        PIECES[CALIFORNIA][BoardConstants.BLACK][BoardConstants.KING] =
                R.drawable.ic_pieces_california_bk;
        PIECES[CALIFORNIA][BoardConstants.WHITE][BoardConstants.PAWN] =
                R.drawable.ic_pieces_california_wp;
        PIECES[CALIFORNIA][BoardConstants.WHITE][BoardConstants.KNIGHT] =
                R.drawable.ic_pieces_california_wn;
        PIECES[CALIFORNIA][BoardConstants.WHITE][BoardConstants.BISHOP] =
                R.drawable.ic_pieces_california_wb;
        PIECES[CALIFORNIA][BoardConstants.WHITE][BoardConstants.ROOK] =
                R.drawable.ic_pieces_california_wr;
        PIECES[CALIFORNIA][BoardConstants.WHITE][BoardConstants.QUEEN] =
                R.drawable.ic_pieces_california_wq;
        PIECES[CALIFORNIA][BoardConstants.WHITE][BoardConstants.KING] =
                R.drawable.ic_pieces_california_wk;

        PIECES[COMPANION][BoardConstants.BLACK][BoardConstants.PAWN] =
                R.drawable.ic_pieces_companion_bp;
        PIECES[COMPANION][BoardConstants.BLACK][BoardConstants.KNIGHT] =
                R.drawable.ic_pieces_companion_bn;
        PIECES[COMPANION][BoardConstants.BLACK][BoardConstants.BISHOP] =
                R.drawable.ic_pieces_companion_bb;
        PIECES[COMPANION][BoardConstants.BLACK][BoardConstants.ROOK] =
                R.drawable.ic_pieces_companion_br;
        PIECES[COMPANION][BoardConstants.BLACK][BoardConstants.QUEEN] =
                R.drawable.ic_pieces_companion_bq;
        PIECES[COMPANION][BoardConstants.BLACK][BoardConstants.KING] =
                R.drawable.ic_pieces_companion_bk;
        PIECES[COMPANION][BoardConstants.WHITE][BoardConstants.PAWN] =
                R.drawable.ic_pieces_companion_wp;
        PIECES[COMPANION][BoardConstants.WHITE][BoardConstants.KNIGHT] =
                R.drawable.ic_pieces_companion_wn;
        PIECES[COMPANION][BoardConstants.WHITE][BoardConstants.BISHOP] =
                R.drawable.ic_pieces_companion_wb;
        PIECES[COMPANION][BoardConstants.WHITE][BoardConstants.ROOK] =
                R.drawable.ic_pieces_companion_wr;
        PIECES[COMPANION][BoardConstants.WHITE][BoardConstants.QUEEN] =
                R.drawable.ic_pieces_companion_wq;
        PIECES[COMPANION][BoardConstants.WHITE][BoardConstants.KING] =
                R.drawable.ic_pieces_companion_wk;

        PIECES[CHESSNUT][BoardConstants.BLACK][BoardConstants.PAWN] =
                R.drawable.ic_pieces_chessnut_bp;
        PIECES[CHESSNUT][BoardConstants.BLACK][BoardConstants.KNIGHT] =
                R.drawable.ic_pieces_chessnut_bn;
        PIECES[CHESSNUT][BoardConstants.BLACK][BoardConstants.BISHOP] =
                R.drawable.ic_pieces_chessnut_bb;
        PIECES[CHESSNUT][BoardConstants.BLACK][BoardConstants.ROOK] =
                R.drawable.ic_pieces_chessnut_br;
        PIECES[CHESSNUT][BoardConstants.BLACK][BoardConstants.QUEEN] =
                R.drawable.ic_pieces_chessnut_bq;
        PIECES[CHESSNUT][BoardConstants.BLACK][BoardConstants.KING] =
                R.drawable.ic_pieces_chessnut_bk;
        PIECES[CHESSNUT][BoardConstants.WHITE][BoardConstants.PAWN] =
                R.drawable.ic_pieces_chessnut_wp;
        PIECES[CHESSNUT][BoardConstants.WHITE][BoardConstants.KNIGHT] =
                R.drawable.ic_pieces_chessnut_wn;
        PIECES[CHESSNUT][BoardConstants.WHITE][BoardConstants.BISHOP] =
                R.drawable.ic_pieces_chessnut_wb;
        PIECES[CHESSNUT][BoardConstants.WHITE][BoardConstants.ROOK] =
                R.drawable.ic_pieces_chessnut_wr;
        PIECES[CHESSNUT][BoardConstants.WHITE][BoardConstants.QUEEN] =
                R.drawable.ic_pieces_chessnut_wq;
        PIECES[CHESSNUT][BoardConstants.WHITE][BoardConstants.KING] =
                R.drawable.ic_pieces_chessnut_wk;

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

        PIECES[STAUNTY][BoardConstants.BLACK][BoardConstants.PAWN] =
                R.drawable.ic_pieces_staunty_bp;
        PIECES[STAUNTY][BoardConstants.BLACK][BoardConstants.KNIGHT] =
                R.drawable.ic_pieces_staunty_bn;
        PIECES[STAUNTY][BoardConstants.BLACK][BoardConstants.BISHOP] =
                R.drawable.ic_pieces_staunty_bb;
        PIECES[STAUNTY][BoardConstants.BLACK][BoardConstants.ROOK] =
                R.drawable.ic_pieces_staunty_br;
        PIECES[STAUNTY][BoardConstants.BLACK][BoardConstants.QUEEN] =
                R.drawable.ic_pieces_staunty_bq;
        PIECES[STAUNTY][BoardConstants.BLACK][BoardConstants.KING] =
                R.drawable.ic_pieces_staunty_bk;
        PIECES[STAUNTY][BoardConstants.WHITE][BoardConstants.PAWN] =
                R.drawable.ic_pieces_staunty_wp;
        PIECES[STAUNTY][BoardConstants.WHITE][BoardConstants.KNIGHT] =
                R.drawable.ic_pieces_staunty_wn;
        PIECES[STAUNTY][BoardConstants.WHITE][BoardConstants.BISHOP] =
                R.drawable.ic_pieces_staunty_wb;
        PIECES[STAUNTY][BoardConstants.WHITE][BoardConstants.ROOK] =
                R.drawable.ic_pieces_staunty_wr;
        PIECES[STAUNTY][BoardConstants.WHITE][BoardConstants.QUEEN] =
                R.drawable.ic_pieces_staunty_wq;
        PIECES[STAUNTY][BoardConstants.WHITE][BoardConstants.KING] =
                R.drawable.ic_pieces_staunty_wk;

        PIECES[ELISA][BoardConstants.BLACK][BoardConstants.PAWN] = R.drawable.ic_pieces_elisa_bp;
        PIECES[ELISA][BoardConstants.BLACK][BoardConstants.KNIGHT] = R.drawable.ic_pieces_elisa_bn;
        PIECES[ELISA][BoardConstants.BLACK][BoardConstants.BISHOP] = R.drawable.ic_pieces_elisa_bb;
        PIECES[ELISA][BoardConstants.BLACK][BoardConstants.ROOK] = R.drawable.ic_pieces_elisa_br;
        PIECES[ELISA][BoardConstants.BLACK][BoardConstants.QUEEN] = R.drawable.ic_pieces_elisa_bq;
        PIECES[ELISA][BoardConstants.BLACK][BoardConstants.KING] = R.drawable.ic_pieces_elisa_bk;
        PIECES[ELISA][BoardConstants.WHITE][BoardConstants.PAWN] = R.drawable.ic_pieces_elisa_wp;
        PIECES[ELISA][BoardConstants.WHITE][BoardConstants.KNIGHT] = R.drawable.ic_pieces_elisa_wn;
        PIECES[ELISA][BoardConstants.WHITE][BoardConstants.BISHOP] = R.drawable.ic_pieces_elisa_wb;
        PIECES[ELISA][BoardConstants.WHITE][BoardConstants.ROOK] = R.drawable.ic_pieces_elisa_wr;
        PIECES[ELISA][BoardConstants.WHITE][BoardConstants.QUEEN] = R.drawable.ic_pieces_elisa_wq;
        PIECES[ELISA][BoardConstants.WHITE][BoardConstants.KING] = R.drawable.ic_pieces_elisa_wk;

        PIECES[SHAPES][BoardConstants.BLACK][BoardConstants.PAWN] =
                R.drawable.ic_pieces_shapes_bp;
        PIECES[SHAPES][BoardConstants.BLACK][BoardConstants.KNIGHT] =
                R.drawable.ic_pieces_shapes_bn;
        PIECES[SHAPES][BoardConstants.BLACK][BoardConstants.BISHOP] =
                R.drawable.ic_pieces_shapes_bb;
        PIECES[SHAPES][BoardConstants.BLACK][BoardConstants.ROOK] =
                R.drawable.ic_pieces_shapes_br;
        PIECES[SHAPES][BoardConstants.BLACK][BoardConstants.QUEEN] =
                R.drawable.ic_pieces_shapes_bq;
        PIECES[SHAPES][BoardConstants.BLACK][BoardConstants.KING] =
                R.drawable.ic_pieces_shapes_bk;
        PIECES[SHAPES][BoardConstants.WHITE][BoardConstants.PAWN] =
                R.drawable.ic_pieces_shapes_wp;
        PIECES[SHAPES][BoardConstants.WHITE][BoardConstants.KNIGHT] =
                R.drawable.ic_pieces_shapes_wn;
        PIECES[SHAPES][BoardConstants.WHITE][BoardConstants.BISHOP] =
                R.drawable.ic_pieces_shapes_wb;
        PIECES[SHAPES][BoardConstants.WHITE][BoardConstants.ROOK] =
                R.drawable.ic_pieces_shapes_wr;
        PIECES[SHAPES][BoardConstants.WHITE][BoardConstants.QUEEN] =
                R.drawable.ic_pieces_shapes_wq;
        PIECES[SHAPES][BoardConstants.WHITE][BoardConstants.KING] =
                R.drawable.ic_pieces_shapes_wk;

        PIECES[FIRI][BoardConstants.BLACK][BoardConstants.PAWN] =
                R.drawable.ic_pieces_firi_bp;
        PIECES[FIRI][BoardConstants.BLACK][BoardConstants.KNIGHT] =
                R.drawable.ic_pieces_firi_bn;
        PIECES[FIRI][BoardConstants.BLACK][BoardConstants.BISHOP] =
                R.drawable.ic_pieces_firi_bb;
        PIECES[FIRI][BoardConstants.BLACK][BoardConstants.ROOK] =
                R.drawable.ic_pieces_firi_br;
        PIECES[FIRI][BoardConstants.BLACK][BoardConstants.QUEEN] =
                R.drawable.ic_pieces_firi_bq;
        PIECES[FIRI][BoardConstants.BLACK][BoardConstants.KING] =
                R.drawable.ic_pieces_firi_bk;
        PIECES[FIRI][BoardConstants.WHITE][BoardConstants.PAWN] =
                R.drawable.ic_pieces_firi_wp;
        PIECES[FIRI][BoardConstants.WHITE][BoardConstants.KNIGHT] =
                R.drawable.ic_pieces_firi_wn;
        PIECES[FIRI][BoardConstants.WHITE][BoardConstants.BISHOP] =
                R.drawable.ic_pieces_firi_wb;
        PIECES[FIRI][BoardConstants.WHITE][BoardConstants.ROOK] =
                R.drawable.ic_pieces_firi_wr;
        PIECES[FIRI][BoardConstants.WHITE][BoardConstants.QUEEN] =
                R.drawable.ic_pieces_firi_wq;
        PIECES[FIRI][BoardConstants.WHITE][BoardConstants.KING] =
                R.drawable.ic_pieces_firi_wk;

        PIECES[KIWEN_SUWI][BoardConstants.BLACK][BoardConstants.PAWN] =
                R.drawable.ic_pieces_kiwen_suwi_bp;
        PIECES[KIWEN_SUWI][BoardConstants.BLACK][BoardConstants.KNIGHT] =
                R.drawable.ic_pieces_kiwen_suwi_bn;
        PIECES[KIWEN_SUWI][BoardConstants.BLACK][BoardConstants.BISHOP] =
                R.drawable.ic_pieces_kiwen_suwi_bb;
        PIECES[KIWEN_SUWI][BoardConstants.BLACK][BoardConstants.ROOK] =
                R.drawable.ic_pieces_kiwen_suwi_br;
        PIECES[KIWEN_SUWI][BoardConstants.BLACK][BoardConstants.QUEEN] =
                R.drawable.ic_pieces_kiwen_suwi_bq;
        PIECES[KIWEN_SUWI][BoardConstants.BLACK][BoardConstants.KING] =
                R.drawable.ic_pieces_kiwen_suwi_bk;
        PIECES[KIWEN_SUWI][BoardConstants.WHITE][BoardConstants.PAWN] =
                R.drawable.ic_pieces_kiwen_suwi_wp;
        PIECES[KIWEN_SUWI][BoardConstants.WHITE][BoardConstants.KNIGHT] =
                R.drawable.ic_pieces_kiwen_suwi_wn;
        PIECES[KIWEN_SUWI][BoardConstants.WHITE][BoardConstants.BISHOP] =
                R.drawable.ic_pieces_kiwen_suwi_wb;
        PIECES[KIWEN_SUWI][BoardConstants.WHITE][BoardConstants.ROOK] =
                R.drawable.ic_pieces_kiwen_suwi_wr;
        PIECES[KIWEN_SUWI][BoardConstants.WHITE][BoardConstants.QUEEN] =
                R.drawable.ic_pieces_kiwen_suwi_wq;
        PIECES[KIWEN_SUWI][BoardConstants.WHITE][BoardConstants.KING] =
                R.drawable.ic_pieces_kiwen_suwi_wk;

        PIECES[SPATIAL][BoardConstants.BLACK][BoardConstants.PAWN] =
                R.drawable.ic_pieces_spatial_bp;
        PIECES[SPATIAL][BoardConstants.BLACK][BoardConstants.KNIGHT] =
                R.drawable.ic_pieces_spatial_bn;
        PIECES[SPATIAL][BoardConstants.BLACK][BoardConstants.BISHOP] =
                R.drawable.ic_pieces_spatial_bb;
        PIECES[SPATIAL][BoardConstants.BLACK][BoardConstants.ROOK] =
                R.drawable.ic_pieces_spatial_br;
        PIECES[SPATIAL][BoardConstants.BLACK][BoardConstants.QUEEN] =
                R.drawable.ic_pieces_spatial_bq;
        PIECES[SPATIAL][BoardConstants.BLACK][BoardConstants.KING] =
                R.drawable.ic_pieces_spatial_bk;
        PIECES[SPATIAL][BoardConstants.WHITE][BoardConstants.PAWN] =
                R.drawable.ic_pieces_spatial_wp;
        PIECES[SPATIAL][BoardConstants.WHITE][BoardConstants.KNIGHT] =
                R.drawable.ic_pieces_spatial_wn;
        PIECES[SPATIAL][BoardConstants.WHITE][BoardConstants.BISHOP] =
                R.drawable.ic_pieces_spatial_wb;
        PIECES[SPATIAL][BoardConstants.WHITE][BoardConstants.ROOK] =
                R.drawable.ic_pieces_spatial_wr;
        PIECES[SPATIAL][BoardConstants.WHITE][BoardConstants.QUEEN] =
                R.drawable.ic_pieces_spatial_wq;
        PIECES[SPATIAL][BoardConstants.WHITE][BoardConstants.KING] =
                R.drawable.ic_pieces_spatial_wk;

        PIECES[CELTIC][BoardConstants.BLACK][BoardConstants.PAWN] =
                R.drawable.ic_pieces_celtic_bp;
        PIECES[CELTIC][BoardConstants.BLACK][BoardConstants.KNIGHT] =
                R.drawable.ic_pieces_celtic_bn;
        PIECES[CELTIC][BoardConstants.BLACK][BoardConstants.BISHOP] =
                R.drawable.ic_pieces_celtic_bb;
        PIECES[CELTIC][BoardConstants.BLACK][BoardConstants.ROOK] =
                R.drawable.ic_pieces_celtic_br;
        PIECES[CELTIC][BoardConstants.BLACK][BoardConstants.QUEEN] =
                R.drawable.ic_pieces_celtic_bq;
        PIECES[CELTIC][BoardConstants.BLACK][BoardConstants.KING] =
                R.drawable.ic_pieces_celtic_bk;
        PIECES[CELTIC][BoardConstants.WHITE][BoardConstants.PAWN] =
                R.drawable.ic_pieces_celtic_wp;
        PIECES[CELTIC][BoardConstants.WHITE][BoardConstants.KNIGHT] =
                R.drawable.ic_pieces_celtic_wn;
        PIECES[CELTIC][BoardConstants.WHITE][BoardConstants.BISHOP] =
                R.drawable.ic_pieces_celtic_wb;
        PIECES[CELTIC][BoardConstants.WHITE][BoardConstants.ROOK] =
                R.drawable.ic_pieces_celtic_wr;
        PIECES[CELTIC][BoardConstants.WHITE][BoardConstants.QUEEN] =
                R.drawable.ic_pieces_celtic_wq;
        PIECES[CELTIC][BoardConstants.WHITE][BoardConstants.KING] =
                R.drawable.ic_pieces_celtic_wk;
    }
}
