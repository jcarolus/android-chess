package jwtc.android.chess.engine;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.util.ArrayList;

import jwtc.chess.Move;
import jwtc.chess.Pos;

public abstract class EngineApi {
    private static final String TAG = "EngineApi";

    public static final int LEVEL_TIME = 1;
    public static final int LEVEL_PLY = 2;

    protected static final int MSG_MOVE = 1;
    protected static final int MSG_INFO = 2;
    protected static final int MSG_ERROR = 3;
    protected int msecs = 0;
    protected int ply = 0;
    protected boolean quiescentSearchOn = true;

    protected ArrayList<EngineListener> listeners = new ArrayList<>();

    protected Handler updateHandler = new Handler() {
        // @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_MOVE) {
                int move = msg.getData().getInt("move");
                int duckMove = msg.getData().getInt("duckMove");
                Log.d(TAG, "handleMessage MOVE " + Move.toDbgString(move) + " :: " + Pos.toString(duckMove));
                for (EngineListener listener: listeners) {
                    listener.OnEngineMove(move, duckMove);
                }

            } else if (msg.what == MSG_INFO) {
                String message = msg.getData().getString("message");
                // Log.d(TAG, "handleMessage INFO " + message);
                for (EngineListener listener: listeners) {
                    listener.OnEngineInfo(message);
                }
            } else if (msg.what == MSG_ERROR) {
                for (EngineListener listener: listeners) {
                    listener.OnEngineError();
                }
            }
            super.handleMessage(msg);
        }
    };

    public void sendMessageFromThread(String sText) {
        Message m = new Message();
        Bundle b = new Bundle();
        m.what = MSG_INFO;
        b.putString("message", sText);
        m.setData(b);
        updateHandler.sendMessage(m);
    }

    public void sendMoveMessageFromThread(int move, int duckMove) {
        Message m = new Message();
        Bundle b = new Bundle();
        b.putInt("move", move);
        b.putInt("duckMove", duckMove);
        m.what = MSG_MOVE;
        m.setData(b);
        updateHandler.sendMessage(m);
    }

    public void sendErrorMessageFromThread() {
        Message m = new Message();
        m.what = MSG_ERROR;
        updateHandler.sendMessage(m);
    }

    abstract public void play();
    abstract public boolean isReady();
    abstract public void abort();
    abstract public void destroy();

    public void setMsecs(int msecs) {
        Log.d(TAG, "setMsecs " + msecs);
        this.msecs = msecs;
        this.ply = 0;
    }

    public void setPly(int ply) {
        Log.d(TAG, "setPly " + ply);
        this.ply = ply;
        this.msecs = 0;
    }

    public void setQuiescentSearchOn(boolean on) {
        this.quiescentSearchOn = on;
    }

    public void addListener(EngineListener listener) {
        this.listeners.add(listener);
    }

    public void removeListener(EngineListener listener) {
        this.listeners.remove(listener);
    }
}
