package jwtc.android.chess.services;

import android.app.Service;
import android.content.Intent;
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
import java.util.ArrayList;
import java.util.List;

public abstract class SocketConnectService extends Service {
    private final Object connectionLock = new Object();
    private final ArrayList<ClientConnection> clientConnections = new ArrayList<>();
    private final Messenger messengerFromActivity = new Messenger(new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            onActivityMessage(msg);
        }
    });

    private Messenger activityMessenger = null;
    private Thread acceptThread = null;
    private Thread connectThread = null;
    private ServerSocket serverSocket = null;
    private int nextClientId = 1;
    private boolean shuttingDown = false;

    protected abstract void onActivityMessage(Message msg);

    protected abstract void onClientConnected(ClientConnection connection, boolean outgoingConnection);

    protected abstract void onClientDisconnected(ClientConnection connection, boolean outgoingConnection);

    protected abstract void onLineReceived(ClientConnection connection, String line);

    protected abstract void onSocketError(Exception exception);

    protected String getLogTag() {
        return getClass().getSimpleName();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(getLogTag(), "onBind");
        return messengerFromActivity.getBinder();
    }

    @Override
    public void onDestroy() {
        Log.i(getLogTag(), "onDestroy");
        stopConnections();
        super.onDestroy();
    }

    protected void registerActivityMessenger(Messenger messenger) {
        activityMessenger = messenger;
    }

    protected void notifyActivity(int what) {
        notifyActivity(what, null);
    }

    protected void notifyActivity(int what, Bundle data) {
        if (activityMessenger == null) {
            Log.d(getLogTag(), "notifyActivity but activityMessenger is null");
            return;
        }

        try {
            Message message = Message.obtain(null, what);
            if (data != null) {
                message.setData(data);
            }
            activityMessenger.send(message);
        } catch (RemoteException e) {
            Log.d(getLogTag(), "notifyActivity failed", e);
        }
    }

    protected void startHosting(final int port) {
        stopConnections();
        shuttingDown = false;
        acceptThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                Log.d(getLogTag(), "ServerSocket created on port " + port);
                while (!shuttingDown && serverSocket != null && !serverSocket.isClosed()) {
                    Socket acceptedSocket = serverSocket.accept();
                    attachSocket(acceptedSocket, false);
                }
            } catch (IOException e) {
                if (!shuttingDown) {
                    Log.d(getLogTag(), "Host loop failed", e);
                    onSocketError(e);
                }
            }
        });
        acceptThread.start();
    }

    protected void startClient(final String hostIp, final int port) {
        stopConnections();
        shuttingDown = false;
        connectThread = new Thread(() -> {
            try {
                attachSocket(new Socket(hostIp, port), true);
            } catch (IOException e) {
                if (!shuttingDown) {
                    Log.d(getLogTag(), "Client connect failed", e);
                    onSocketError(e);
                }
            }
        });
        connectThread.start();
    }

    protected void stopConnections() {
        shuttingDown = true;

        Thread currentAcceptThread = acceptThread;
        acceptThread = null;
        if (currentAcceptThread != null) {
            currentAcceptThread.interrupt();
        }

        Thread currentConnectThread = connectThread;
        connectThread = null;
        if (currentConnectThread != null) {
            currentConnectThread.interrupt();
        }

        closeServerSocket();

        List<ClientConnection> snapshot = getClientConnectionsSnapshot();
        for (ClientConnection connection : snapshot) {
            closeClientConnection(connection);
        }

        synchronized (connectionLock) {
            clientConnections.clear();
        }
    }

    protected void sendLine(ClientConnection connection, String line) {
        if (connection == null || line == null) {
            return;
        }

        new Thread(() -> {
            try {
                connection.writeLine(line);
            } catch (IOException e) {
                Log.d(getLogTag(), "Could not write to socket", e);
                closeClientConnection(connection);
            }
        }).start();
    }

    protected void broadcastLine(String line) {
        for (ClientConnection connection : getClientConnectionsSnapshot()) {
            sendLine(connection, line);
        }
    }

    protected List<ClientConnection> getClientConnectionsSnapshot() {
        synchronized (connectionLock) {
            return new ArrayList<>(clientConnections);
        }
    }

    protected void closeClientConnection(ClientConnection connection) {
        connection.close();
    }

    private void closeServerSocket() {
        ServerSocket currentServerSocket = serverSocket;
        serverSocket = null;
        if (currentServerSocket != null) {
            try {
                currentServerSocket.close();
            } catch (IOException e) {
                Log.d(getLogTag(), "Could not close ServerSocket", e);
            }
        }
    }

    private void attachSocket(Socket socket, boolean outgoingConnection) throws IOException {
        ClientConnection connection = new ClientConnection(nextClientId++, socket, outgoingConnection);
        synchronized (connectionLock) {
            clientConnections.add(connection);
        }

        onClientConnected(connection, outgoingConnection);
        connection.readerThread = new Thread(() -> readLoop(connection));
        connection.readerThread.start();
    }

    private void readLoop(ClientConnection connection) {
        try {
            while (!shuttingDown && !connection.isClosed()) {
                String response = connection.reader.readLine();
                if (response == null) {
                    break;
                }
                onLineReceived(connection, response);
            }
        } catch (IOException e) {
            if (!shuttingDown) {
                Log.d(getLogTag(), "Socket read failed", e);
                onSocketError(e);
            }
        } finally {
            boolean removed;
            synchronized (connectionLock) {
                removed = clientConnections.remove(connection);
            }
            connection.close();
            if (removed) {
                onClientDisconnected(connection, connection.outgoingConnection);
            }
        }
    }

    protected static class ClientConnection {
        public final int clientId;
        public final Socket socket;
        public final boolean outgoingConnection;
        private final BufferedWriter writer;
        private final BufferedReader reader;
        private Thread readerThread = null;
        private boolean closed = false;

        ClientConnection(int clientId, Socket socket, boolean outgoingConnection) throws IOException {
            this.clientId = clientId;
            this.socket = socket;
            this.outgoingConnection = outgoingConnection;
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        }

        synchronized void writeLine(String line) throws IOException {
            if (closed) {
                return;
            }
            writer.write(line + "\n");
            writer.flush();
        }

        synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }

        synchronized boolean isClosed() {
            return closed;
        }
    }
}
