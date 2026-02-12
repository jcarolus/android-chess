package jwtc.android.chess.services;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.util.ArrayList;

import jwtc.chess.board.BoardConstants;

public class LocalClockApi {
    protected static final String TAG = "LocalClockApi";

    protected long whiteRemaining = 0;
    protected long blackRemaining = 0;
    protected ArrayList<ClockListener> listeners = new ArrayList<>();

    protected long increment = 0;
    protected long lastMeasureTime = 0;
    protected int currentTurn = 1;
    protected boolean clockIsConfigured = false;

    private Thread clockThread = null;

    protected Handler updateHandler = new Handler(Looper.getMainLooper()) {
        // @Override
        public void handleMessage(Message msg) {
            dispatchClockTime();
            super.handleMessage(msg);
        }
    };

    public void addListener(ClockListener listener) {
        this.listeners.add(listener);
    }

    public void removeListener(ClockListener listener) {
        this.listeners.remove(listener);
    }

    public long getRemaining(int turn) {
        return turn == BoardConstants.WHITE ? getWhiteRemaining() : getBlackRemaining();
    }

    public void startClock(long increment, long whiteRemaining, long blackRemaining, int turn, long startTime) {
        Log.d(TAG, "startClock " + increment + " " + whiteRemaining + " " + blackRemaining + " " + turn + " " + startTime);

        this.increment = increment;
        this.whiteRemaining = whiteRemaining;
        this.blackRemaining = blackRemaining;
        this.currentTurn = turn;
        this.clockIsConfigured = whiteRemaining > 0 && blackRemaining > 0;

        this.lastMeasureTime = startTime;

        if (startTime > 0 && clockThread == null) {
            clockThread = new Thread(new RunnableImp());
            clockThread.start();
        }
    }

    public void stopClock() {
        if (clockThread != null) {
            synchronized (this) {
                clockThread.interrupt();
            }
            clockThread = null;
        }
    }

    public long getBlackRemaining() {
        if (currentTurn == BoardConstants.BLACK) {
            final long currentTime = System.currentTimeMillis();
            final long usedMillies = (currentTime - lastMeasureTime);
            final long remaining = blackRemaining - usedMillies;
            return remaining >= 0 ? remaining : 0;
        } else {
            return blackRemaining >= 0 ? blackRemaining : 0;
        }
    }

    public long getWhiteRemaining() {
        if (currentTurn == BoardConstants.WHITE) {
            final long currentTime = System.currentTimeMillis();
            final long usedMillies = (currentTime - lastMeasureTime);
            final long remaining = whiteRemaining - usedMillies;
            return remaining >= 0 ? remaining : 0;
        }
        return whiteRemaining >= 0 ? whiteRemaining : 0;
    }

    public String getBlackRemainingTime() {
        return timeToString(getRemaining(BoardConstants.BLACK));
    }

    public String getWhiteRemainingTime() {
        return timeToString(getRemaining(BoardConstants.WHITE));
    }

    public long getLastMeasureTime() {
        return lastMeasureTime;
    }

    public void switchTurn(int newTurn) {
        if (newTurn != currentTurn) {
            Log.d(TAG, "switchTurn " + newTurn);
            currentTurn = newTurn;
            final long currentTime = System.currentTimeMillis();
            final long usedMillies = (currentTime - lastMeasureTime);

            if (newTurn == BoardConstants.BLACK) {
                whiteRemaining -= usedMillies;
                whiteRemaining += increment;
            } else {
                blackRemaining -= usedMillies;
                blackRemaining += increment;
            }

            lastMeasureTime = currentTime;
        }
    }

    public boolean isClockConfigured() {
        return clockIsConfigured;
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

    private class RunnableImp implements Runnable {
        @Override
        public void run() {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Message m = new Message();
                    m.what = 0;
                    updateHandler.sendMessage(m);

                    Thread.sleep(500);
                }
            } catch (InterruptedException e) {
                Log.d(TAG, "Runnable interrupted");
            }
        }
    }
}
