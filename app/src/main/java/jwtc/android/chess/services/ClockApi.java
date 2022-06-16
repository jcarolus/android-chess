package jwtc.android.chess.services;

import android.util.Log;

import java.util.ArrayList;

import jwtc.chess.board.BoardConstants;

public abstract class ClockApi {
    private static final String TAG = "ClockApi";
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

    public abstract long getBlackRemaining();

    public abstract long getWhiteRemaining();

    public String getBlackRemainingTime() {
        return timeToString(getBlackRemaining());
    }

    public String getWhiteRemainingTime() {
        return timeToString(getWhiteRemaining());
    }

    protected String timeToString(final long millies) {
        int seconds = (int)(millies / 1000);
        if (seconds >= 0) {
            return String.format("%d:%02d", (int) (Math.floor(seconds / 60)), seconds % 60);
        } else {
            return "-:-";
        }
    }

    protected void dispatchClockTime() {
        for (ClockListener listener : listeners) {
            listener.OnClockTime();
        }
    }
}
