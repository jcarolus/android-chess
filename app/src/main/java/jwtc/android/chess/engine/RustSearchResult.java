package jwtc.android.chess.engine;

/** White-perspective score; +/-100000 denotes mate without a distance. */
public final class RustSearchResult {
    public final long searchId;
    public final String bestMove;
    public final int score;
    public final int completedDepth;
    public final long nodes;
    public final boolean hasEvaluation;
    public final boolean interrupted;

    public RustSearchResult(long searchId, String bestMove, int score, int completedDepth,
                            long nodes, boolean hasEvaluation, boolean interrupted) {
        this.searchId = searchId;
        this.bestMove = bestMove;
        this.score = score;
        this.completedDepth = completedDepth;
        this.nodes = nodes;
        this.hasEvaluation = hasEvaluation;
        this.interrupted = interrupted;
    }
}
