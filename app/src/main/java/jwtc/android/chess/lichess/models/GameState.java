package jwtc.android.chess.lichess.models;

public class GameState {

    public String type;
    public String moves;

    public long wtime;
    public long btime;

    public long winc;
    public long binc;

    public boolean wdraw;
    public boolean bdraw;

    public boolean wtakeback;
    public boolean btakeback;

    public String status;
}