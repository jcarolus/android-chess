package jwtc.android.chess.convergence;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * Wrapper class for the Simple device discovery protocol
 */
public class SSDP {

    public static final String TAG = "convergence.SSDP";
    protected Handler _threadHandler;

    public SSDP(Handler handler) {
        _threadHandler = handler;
    }

    /**
     * start searching for devices
     * SSDP request
     */
    public void searchDevices() {

        new Thread(new Runnable() {

            public void run() {
                Log.i(TAG, "Start run");
                Message msg = new Message();
                msg.what = ConvergenceActivity.MSG_SEARCH_DEVICE;
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
                    String ssdpLocation = null;
                    String[] arrS = s.split("\r\n");
                    if (arrS.length > 0) {
                        for (int i = 0; i < arrS.length; i++) {
                            String sTmp = arrS[i].toLowerCase();
                            Log.i(TAG, sTmp);
                            int iPos = sTmp.indexOf(":");
                            if (iPos > 0) {
                                if (sTmp.substring(0, iPos).equals("location")) {
                                    ssdpLocation = sTmp.substring(iPos + 1).trim();
                                    break;
                                }
                            }
                        }
                    }

                    InetAddress clientAddr = packetReceive.getAddress();
                    String sTmp = clientAddr.toString();
                    Log.i(TAG, "Got response from: " + sTmp);
                    int iPos = sTmp.indexOf("/");
                    if (ssdpLocation != null) {
                        bun.putBoolean("status", true);
                        bun.putString("buffer", ssdpLocation);

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
                Log.i(TAG, "Done...");
            }
        }).start();
    }
}
