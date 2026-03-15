package jwtc.android.chess.lichess.models;

import java.util.List;

public class PuzzleBatchSolveRequest {
    public List<Solution> solutions;

    public static class Solution {
        public String id;
        public boolean win;
        public boolean rated;
    }
}
