package jwtc.android.chess.hotspotboard;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Locale;

public class HotspotBoardService extends Service {
    protected static final String TAG = "HotspotBoardService";
    public static final int MSG_ACTIVITY_CONNECTED = 1;
    public static final int MSG_START_SESSION = 2;
    public static final int MSG_SOCKET_CONNECTED = 3;
    public static final int MSG_SOCKET_DISCONNECTED = 4;
    public static final int MSG_SEND_GAME_UPDATE = 5;
    public static final int MSG_RECEIVED_GAME_UPDATE = 6;
    public static final int MSG_SET_HOST_COLOR = 7;
    public static final int MSG_SET_PLAYER_COLOR = 8;

    private Thread workerThread;
    private Socket socket = null;
    private ServerSocket serverSocket = null;
    BufferedWriter writer = null;

    private Messenger activityMessenger = null;
    private boolean isHost = false;
    private boolean hostPlaysAsWhite = true;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return messengerFromActivity.getBinder();
    }

    // Messenger that receives messages from the activity
    private final Messenger messengerFromActivity = new Messenger(new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "handleMessage " + msg.what);
            switch (msg.what) {
                case MSG_ACTIVITY_CONNECTED:
                    activityMessenger = msg.replyTo;
                    break;
                case MSG_START_SESSION:
                    isHost = msg.arg1 == 1;
                    startSession(isHost, 8080);
                    break;
                case MSG_SEND_GAME_UPDATE:
                    if (writer != null) {
                        if (socket == null) {
                            Log.d(TAG, "socket is null");
                        } else {
                            try {
                                String s = msg.getData().getString("data", null);
                                Log.d(TAG, "Trying to write to socket: " + s);
                                if (s != null) {
                                    new Thread(() -> {
                                        try {
                                            writer.write(s + "\n");
                                            writer.flush();
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                    }).start();
                                }
                            } catch (Exception ex) {
                                Log.d(TAG, "Could not write to socket: " + ex.getMessage());
                                ex.printStackTrace();
                            }
                        }
                    } else {
                        Log.d(TAG, "writer is null");
                    }

                    break;
            }
        }
    });

    private void notifyActivityGameUpdate(String data) {
        if (activityMessenger == null) {
            Log.d(TAG, "notifyActivityGameUpdate but activityMessenger is null");
            return;
        }
        Log.d(TAG, "notifyActivityGameUpdate: " + data);
        try {
            Message message = Message.obtain(null, MSG_RECEIVED_GAME_UPDATE);
            Bundle bundle = new Bundle();
            bundle.putString("data", data);
            message.setData(bundle);
            activityMessenger.send(message);
        } catch (RemoteException e) {
            Log.d(TAG, "notifyActivityGameUpdate failed");
            e.printStackTrace();
        }
    }

    private void notifyActivity(int what) {
        if (activityMessenger == null) {
            Log.d(TAG, "notifyActivity but activityMessenger is null");
            return;
        }
        Log.d(TAG, "notifyActivity: " + what);
        try {
            Message message = Message.obtain(null, what);
            activityMessenger.send(message);
        } catch (RemoteException e) {
            Log.d(TAG, "notifyActivity failed");
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        tearDown();
        super.onDestroy();
    }

    public void tearDown() {
        // activityMessenger = null;
        workerThread = null;
        try {
            if (socket != null && socket.isConnected()) {
                socket.close();
            }
        } catch (Exception ex) {}
        socket = null;

        if (serverSocket != null) {
            try {
                serverSocket.close();
                serverSocket = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void startSession(boolean isHost, final int port) {
        Log.d(TAG, "startSession " + (isHost ? " as host" : " as client"));
        if (serverSocket != null) {
            if (!serverSocket.isClosed()) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    Log.d(TAG, "ServerSocket was open, but closing error " + e.getMessage());
                }
            }
        }
        workerThread = new Thread(() -> {
            try {
                if (isHost) {
                    serverSocket = new ServerSocket(port);
                    Log.d(TAG, "Serversocket created");
                    socket = serverSocket.accept();
                    Log.d(TAG, "client socket connected to server");
                } else {
                    WifiManager wifiManager = (WifiManager) HotspotBoardService.this.getSystemService(Context.WIFI_SERVICE);
                    DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
                    int hostAddress = dhcpInfo.gateway;

                    String hostIp = String.format(Locale.US, "%d.%d.%d.%d",
                            (hostAddress & 0xff),
                            (hostAddress >> 8 & 0xff),
                            (hostAddress >> 16 & 0xff),
                            (hostAddress >> 24 & 0xff));

                    socket = new Socket(hostIp, port);

                    Log.d(TAG, "client socket connected");
                }

                notifyActivity(MSG_SOCKET_CONNECTED);

                writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                while (socket != null && socket.isConnected()) {
                    String response = reader.readLine();
                    Log.d(TAG, "Received from socket: " + response);
                    if (response != null) { // null when socket is closed
                        notifyActivityGameUpdate(response);
                    } else {
                        break;
                    }
                }
                notifyActivity(MSG_SOCKET_DISCONNECTED);
                Log.d(TAG, "socket disconnected in workerThread");
                if (serverSocket != null) {
                    serverSocket.close();
                    serverSocket = null;
                }
            } catch (Exception ex) {
                Log.d(TAG, ex.toString());
                notifyActivity(MSG_SOCKET_DISCONNECTED);
            }
        });
        workerThread.start();
    }
}
