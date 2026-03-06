package jwtc.android.chess.lichess.models;

import java.util.List;

public class Puzzle {
    public String id;
    public int initialPly;
    public int plays;
    public int rating;
    public List<String> solution;
    public List<String> themes;
}
