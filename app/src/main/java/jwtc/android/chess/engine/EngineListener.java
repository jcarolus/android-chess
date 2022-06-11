package jwtc.android.chess.engine;

public interface EngineListener {
    void OnEngineMove(int move);
    void OnInfo(String message);
    void OnEngineStarted();
    void OnEngineAborted();
    void OnEngineError();
}
