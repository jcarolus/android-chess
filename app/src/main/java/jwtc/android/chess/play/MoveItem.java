package jwtc.android.chess.play;

public class MoveItem {
    public String nr;
    public String move;
    public String annotation;
    public int turn;

    public MoveItem(String nr, String move, String annotation, int turn) {
        this.nr = nr;
        this.move = move;
        this.annotation = annotation;
        this.turn = turn;
    }
}