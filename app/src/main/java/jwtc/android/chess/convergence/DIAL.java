package jwtc.android.chess.convergence;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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

/**
 *
 */
public class DIAL {

    public static final String TAG = "convergence.Connection";

    private String _ssdpLocation;
    protected Handler _threadHandler;

    //private ArrayList<HashMap<String,String>> _mapConnections = new ArrayList<HashMap<String,String>>();


    public DIAL(String ssdpLocation, Handler handler) {
        _ssdpLocation = ssdpLocation;
        _threadHandler = handler;
    }


    /**
     * make a DIAL description request to the device that was found
     *
     * @throws Exception
     */
    public void dialDescription() throws Exception {
        HttpGet request = new HttpGet();
        request.setURI(new URI(_ssdpLocation));
        doRequest(request, ConvergenceActivity.MSG_DIAL_DESCRIPTION);
    }

    /**
     * try to DIAL and open an application on the TV using urlDial and postBody as an argument
     *
     * @param urlDial
     * @param postBody
     * @throws Exception
     */
    public void dialApp(String urlDial, String postBody) throws Exception {
        HttpPost request = new HttpPost();
        request.setURI(new URI(urlDial));

        request.setHeader("Content-Type", "application/x-www-form-urlencoded");
        request.setEntity(new StringEntity(postBody));

        doRequest(request, ConvergenceActivity.MSG_DIAL_APP);
    }

    /**
     * do a HttpGet or HttpPost request
     * send the result to the _threadHandler
     *
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
                        if (msgType == ConvergenceActivity.MSG_DIAL_DESCRIPTION && responseHeaders[i].getName().toLowerCase().equals("application-url")) {
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
