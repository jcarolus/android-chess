package jwtc.android.chess.engine;

public interface EngineListener {
    void OnEngineMove(int move, int duckMove, int value);

    void OnEngineInfo(String message, float value);

    void OnEngineStarted();

    void OnEngineAborted();

    void OnEngineError();
}
