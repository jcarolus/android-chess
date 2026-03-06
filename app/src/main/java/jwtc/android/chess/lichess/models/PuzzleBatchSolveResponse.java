package jwtc.android.chess.lichess.models;

import java.util.List;

public class PuzzleBatchSolveResponse {
    public List<PuzzleAndGame> puzzles;
    public PuzzleGlicko glicko;
    public List<PuzzleBatchSolveRound> rounds;
}
