package jwtc.android.chess.convergence;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URI;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.entity.StringEntity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class Connection {

    public static final String TAG = "convergence.Connection";
    public static final int MSG_FIND_DEVICE = 1;
    public static final int MSG_DIAL_DESCRIPTION = 2;
    public static final int MSG_DIAL_APP = 3;

    private String _serverIp, _ssdpLocation;
    private int _serverPort = 80;

    /**
     * provide an inner thread handler to any results can be safely used by
     * an instance of the Connection class from an Android Activity
     */
    static class InnerThreadHandler extends Handler {
        WeakReference<Connection> _connection;

        InnerThreadHandler(Connection connection) {
            _connection = new WeakReference<Connection>(connection);
        }

        @Override
        public void handleMessage(Message msg) {
            Connection connection = _connection.get();
            if (connection != null) {
                Bundle bun = msg.getData();
                switch (msg.what) {
                    case MSG_FIND_DEVICE:
                        if(bun.getBoolean("status")) {
                            Log.i(TAG, "ThreadHandler found device, trying to dial");
                            try {
                                connection.dialDescription();
                            } catch (Exception ex) {
                                Log.e(TAG, "Could get dial description: " + ex.toString());
                            }
                        } else {
                            Log.e(TAG, bun.getString("buffer"));
                        }
                        break;
                    case MSG_DIAL_DESCRIPTION:
                        if(bun.getBoolean("status")) {
                            try {
                                // test with youtube
                                connection.dialApp(bun.getString("dialUrl") + "YouTube", "v=xOAG7Ab_Oz0");
                            }catch (Exception ex){
                                Log.e(TAG, "Could get dial application: " + ex.toString());
                            }
                        } else {
                            Log.e(TAG, bun.getString("buffer"));
                        }
                        break;
                    case MSG_DIAL_APP:
                        if(bun.getBoolean("status")) {

                        } else {
                            Log.e(TAG, bun.getString("buffer"));
                        }
                        break;
                }

                super.handleMessage(msg);
            }
        }
    }

    protected InnerThreadHandler _threadHandler = new InnerThreadHandler(this);

    public Connection() {

    }

    /**
     * start searching for a device
     * SSDP request
     */
    public void searchDevice() {

        new Thread(new Runnable() {

            public void run() {
                Log.i(TAG, "Start run");
                Message msg = new Message();
                msg.what = MSG_FIND_DEVICE;
                Bundle bun = new Bundle();

                try {
                    InetAddress serverAddr = InetAddress.getByName("239.255.255.250");

                    DatagramSocket socket = new DatagramSocket();
                    socket.setSoTimeout(10000);

                    String ST = "urn:dial-multiscreen-org:service:dial:1";

                    byte[] bufSend = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 3\r\nST: " + ST + "\r\n\r\n").getBytes();
                    DatagramPacket packetSend = new DatagramPacket(bufSend, bufSend.length, serverAddr, 1900);

                    socket.send(packetSend);

                    Log.i(TAG, "SSDP packet was send");

                    byte[] bufReceive = new byte[1024];
                    DatagramPacket packetReceive = new DatagramPacket(bufReceive, bufReceive.length);
                    socket.receive(packetReceive);

                    String s = new String(packetReceive.getData()).substring(0, packetReceive.getLength());

                    Log.i(TAG, s);
                    _ssdpLocation = null;
                    String[] arrS = s.split("\r\n");
                    if (arrS.length > 0) {
                        for (int i = 0; i < arrS.length; i++) {
                            String sTmp = arrS[i];
                            Log.i(TAG, sTmp);
                            int iPos = sTmp.indexOf(":");
                            if (iPos > 0) {
                                if (sTmp.substring(0, iPos).equals("Location")) {
                                    _ssdpLocation = sTmp.substring(iPos + 1);
                                    break;
                                }
                            }
                        }
                    }

                    InetAddress clientAddr = packetReceive.getAddress();
                    String sTmp = clientAddr.toString();
                    Log.i(TAG, "Got response from: " + sTmp);
                    int iPos = sTmp.indexOf("/");
                    if (iPos >= 0) {
                        _serverIp = sTmp.substring(iPos + 1);
                        bun.putBoolean("status", true);
                        bun.putString("buffer", _serverIp);

                    } else {
                        bun.putBoolean("status", false);
                        bun.putString("buffer", "");
                    }

                } catch (Exception ex) {
                    Log.e("Connection", ex.toString());
                    bun.putBoolean("status", false);
                    bun.putString("buffer", ex.toString());
                }

                msg.setData(bun);
                _threadHandler.sendMessage(msg);
                Log.i("Connection", "Done...");
            }
        }).start();
    }

    /**
     * Create and return an URI instance based on _serverIp, _serverPost and the path
     *
     * @param path String
     * @return instance of the URI
     */
    public URI getURI(String path) throws Exception {
        return new URI("http://" + _serverIp + ":" + _serverPort + (path.startsWith("/") ? path : "/" + path));
    }

    /**
     * make a DIAL description request to the device that was found
     * @throws Exception
     */
    public void dialDescription() throws Exception {
        HttpGet request = new HttpGet();
        request.setURI(new URI(_ssdpLocation));
        doRequest(request, MSG_DIAL_DESCRIPTION);
    }

    public void dialApp(String urlDial, String postBody) throws Exception {
        HttpPost request = new HttpPost();
        request.setURI(new URI(urlDial));

        request.setHeader("Content-Type", "application/x-www-form-urlencoded");
        request.setEntity(new StringEntity(postBody));

        doRequest(request, MSG_DIAL_APP);
    }

    /**
     * do a HttpGet or HttpPost request
     * send the result to the _threadHandler
     * @param request
     */
    public void doRequest(final Object request, final int msgType) {

        if (request instanceof HttpGet) {
            Log.i(TAG, "doRequest GET " + ((HttpGet) request).getURI());
        } else {
            Log.i(TAG, "doRequest POST " + ((HttpPost) request).getURI());
        }

        new Thread(new Runnable() {
            public void run() {
                // prepare data for threadhandler
                Message msg = new Message();
                msg.what = msgType;
                Bundle bun = new Bundle();

                BufferedReader in = null;
                try {

                    HttpClient client = new DefaultHttpClient();
                    HttpResponse response;
                    if (request instanceof HttpGet) {
                        response = client.execute((HttpGet) request);
                    } else {
                        response = client.execute((HttpPost) request);
                    }

                    Log.i(TAG, "Status: " + response.getStatusLine());
                    Header[] responseHeaders = response.getAllHeaders();
                    for (int i = 0; i < responseHeaders.length; i++) {
                        Log.i(TAG, "header: " + responseHeaders[i].getName() + ": " + responseHeaders[i].getValue());
                        if (responseHeaders[i].getName().toLowerCase().equals("status")) {
                            Log.i(TAG, "Response status " + responseHeaders[i].getValue());
                            bun.putInt("status", Integer.parseInt(responseHeaders[i].getValue()));
                        }
                        if(msgType == MSG_DIAL_DESCRIPTION && responseHeaders[i].getName().toLowerCase().equals("application-url")){
                            bun.putString("dialUrl", responseHeaders[i].getValue());
                        }
                    }

                    in = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
                    StringBuffer sb = new StringBuffer("");
                    String line = "";

                    while ((line = in.readLine()) != null) {
                        sb.append(line + "\n");
                    }
                    in.close();

                    bun.putString("buffer", sb.toString());
                    bun.putBoolean("status", true);

                } catch (Exception ex) {
                    bun.putBoolean("status", false);
                    bun.putString("buffer", ex.toString());
                    //Log.e(TAG, ex.toString());
                } finally {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                msg.setData(bun);
                _threadHandler.sendMessage(msg);
            }
        }).start();
    }

}
