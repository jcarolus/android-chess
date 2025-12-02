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
            case BoardConstants.DUCK:
                return R.string.piece_duck;
            default:
                return 0;
        }
    }

    public static String toPromoUCI(int piece) {
        switch (piece) {
            case BoardConstants.KNIGHT:
                return "n";
            case BoardConstants.BISHOP:
                return "b";
            case BoardConstants.ROOK:
                return "r";
            case BoardConstants.QUEEN:
                return "q";
            default:
                return "";
        }
    }

    public static int fromUCIPromo(String piece) {
        if (piece.equals("n")) {
            return BoardConstants.KNIGHT;
        } else if (piece.equals("b")) {
            return BoardConstants.BISHOP;
        } else if (piece.equals("r")) {
            return BoardConstants.ROOK;
        }
        return BoardConstants.QUEEN;
    }
}
