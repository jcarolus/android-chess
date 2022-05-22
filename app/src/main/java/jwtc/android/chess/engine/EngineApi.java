package jwtc.android.chess.engine;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import java.util.ArrayList;

public abstract class EngineApi {

    protected static final int MSG_MOVE = 1;
    protected static final int MSG_INFO = 2;

    protected ArrayList<EngineListener> listeners = new ArrayList<>();

    protected Handler updateHandler = new Handler() {
        // @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_MOVE) {
                int move = msg.getData().getInt("move");
                for (EngineListener listener: listeners) {
                    listener.OnMove(move);
                }

            } else if (msg.what == MSG_INFO) {
                String message = msg.getData().getString("message");
                for (EngineListener listener: listeners) {
                    listener.OnInfo(message);
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

    public void sendMoveMessageFromThread(int move) {
        Message m = new Message();
        Bundle b = new Bundle();
        b.putInt("move", move);
        m.what = MSG_MOVE;
        m.setData(b);
        updateHandler.sendMessage(m);
    }

    abstract public void play(int msecs, int ply);
    abstract public boolean isReady();
    abstract public void abort();
    abstract public void destroy();


    public void addListener(EngineListener listener) {
        this.listeners.add(listener);
    }

    public void removeListener(EngineListener listener) {
        this.listeners.remove(listener);
    }
}
