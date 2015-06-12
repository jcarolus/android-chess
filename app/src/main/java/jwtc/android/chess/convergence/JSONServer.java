package jwtc.android.chess.convergence;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Enumeration;

import jwtc.chess.JNI;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class JSONServer {
    private Thread m_thread;
    public static final String TAG = "Convergence.JSONServer";
    public static final int REQ_INVALID = 0;
    public static final int REQ_JSON = 1;
    public static final int REQ_HEAD = 2;
    public static final int REQ_REDIRECT = 3;

    protected JNI _jni;
    protected int _portNumber;
    protected ServerSocket _serverSocket;
    protected boolean _run;

    protected Handler _threadHandler;

    public JSONServer(int portNumber, Handler handler) {
        _portNumber = portNumber;
        _run = true;
        _jni = new JNI();
        _serverSocket = null;
        _threadHandler = handler;
    }

    public boolean start() {

        _run = true;

        try {
            _serverSocket = new ServerSocket(_portNumber);
        } catch (Exception ex) {
            _serverSocket = null;
            Log.e(TAG, ex.toString());
            return false;
        }

        m_thread = new Thread(new Runnable() {

            public void run() {
                int req;
                try {
                    Log.i(TAG, "Trying to open socket");
                    sendMessage(ConvergenceActivity.MSG_SERVER_STARTED);
                    while (_run) {
                        req = REQ_INVALID;

                        Log.i(TAG, "Waiting for client socket");
                        Socket clientSocket = _serverSocket.accept();
                        Log.i(TAG, "Accepted");
                        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                        Log.i(TAG, "Start read from client");

                        String inputLine;
                        while ((inputLine = in.readLine()) != null) {
                            Log.i(TAG, "Got [" + inputLine + "]");
                            if (inputLine.indexOf("GET / ") == 0) {
                                req = REQ_JSON;
                            }
                            if (inputLine.indexOf("HEAD / ") == 0) {
                                req = REQ_HEAD;
                            }
                            if (inputLine.length() == 0) {
                                break;
                            }
                        }
                        String outputLine = "HTTP/1.0 400 Bad request\r\n\r\n-\r\n\r\n";

                        switch (req) {
                            case REQ_JSON:
                                outputLine = "HTTP/1.0 200 OK\r\n" +
                                        "Access-Control-Allow-Origin: *\r\n" +
                                        "Content-Type:application/json\r\n\r\n{\"FEN\":\"" + _jni.toFEN() + "\"}\r\n\r\n";
                                break;
                            case REQ_HEAD:
                                outputLine = "HTTP/1.0 200 OK\r\n" +
                                        "Access-Control-Allow-Origin: *:*\r\n\r\n";
                                break;
                            case REQ_REDIRECT:
                                outputLine = "HTTP/1.0 302\r\nLocation:http://www.jwtc.nl/tv/\r\n\r\n";
                                break;
                        }

                        Log.i(TAG, outputLine);
                        out.println(outputLine);
                        clientSocket.close();
                    }
                    _serverSocket.close();
                    sendMessage(ConvergenceActivity.MSG_SERVER_STOPPED);
                } catch (Exception ex) {
                    Log.e(TAG, "Exception " + ex);
                    sendMessage(ConvergenceActivity.MSG_SERVER_STOPPED);
                }
            }
        });

        m_thread.start();

        return true;
    }

    public void sendMessage(int msgType){
        Message msg = new Message();
        msg.what = msgType;
        _threadHandler.sendMessage(msg);
    }

    public boolean isAlive() {
        if (m_thread == null) {
            Log.i(TAG, "isAlive -> thread = null");
            return false;
        }
        if (m_thread.isAlive()) {
            Log.i(TAG, "isAlive -> thread = " + m_thread.isAlive() + ", serversocket " + (_serverSocket != null));
            return _serverSocket != null && (false == _serverSocket.isClosed());
        }
        return false;
    }

    public void stop() {
        _run = false;
        if (_serverSocket != null && (false == _serverSocket.isClosed())) {
            try {
                _serverSocket.close();
            } catch (Exception ex) {
            }
        }
        _serverSocket = null;
        m_thread = null;
        sendMessage(ConvergenceActivity.MSG_SERVER_STOPPED);
    }

    public static String getIpAddress() {
        try {
            for (Enumeration en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = (NetworkInterface) en.nextElement();
                for (Enumeration enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = (InetAddress) enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        String ipAddress = inetAddress.getHostAddress().toString();
                        Log.i("IP address", "" + ipAddress);
                        //if(ipAddress.startsWith("192.168.") || ipAddress.startsWith("172.16.") || ipAddress.startsWith("10.0.")){
                        return ipAddress;
                        //}
                    }
                }
            }
        } catch (Exception ex) {
            Log.e(TAG, "Socket exception in GetIP Address of Utilities " + ex.toString());
        }
        return null;
    }
}
