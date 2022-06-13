package jwtc.android.chess.services;

import java.util.ArrayList;

import jwtc.chess.board.BoardConstants;

public abstract class ClockApi {
    protected long whiteRemaining = 0;
    protected long blackRemaining = 0;
    protected ArrayList<ClockListener> listeners = new ArrayList<>();

    public void addListener(ClockListener listener) {
        this.listeners.add(listener);
    }
    public void removeListener(ClockListener listener) {
        this.listeners.remove(listener);
    }

    public long getRemaining(int turn) {
        return turn == BoardConstants.WHITE ? whiteRemaining : blackRemaining;
    }

    public long getBlackRemaining() {
        return blackRemaining;
    }

    public long getWhiteRemaining() {
        return whiteRemaining;
    }

    protected void dispatchClockTime() {
        for (ClockListener listener : listeners) {
            listener.OnClockTime();
        }
    }
}
