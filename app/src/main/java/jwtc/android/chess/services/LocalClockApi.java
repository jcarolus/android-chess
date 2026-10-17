package jwtc.android.chess.services;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;

import jwtc.chess.board.BoardConstants;

public class LocalClockApi implements GameListener {
    protected static final String TAG = "LocalClockApi";
    // Fire warnings this much before the clock actually reaches a threshold, so the
    // sound/speech lands on time rather than after the mark has already passed.
    private static final long TIME_WARNING_LEAD_MILLIES = 1000L;
    private static final long[] TIME_WARNING_THRESHOLDS_MILLIES = {
        30 * 60 * 1000L,
        20 * 60 * 1000L,
        10 * 60 * 1000L,
        5 * 60 * 1000L,
        2 * 60 * 1000L,
        60 * 1000L,
        30 * 1000L,
        15 * 1000L,
        10 * 1000L,
        5 * 1000L
    };

    protected ArrayList<ClockListener> listeners = new ArrayList<>();

    private final LocalClockState clock = new LocalClockState();
    protected int whiteWarningMask = 0;
    protected int blackWarningMask = 0;
    protected long whitePreviousWarningCheck = -1;
    protected long blackPreviousWarningCheck = -1;

    private final GameApi gameApi;
    private boolean updatesEnabled;

    public LocalClockApi(GameApi gameApi) {
        this.gameApi = gameApi;
        this.gameApi.addListener(this);
    }

    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private final Runnable updateClock = new Runnable() {
        @Override
        public void run() {
            if (!updatesEnabled) return;
            checkAndDispatchTimeWarning();
            dispatchClockTime();
            if (updatesEnabled) updateHandler.postDelayed(this, 500);
        }
    };

    public void addListener(ClockListener listener) {
        this.listeners.add(listener);
    }

    public void removeListener(ClockListener listener) {
        this.listeners.remove(listener);
    }

    public long getRemaining(int turn) {
        return clock.remaining(turn, SystemClock.elapsedRealtime());
    }

    public void startClock(long increment, long whiteRemaining, long blackRemaining, int turn, long startTime) {
        Log.d(TAG, "startClock " + increment + " " + whiteRemaining + " " + blackRemaining + " " + turn + " " + startTime);

        updateHandler.removeCallbacks(updateClock);
        long elapsed = startTime > 0 ? Math.max(0, System.currentTimeMillis() - startTime) : 0;
        clock.start(increment, whiteRemaining, blackRemaining, turn,
            SystemClock.elapsedRealtime() - elapsed, startTime > 0);
        whiteWarningMask = 0;
        blackWarningMask = 0;
        whitePreviousWarningCheck = getWhiteRemaining() - TIME_WARNING_LEAD_MILLIES;
        blackPreviousWarningCheck = getBlackRemaining() - TIME_WARNING_LEAD_MILLIES;
        updatesEnabled = startTime > 0;
        if (updatesEnabled) updateHandler.post(updateClock);
    }

    public void stopClock() {
        clock.stop(SystemClock.elapsedRealtime());
        updatesEnabled = false;
        updateHandler.removeCallbacks(updateClock);
    }

    public int getActivePlayer() {
        return clock.activePlayer();
    }

    public long getBlackRemaining() {
        return getRemaining(BoardConstants.BLACK);
    }

    public long getWhiteRemaining() {
        return getRemaining(BoardConstants.WHITE);
    }

    public String getBlackRemainingTime() {
        return timeToString(getRemaining(BoardConstants.BLACK));
    }

    public String getWhiteRemainingTime() {
        return timeToString(getRemaining(BoardConstants.WHITE));
    }

    public boolean isClockConfigured() {
        return clock.isConfigured();
    }

    protected void switchTurn() {
        clock.completeTurn(SystemClock.elapsedRealtime());
        int player = clock.activePlayer();
        updatePreviousWarningCheck(player, getRemaining(player) - TIME_WARNING_LEAD_MILLIES);
    }

    protected String timeToString(final long millies) {
        int seconds = (int) (millies / 1000);
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

    protected void dispatchTimeWarning(int turn, long remainingMillies) {
        for (ClockListener listener : listeners) {
            listener.OnTimeWarning(turn, remainingMillies);
        }
    }

    protected void checkAndDispatchTimeWarning() {
        if (!clock.isRunning()) {
            return;
        }

        int turn = clock.activePlayer();
        // Bring the warning forward by the lead time: both the threshold crossing and the
        // value handed to feedbackTimeWarning shift together, so the spoken label stays correct.
        long remaining = getRemaining(turn) - TIME_WARNING_LEAD_MILLIES;
        long previous = getPreviousWarningCheck(turn);

        if (previous < 0) {
            updatePreviousWarningCheck(turn, remaining);
            return;
        }

        for (int i = 0; i < TIME_WARNING_THRESHOLDS_MILLIES.length; i++) {
            long threshold = TIME_WARNING_THRESHOLDS_MILLIES[i];
            int warningBit = 1 << i;
            if (previous > threshold && remaining <= threshold && (getWarningMask(turn) & warningBit) == 0) {
                setWarningMask(turn, getWarningMask(turn) | warningBit);
                dispatchTimeWarning(turn, remaining);
            }
        }

        updatePreviousWarningCheck(turn, remaining);
    }

    protected long getPreviousWarningCheck(int turn) {
        return turn == BoardConstants.WHITE ? whitePreviousWarningCheck : blackPreviousWarningCheck;
    }

    protected void updatePreviousWarningCheck(int turn, long remaining) {
        if (turn == BoardConstants.WHITE) {
            whitePreviousWarningCheck = remaining;
        } else {
            blackPreviousWarningCheck = remaining;
        }
    }

    protected int getWarningMask(int turn) {
        return turn == BoardConstants.WHITE ? whiteWarningMask : blackWarningMask;
    }

    protected void setWarningMask(int turn, int value) {
        if (turn == BoardConstants.WHITE) {
            whiteWarningMask = value;
        } else {
            blackWarningMask = value;
        }
    }

    @Override
    public void onMoveApplied(int move) {
        // Account for play at the end of whichever line is selected.
        if (this.gameApi.isAtLineEnd()) {
            int turn = clock.activePlayer();
            if (this.gameApi.getTurn() != turn) {
                switchTurn();
            }
        }
    }

    @Override
    public void onDuckMoveApplied(int duckMove) {
        if (gameApi.isAtLineEnd()) {
            switchTurn();
        }
    }
}
