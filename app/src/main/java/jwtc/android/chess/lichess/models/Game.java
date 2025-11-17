package jwtc.android.chess.lichess.models;

public class Game {
    public String fullId;
    public String gameId;
    public String fen;
    public String color;
    public String lastMove;
    public String source;
    public Status status;
    public Variant variant;
    public String speed;
    public String perf;
    public boolean rated;
    public boolean hasMoved;
    public Opponent opponent;
    public boolean isMyTurn;
    public int secondsLeft;
    public Compat compat;
    public String id;
}