package jwtc.android.chess.lichess.models;

public class Challenge {
    public String id;
    public String url;
    public String status;
    public Player challenger;
    public Player destUser;
    public Variant variant;
    public boolean rated;
    public String speed;
    public TimeControl timeControl;
    public String color;
    public String finalColor;
    public Perf perf;
    public String direction;
}
