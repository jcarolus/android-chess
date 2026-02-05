package jwtc.android.chess.play;

public class MoveItem {
    public String nr;
    public String sMove;
    public String annotation;
    public int move;
    public int turn;

    public MoveItem(String nr, String sMove, int move, String annotation, int turn) {
        this.nr = nr;
        this.sMove = sMove;
        this.move = move;
        this.annotation = annotation;
        this.turn = turn;
    }
}