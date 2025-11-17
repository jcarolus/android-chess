package jwtc.android.chess.lichess.models;

public class State {
    public String type;  // "gameState"
    public String moves;
    public long wtime;
    public long btime;
    public long winc;
    public long binc;
    public String status;
}