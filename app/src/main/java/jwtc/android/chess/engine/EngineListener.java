package jwtc.android.chess.engine;

public interface EngineListener {
    void OnEngineMove(int move, int duckMove, int value);

    void OnEngineInfo(String message, float value);

    void OnEngineStarted();

    void OnEngineAborted();

    void OnEngineError();

    /** Completed analysis result in UCI notation; never an instruction to play a move. */
    default void onAnalysisComplete(String fen, String bestMove) {}
}
