package jwtc.chess;

/**
 * One legal move of a Position as the board reports it: the packed {@link Move}, its coordinate
 * notation (Chess 960 castling written king to rook square) and its SAN without a Duck placement.
 */
public final class LegalMove {
    public final int packed;
    public final String coordinate, san;

    public LegalMove(int packed, String coordinate, String san) {
        this.packed = packed;
        this.coordinate = coordinate;
        this.san = san;
    }
}
