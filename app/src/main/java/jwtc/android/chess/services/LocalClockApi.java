package jwtc.android.chess.services;
import android.os.Handler;
import android.os.Message;

import jwtc.chess.board.BoardConstants;

public class LocalClockApi extends ClockApi {
    protected static final String TAG = "LocalClockApi";

    protected long increment = 0;
    protected long startTime = 0;

    protected Handler updateHandler = new Handler() {
        // @Override
        public void handleMessage(Message msg) {
            dispatchClockTime();
            super.handleMessage(msg);
        }
    };

    public void setTimer(long given, long increment) {
        this.increment = increment;
        whiteRemaining = given;
        blackRemaining = given;
        startTime = 0;
    }

    public void switchTurn(int newTurn) {
        final long currentTime = System.currentTimeMillis();
        final long usedMillies = (currentTime - startTime);

        if (newTurn == BoardConstants.BLACK) {
            whiteRemaining -= usedMillies;
            whiteRemaining += increment;
        } else {
            blackRemaining -= usedMillies;
            blackRemaining += increment;
        }

        startTime = currentTime;
    }

    private class RunnableImp implements Runnable {
        @Override
        public void run() {
            Message m = new Message();
            updateHandler.sendMessage(m);
        }
    }
}
