package jwtc.android.chess.constants;

import jwtc.android.chess.R;
import jwtc.chess.board.BoardConstants;

public class Piece {

    // @TODO localized strings.xml resource based
    public static String toString(int piece) {
        switch (piece) {
            case BoardConstants.PAWN:
                return "pawn";
            case BoardConstants.KNIGHT:
                return "Knight";
            case BoardConstants.BISHOP:
                return "Bishop";
            case BoardConstants.ROOK:
                return "Rook";
            case BoardConstants.QUEEN:
                return "Queen";
            case BoardConstants.KING:
                return "King";
            default:
                return "";
        }
    }

    public static int toResource(int piece) {
        switch (piece) {
            case BoardConstants.PAWN:
                return R.string.piece_pawn;
            case BoardConstants.KNIGHT:
                return R.string.piece_knight;
            case BoardConstants.BISHOP:
                return R.string.piece_bishop;
            case BoardConstants.ROOK:
                return R.string.piece_rook;
            case BoardConstants.QUEEN:
                return R.string.piece_queen;
            case BoardConstants.KING:
                return R.string.piece_king;
            default:
                return 0;
        }
    }
}
