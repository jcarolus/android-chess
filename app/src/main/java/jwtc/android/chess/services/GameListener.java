package jwtc.android.chess.services;

/**
 * Observes changes made by {@link GameApi}.
 *
 * <p>Callbacks are invoked after the corresponding operation has completed. Implementations may
 * inspect {@link GameApi} or the underlying game state to determine the resulting position. They
 * are notifications only and do not request or authorize game changes.</p>
 */
public interface GameListener {
    default void onMoveApplied(int move) {}

    default void onDuckMoveApplied(int duckMove) {}

    default void OnState() {}

    default void onIllegalMoveAttempted() {}

    default void onHistoryPositionChanged(int boardNumber) {}

    default void onNewGameStarted(int variant) {}

    default void onGameLoaded() {}

    default void onGameResumed() {}

    default void onPlayerResigned(int color) {}

    default void onDrawAgreed() {}

    default void onPlayerForfeitedOnTime(int color) {}
}
