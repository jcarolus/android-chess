package jwtc.android.chess.services;

public interface GameListener {
    void OnMove(int move);
    void OnDuckMove(int duckMove);
    void OnState();
}
