package jwtc.android.chess.lichess.models;

public class GameFull {

    public String id;
    public Variant variant;
    public String speed;
    public Perf perf;
    public boolean rated;
    public long createdAt;

    public Player white;
    public Player black;

    public String initialFen;
    public Clock clock;

    public String type;  // will be "gameFull"

    public State state;
}

