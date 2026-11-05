package jwtc.android.chess.services;

import jwtc.chess.board.BoardConstants;

/** Clock accounting independent of the displayed board and the UI refresh rate. */
final class LocalClockState {
    private final long[] remaining = new long[2];
    private int activePlayer;
    private long increment;
    private long measuredAt;
    private boolean configured;
    private boolean running;

    void start(long increment, long white, long black, int player, long now, boolean running) {
        remaining[BoardConstants.WHITE] = Math.max(0, white);
        remaining[BoardConstants.BLACK] = Math.max(0, black);
        activePlayer = player;
        this.increment = increment;
        measuredAt = now;
        configured = white > 0 && black > 0;
        this.running = configured && running;
    }

    long remaining(int player, long now) {
        long elapsed = running && player == activePlayer ? Math.max(0, now - measuredAt) : 0;
        return Math.max(0, remaining[player] - elapsed);
    }

    void completeTurn(long now) {
        if (!running) {
            return;
        }
        remaining[activePlayer] = remaining(activePlayer, now);
        measuredAt = now;
        if (remaining[activePlayer] == 0) {
            running = false;
            return;
        }
        remaining[activePlayer] += increment;
        activePlayer =
                activePlayer == BoardConstants.WHITE ? BoardConstants.BLACK : BoardConstants.WHITE;
    }

    /** Hands the clock to {@code player} without completing a turn or awarding increment. */
    void setActivePlayer(int player, long now) {
        if (player == activePlayer) {
            return;
        }
        remaining[activePlayer] = remaining(activePlayer, now);
        measuredAt = now;
        activePlayer = player;
    }

    void stop(long now) {
        remaining[activePlayer] = remaining(activePlayer, now);
        measuredAt = now;
        running = false;
    }

    int activePlayer() {
        return activePlayer;
    }

    boolean isConfigured() {
        return configured;
    }

    boolean isRunning() {
        return running;
    }
}
