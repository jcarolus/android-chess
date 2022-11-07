package jwtc.android.chess.engine;

public interface EngineListener {
    void OnEngineMove(int move, int duckMove);
    void OnEngineInfo(String message);
    void OnEngineStarted();
    void OnEngineAborted();
    void OnEngineError();
}
