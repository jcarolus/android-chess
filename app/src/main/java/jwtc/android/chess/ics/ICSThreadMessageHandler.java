package jwtc.android.chess.ics;

import android.os.Handler;
import android.os.Message;
import java.lang.ref.WeakReference;

public class ICSThreadMessageHandler extends Handler {
    public static final int MSG_PARSE = 1;
    public static final int MSG_ERROR = 2;
    public static final int MSG_CONNECTION_CLOSED = 3;
    public static final int MSG_TIMEOUT = 4;

    private WeakReference<ICSServer> serverWeakReference;
    private Runnable runnable;

    ICSThreadMessageHandler(ICSServer icsServer) {
        this.serverWeakReference = new WeakReference<ICSServer>(icsServer);
        runnable = null;
    }

    @Override
    public void handleMessage(Message msg) {
        ICSServer icsServer = serverWeakReference.get();
        if (icsServer != null) {
            icsServer.handleThreadMessage(msg);
            super.handleMessage(msg);
        }
    }

    public void setTimeout(long ms) {
        cancelTimeout();

        runnable = new Runnable() {
            @Override
            public void run() {
                Message m = new Message();
                m.what = MSG_TIMEOUT;

                handleMessage(m);
            }
        };
        postDelayed(runnable, ms);
    }

    public void cancelTimeout() {
        if (runnable != null) {
            removeCallbacks(runnable);
        }
    }
}