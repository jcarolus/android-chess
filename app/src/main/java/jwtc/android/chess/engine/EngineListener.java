package jwtc.android.chess.engine;

public interface EngineListener {
    void OnMove(int move);
    void OnInfo(String message);
    void OnStarted();
    void OnAborted();
    void OnError();
}
