package jwtc.android.chess.hotspotboard;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;

import java.util.Locale;

import jwtc.android.chess.services.SocketConnectService;

public class HotspotBoardService extends SocketConnectService {
    protected static final String TAG = "HotspotBoardService";
    public static final int MSG_ACTIVITY_CONNECTED = 1;
    public static final int MSG_START_SESSION = 2;
    public static final int MSG_SOCKET_CONNECTED = 3;
    public static final int MSG_SOCKET_DISCONNECTED = 4;
    public static final int MSG_SEND_GAME_UPDATE = 5;
    public static final int MSG_RECEIVED_GAME_UPDATE = 6;
    public static final int MSG_SET_HOST_COLOR = 7;
    public static final int MSG_SET_PLAYER_COLOR = 8;

    private boolean isHost = false;
    private ClientConnection activePlayingConnection = null;

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected void onActivityMessage(Message msg) {
        Log.d(TAG, "handleMessage " + msg.what);
        switch (msg.what) {
            case MSG_ACTIVITY_CONNECTED:
                registerActivityMessenger(msg.replyTo);
                break;
            case MSG_START_SESSION:
                isHost = msg.arg1 == 1;
                startSession(isHost, 8080);
                break;
            case MSG_SEND_GAME_UPDATE:
                String gameUpdate = msg.getData().getString("data", null);
                Log.d(TAG, "Trying to write to socket: " + gameUpdate);
                if (gameUpdate != null) {
                    broadcastLine(gameUpdate);
                }
                break;
        }
    }

    private void notifyActivityGameUpdate(String data) {
        Log.d(TAG, "notifyActivityGameUpdate: " + data);
        Bundle bundle = new Bundle();
        bundle.putString("data", data);
        notifyActivity(MSG_RECEIVED_GAME_UPDATE, bundle);
    }

    @Override
    protected void onClientConnected(ClientConnection connection, boolean outgoingConnection) {
        Log.d(TAG, "client connected " + connection.clientId + ", outgoing=" + outgoingConnection);
        if (!isHost) {
            notifyActivity(MSG_SOCKET_CONNECTED);
            return;
        }

        if (activePlayingConnection == null) {
            activePlayingConnection = connection;
            notifyActivity(MSG_SOCKET_CONNECTED);
        }
    }

    @Override
    protected void onClientDisconnected(ClientConnection connection, boolean outgoingConnection) {
        Log.d(TAG, "client disconnected " + connection.clientId + ", outgoing=" + outgoingConnection);
        if (!isHost) {
            notifyActivity(MSG_SOCKET_DISCONNECTED);
            return;
        }

        if (connection == activePlayingConnection) {
            activePlayingConnection = null;
            notifyActivity(MSG_SOCKET_DISCONNECTED);
        }
    }

    @Override
    protected void onLineReceived(ClientConnection connection, String line) {
        Log.d(TAG, "Received from socket: " + line + ", clientId=" + connection.clientId);
        if (!isHost) {
            notifyActivityGameUpdate(line);
            return;
        }

        if (connection == activePlayingConnection) {
            notifyActivityGameUpdate(line);
        }
    }

    @Override
    protected void onSocketError(Exception exception) {
        Log.d(TAG, exception.toString());
        notifyActivity(MSG_SOCKET_DISCONNECTED);
    }

    public void startSession(boolean isHost, final int port) {
        Log.d(TAG, "startSession " + (isHost ? " as host" : " as client"));
        activePlayingConnection = null;
        if (isHost) {
            startHosting(port);
        } else {
            startClient(resolveGatewayHostIp(), port);
        }
    }

    private String resolveGatewayHostIp() {
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
        int hostAddress = dhcpInfo.gateway;

        return String.format(Locale.US, "%d.%d.%d.%d",
            (hostAddress & 0xff),
            (hostAddress >> 8 & 0xff),
            (hostAddress >> 16 & 0xff),
            (hostAddress >> 24 & 0xff));
    }
}
