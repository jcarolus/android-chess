package jwtc.android.chess.ics;

import android.os.Handler;
import android.os.Message;
import java.lang.ref.WeakReference;

public class ICSThreadMessageHandler extends Handler {
    public static final int MSG_PARSE = 1;
    public static final int MSG_ERROR = 2;
    public static final int MSG_CONNECTION_CLOSED = 3;

    private WeakReference<ICSServer> serverWeakReference;

    ICSThreadMessageHandler(ICSServer icsServer) {
        this.serverWeakReference = new WeakReference<ICSServer>(icsServer);
    }

    @Override
    public void handleMessage(Message msg) {
        ICSServer icsServer = serverWeakReference.get();
        if (icsServer != null) {
            icsServer.handleThreadMessage(msg);
            super.handleMessage(msg);
        }
    }
}