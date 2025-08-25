package jwtc.android.chess.constants;

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
}
