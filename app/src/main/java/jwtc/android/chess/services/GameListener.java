package jwtc.android.chess.services;

/**
 * Observes changes made by {@link GameApi}.
 *
 * <p>Callbacks are invoked after the corresponding operation has completed. Implementations may
 * inspect {@link GameApi} or the underlying game state to determine the resulting position. They
 * are notifications only and do not request or authorize game changes.</p>
 */
public interface GameListener {
    void onMoveApplied(int move);

    void onDuckMoveApplied(int duckMove);

    void OnState();

    void onIllegalMoveAttempted();

    void onHistoryPositionChanged(int boardNumber);

    void onNewGameStarted(int variant);

    void onGameLoaded();

    void onPlayerResigned(int color);

    void onDrawAgreed();

    void onPlayerForfeitedOnTime(int color);
}
