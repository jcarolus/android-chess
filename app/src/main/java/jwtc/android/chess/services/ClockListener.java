package jwtc.android.chess.services;

public interface ClockListener {
    void OnClockTime();
    void OnTimeWarning(int turn, long remainingMillies);
}
