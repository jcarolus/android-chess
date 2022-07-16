package jwtc.android.chess.services;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import jwtc.chess.board.BoardConstants;

public class LocalClockApi extends ClockApi {
    protected static final String TAG = "LocalClockApi";

    protected long increment = 0;
    protected long lastMeasureTime = 0;
    protected int currentTurn = 1;

    private Thread clockThread = null;

    protected Handler updateHandler = new Handler() {
        // @Override
        public void handleMessage(Message msg) {
            dispatchClockTime();
            super.handleMessage(msg);
        }
    };

    public void startClock(long increment, long whiteRemaining, long blackRemaining, int turn, long startTime) {
        Log.d(TAG, "startClock " + increment + " " + whiteRemaining + " " + blackRemaining + " " + turn);

        this.increment = increment;
        this.whiteRemaining = whiteRemaining;
        this.blackRemaining = blackRemaining;
        this.currentTurn = turn;

        this.lastMeasureTime = startTime;

        if (startTime > 0 && clockThread == null) {
            clockThread = new Thread(new RunnableImp());
            clockThread.start();
        }
    }

    public void stopClock() {
        if (clockThread != null) {
            try {
                synchronized (this) {
                    clockThread.interrupt();
                }
                clockThread.join();
            } catch (InterruptedException e) {
                Log.d(TAG, "stopClock interrupted");
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

    private class RunnableImp implements Runnable {
        @Override
        public void run() {
            try {
                while (true) {
                    Message m = new Message();
                    m.what = 0;
                    updateHandler.sendMessage(m);

                    Thread.sleep(500);
                }
            }
            catch (InterruptedException e) {
                Log.d(TAG, "Runnable interrupted");
            }
        }
    }
}
