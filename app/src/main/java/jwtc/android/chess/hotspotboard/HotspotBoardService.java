package jwtc.android.chess.hotspotboard;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import java.util.ArrayList;
import java.util.EnumSet;
import jwtc.android.chess.engine.RustSearchRequest.Variant;
import jwtc.android.chess.services.DisplayedPosition;
import jwtc.android.chess.services.NetworkAddressHelper;
import jwtc.android.chess.services.SocketConnectService;
import jwtc.chess.board.BoardConstants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class HotspotBoardService extends SocketConnectService {
    protected static final String TAG = "HotspotBoardService";
    private static final int POLL_INTERVAL_MS = 1000;
    public static final int MSG_ACTIVITY_CONNECTED = 1;
    public static final int MSG_START_SESSION = 2;
    public static final int MSG_SOCKET_CONNECTED = 3;
    public static final int MSG_SOCKET_DISCONNECTED = 4;
    public static final int MSG_SEND_GAME_UPDATE = 5;
    public static final int MSG_RECEIVED_GAME_UPDATE = 6;
    public static final int MSG_SET_HOST_COLOR = 7;
    public static final int MSG_SET_PLAYER_COLOR = 8;
    public static final int MSG_STOP_SESSION = 9;
    public static final int MSG_SOCKET_LISTENING = 10;
    public static final int MSG_SESSION_STATE = 11;
    public static final int MSG_ACTIVITY_DISCONNECTED = 12;
    public static final int MSG_PEER_VARIANTS = 13;
    public static final String KEY_PEER_VARIANTS = "peerVariants";
    public static final String KEY_CONNECTION_MODE = "connectionMode";
    public static final String KEY_HOST_IP = "hostIp";
    public static final String KEY_HOST_MODE = "hostMode";
    public static final int CONNECTION_MODE_HOTSPOT = 0;
    public static final int CONNECTION_MODE_LOCAL_WIFI = 1;
    public static final int HOST_MODE_PLAY = 0;
    public static final int HOST_MODE_SHARE = 1;

    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollRunnable =
            new Runnable() {
                @Override
                public void run() {
                    broadcastSnapshotIfChanged();
                    pollHandler.postDelayed(this, POLL_INTERVAL_MS);
                }
            };

    private boolean isHost = false;
    private ClientConnection activePlayingConnection = null;
    private EnumSet<Variant> peerVariants = EnumSet.of(Variant.STANDARD);
    private int connectionMode = CONNECTION_MODE_HOTSPOT;
    private int hostMode = HOST_MODE_PLAY;
    private String hostIpAddress = null;
    private String lastBroadcastFen = null;
    private boolean isListening = false;
    private String playerName = "";
    private GameMessage currentPosition;
    private int gameResult = 0;
    private boolean observing = false;

    private void rememberGame(GameMessage message, boolean incoming) {
        if (message.type == GameMessage.TYPE_MOVE
                || message.type == GameMessage.TYPE_SHARE_SNAPSHOT) {
            currentPosition = message;
            observing = incoming && message.type == GameMessage.TYPE_SHARE_SNAPSHOT;
            gameResult = 0;
        } else if (message.type == GameMessage.TYPE_RESIGN) {
            boolean white = playerName.equals(message.white);
            if (incoming) {
                white = !white;
            }
            gameResult = white ? BoardConstants.WHITE_RESIGNED : BoardConstants.BLACK_RESIGNED;
        } else if (message.type == GameMessage.TYPE_DRAW_ACCEPT) {
            gameResult = BoardConstants.DRAW_AGREEMENT;
        }
    }

    private void putPeerVariants(Bundle state) {
        ArrayList<String> names = new ArrayList<>();
        for (Variant variant : peerVariants) {
            names.add(variant.name());
        }
        state.putStringArrayList(KEY_PEER_VARIANTS, names);
    }

    private void resetPlayingConnection() {
        activePlayingConnection = null;
        peerVariants = EnumSet.of(Variant.STANDARD);
        currentPosition = null;
        gameResult = 0;
        observing = false;
    }

    private void sendSessionState() {
        Bundle state = new Bundle();
        state.putBoolean("connected", hasConnectedSockets());
        state.putBoolean("listening", isHost && isListening && !hasConnectedSockets());
        state.putBoolean("host", isHost);
        state.putBoolean("share", hostMode == HOST_MODE_SHARE);
        state.putBoolean("observing", observing);
        state.putString("name", playerName);
        state.putInt(KEY_CONNECTION_MODE, connectionMode);
        state.putString(KEY_HOST_IP, hostIpAddress);
        state.putInt("result", gameResult);
        putPeerVariants(state);
        if (currentPosition != null) {
            try {
                state.putString("position", currentPosition.toJsonString());
            } catch (JSONException e) {
                Log.w(TAG, "Cannot serialize position", e);
            }
        }
        notifyActivity(MSG_SESSION_STATE, state);
    }

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
                sendSessionState();
                break;
            case MSG_ACTIVITY_DISCONNECTED:
                unregisterActivityMessenger(msg.replyTo);
                break;
            case MSG_START_SESSION:
                isHost = msg.arg1 == 1;
                Bundle sessionData = msg.getData();
                if (sessionData != null) {
                    playerName = sessionData.getString("name", "");
                    connectionMode =
                            sessionData.getInt(KEY_CONNECTION_MODE, CONNECTION_MODE_HOTSPOT);
                    hostIpAddress = sessionData.getString(KEY_HOST_IP, null);
                    hostMode = sessionData.getInt(KEY_HOST_MODE, HOST_MODE_PLAY);
                }
                startSession(isHost, 8080);
                break;
            case MSG_SEND_GAME_UPDATE:
                if (isHost && hostMode == HOST_MODE_SHARE) {
                    broadcastSnapshot(true);
                } else {
                    String gameUpdate = msg.getData().getString("data", null);
                    Log.d(TAG, "Trying to write to socket: " + gameUpdate);
                    if (gameUpdate != null && activePlayingConnection != null) {
                        try {
                            GameMessage update = GameMessage.fromJson(gameUpdate);
                            if (canSendVariant(update.variant)) {
                                rememberGame(update, false);
                                sendLine(activePlayingConnection, gameUpdate);
                            }
                        } catch (JSONException e) {
                            Log.w(TAG, "Invalid game update", e);
                        }
                    }
                }
                break;
            case MSG_STOP_SESSION:
                stopSession();
                break;
        }
    }

    private boolean canSendVariant(Variant variant) {
        if (variant == null || variant == Variant.STANDARD) {
            return true;
        }
        if (isHost) {
            return peerVariants.contains(variant);
        }
        // The host selects the variant in the first position it sends.
        return currentPosition != null && variant == currentPosition.variant;
    }

    private void notifyActivityGameUpdate(String data, GameMessage update) {
        Log.d(TAG, "notifyActivityGameUpdate: " + data);
        rememberGame(update, true);
        Bundle bundle = new Bundle();
        bundle.putString("data", data);
        notifyActivity(MSG_RECEIVED_GAME_UPDATE, bundle);
    }

    @Override
    protected void onClientConnected(ClientConnection connection, boolean outgoingConnection) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            pollHandler.post(() -> onClientConnected(connection, outgoingConnection));
            return;
        }
        Log.d(TAG, "client connected " + connection.clientId + ", outgoing=" + outgoingConnection);

        if (connection.socket.isClosed()) {
            return;
        }
        if (isHost && hostMode == HOST_MODE_SHARE) {
            sendSnapshot(connection);
        } else {
            if (activePlayingConnection != null) {
                closeClientConnection(connection);
                return;
            }
            activePlayingConnection = connection;
            if (outgoingConnection) {
                try {
                    sendLine(connection, GameMessage.supportedVariantsMessage());
                } catch (JSONException e) {
                    Log.w(TAG, "Cannot advertise Hotspot variants", e);
                }
            }
        }

        notifyCurrentConnectionState(true);
    }

    @Override
    protected void onClientDisconnected(ClientConnection connection, boolean outgoingConnection) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            pollHandler.post(() -> onClientDisconnected(connection, outgoingConnection));
            return;
        }
        Log.d(
                TAG,
                "client disconnected " + connection.clientId + ", outgoing=" + outgoingConnection);

        if (connection == activePlayingConnection) {
            resetPlayingConnection();
        } else if (!isHost || hostMode != HOST_MODE_SHARE) {
            return;
        }

        notifyCurrentConnectionState(true);
    }

    @Override
    protected void onLineReceived(ClientConnection connection, String line) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            pollHandler.post(() -> onLineReceived(connection, line));
            return;
        }
        Log.d(TAG, "Received from socket: " + line + ", clientId=" + connection.clientId);
        boolean sharing = isHost && hostMode == HOST_MODE_SHARE;
        if (!sharing && connection != activePlayingConnection) {
            return;
        }

        try {
            JSONObject json = new JSONObject(line);
            JSONArray variants = json.optJSONArray("supportedVariants");
            if (variants != null) {
                if (isHost && !sharing) {
                    peerVariants = GameMessage.supportedVariantsFromJson(variants);
                    Bundle state = new Bundle();
                    putPeerVariants(state);
                    notifyActivity(MSG_PEER_VARIANTS, state);
                }
                return;
            }
            notifyActivityGameUpdate(line, GameMessage.fromJson(json));
        } catch (JSONException e) {
            Log.w(TAG, "Invalid Hotspot message", e);
        }
    }

    @Override
    protected void onSocketError(Exception exception) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            pollHandler.post(() -> onSocketError(exception));
            return;
        }
        Log.d(TAG, exception.toString());
        notifyCurrentConnectionState(true);
    }

    @Override
    public void onDestroy() {
        stopPolling();
        super.onDestroy();
    }

    public void startSession(boolean isHost, final int port) {
        Log.d(
                TAG,
                "startSession " + (isHost ? " as host" : " as client") + ", hostMode=" + hostMode);
        resetPlayingConnection();
        lastBroadcastFen = null;
        isListening = false;
        if (isHost) {
            startHosting(port);
            isListening = true;
            notifyActivity(MSG_SOCKET_LISTENING);
            if (hostMode == HOST_MODE_SHARE) {
                startPolling();
            } else {
                stopPolling();
            }
        } else {
            stopPolling();
            startClient(resolveRemoteHostIp(), port);
        }
    }

    public void stopSession() {
        Log.d(TAG, "stopSession");
        stopPolling();
        stopConnections();
        resetPlayingConnection();
        lastBroadcastFen = null;
        isListening = false;
        notifyActivity(MSG_SOCKET_DISCONNECTED);
    }

    private void notifyCurrentConnectionState(boolean includeDisconnected) {
        if (hasConnectedSockets()) {
            notifyActivity(MSG_SOCKET_CONNECTED);
            return;
        }

        if (isHost && isListening) {
            notifyActivity(MSG_SOCKET_LISTENING);
            return;
        }

        if (includeDisconnected) {
            notifyActivity(MSG_SOCKET_DISCONNECTED);
        }
    }

    private boolean hasConnectedSockets() {
        if (!isHost || hostMode != HOST_MODE_SHARE) {
            return activePlayingConnection != null && !activePlayingConnection.socket.isClosed();
        }
        for (ClientConnection connection : getClientConnectionsSnapshot()) {
            if (!connection.socket.isClosed()) {
                return true;
            }
        }
        return false;
    }

    private void startPolling() {
        pollHandler.removeCallbacks(pollRunnable);
        pollHandler.post(pollRunnable);
    }

    private void stopPolling() {
        pollHandler.removeCallbacks(pollRunnable);
    }

    private void broadcastSnapshotIfChanged() {
        String fen = DisplayedPosition.currentFen();
        if (fen == null || fen.equals(lastBroadcastFen)) {
            return;
        }
        broadcastSnapshot(false);
    }

    private void broadcastSnapshot(boolean force) {
        String fen = DisplayedPosition.currentFen();
        if (!force && (fen == null || fen.equals(lastBroadcastFen))) {
            return;
        }
        if (fen == null) {
            return;
        }

        try {
            // int type, String FEN, String white, String black, int lastMove, String lastMovePgn
            String payload =
                    new GameMessage(GameMessage.TYPE_SHARE_SNAPSHOT, fen, "", "", 0, "")
                            .toJsonString();
            lastBroadcastFen = fen;
            broadcastLine(payload);
        } catch (JSONException e) {
            Log.d(TAG, "Could not serialize share snapshot", e);
        }
    }

    private void sendSnapshot(ClientConnection connection) {
        try {
            String fen = DisplayedPosition.currentFen();
            if (fen == null) {
                return;
            }
            sendLine(
                    connection,
                    new GameMessage(GameMessage.TYPE_SHARE_SNAPSHOT, fen, "", "", 0, "")
                            .toJsonString());
        } catch (JSONException e) {
            Log.d(TAG, "Could not send share snapshot", e);
        }
    }

    private String resolveRemoteHostIp() {
        if (connectionMode == CONNECTION_MODE_LOCAL_WIFI
                && hostIpAddress != null
                && hostIpAddress.trim().length() > 0) {
            return hostIpAddress.trim();
        }
        return NetworkAddressHelper.getWifiGatewayIp(this);
    }
}
