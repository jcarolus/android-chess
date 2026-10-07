package jwtc.android.chess.engine;

public interface EngineListener {
    void OnEngineMove(int move, int duckMove, int value);

    void OnEngineInfo(String message, float value);

    void OnEngineStarted();

    void OnEngineAborted();

    void OnEngineError();

    /**
     * Completed analysis, never an instruction to play a move. Evaluation is the last exact
     * principal-line score, from the searched side's perspective; null if none was received.
     */
    default void onAnalysisComplete(String fen, String bestMove, EngineEvaluation evaluation) {}
}
