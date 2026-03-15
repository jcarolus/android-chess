package jwtc.android.chess.lichess.models;

import java.util.List;

public class PuzzleGame {
    public String clock;
    public String id;
    public Perf perf;
    public String pgn;
    public List<PuzzlePlayer> players;
    public boolean rated;
}
