package jwtc.chess;

public class PGNEntry {

    public String sMove, sAnnotation;
    public int move, duckMove;
    public int finalState = -1;

    /** Recorded remaining time after this move, or null when unavailable. */
    public Long getClockMillis() {
        return PgnComment.clockMillis(sAnnotation);
    }

    public PGNEntry(String sM, String sA, int move, int duckMove) {
        sMove = sM;
        sAnnotation = sA;
        this.move = move;
        this.duckMove = duckMove;
    }
}
