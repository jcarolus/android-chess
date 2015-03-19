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
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import java.io.StringReader;
import java.net.URL;

public class Connection {

	public static final String TAG = "convergence.Connection";
	public static final int MSG_ERROR 				= -1;
	public static final int MSG_FOUND_DEVICE 		= 1;
	public static final int MSG_SERVER_RESPONSE		= 2;

	public static final int TYPE_DIAL				= 1;
    public static final int TYPE_DLNA               = 2;

    public static final int STATUS_DISCOVER         = 1;
    public static final int STATUS_GETSERVICE       = 2;
    public static final int STATUS_HASCONTROL       = 3;

    public static final int DLNA_TRANSPORT          = 1;
    public static final int DLNA_PLAY               = 2;
    public static final int DLNA_DONE               = 3;

	private String _serverIp, _ssdpLocation, _controlUrl = null;
	private int _serverPort = 80;
	private int _connectionType = TYPE_DIAL, _status = STATUS_DISCOVER, _dlnaStatus = DLNA_TRANSPORT;
	
	
	static class InnerThreadHandler extends Handler{
		WeakReference<Connection> _connection;
		
		InnerThreadHandler(Connection connection){
			_connection = new WeakReference<Connection>(connection);
		}

		@Override public void handleMessage(Message msg) {
			Connection connection = _connection.get();
			if(connection != null){
				switch(msg.what){
				case MSG_FOUND_DEVICE:
					Log.i(TAG, "ThreadHandler found device, trying to connect");
					connection.connect();
					break;
				case MSG_SERVER_RESPONSE:
                    String sBuf = msg.getData().getString("buffer");
					Log.i(TAG, "ThreadHandler got: " + sBuf);
					Log.i(TAG, "Status " + msg.getData().getInt("status"));
                    if(connection._status == STATUS_GETSERVICE) {
                        try {
                            XmlPullParser parser = Xml.newPullParser();
                            parser.setInput(new StringReader(sBuf));
                            boolean inService = false, inServiceType = false, isRenderingControl = false, isControlUrl = false;
                            int eventType = parser.getEventType();

                            while (eventType != XmlPullParser.END_DOCUMENT) {
                                if (eventType == XmlPullParser.START_DOCUMENT) {
                                    //Log.i(TAG, "Start document");
                                } else if (eventType == XmlPullParser.START_TAG) {
                                    //Log.i(TAG, "Start tag " + parser.getName());
                                    if (parser.getName().equals("service")) {
                                        inService = true;
                                    } else if (inService && parser.getName().equals("serviceType")) {
                                        inServiceType = true;
                                    } else if (isRenderingControl && parser.getName().equals("controlURL")) {
                                        isControlUrl = true;
                                    }
                                } else if (eventType == XmlPullParser.END_TAG) {
                                    //Log.i(TAG, "End tag "+parser.getName());
                                    if (parser.getName().equals("service")) {
                                        inService = false;
                                    } else if (parser.getName().equals("serviceType")) {
                                        inServiceType = false;
                                    } else if (parser.getName().equals("controlURL")) {
                                        isControlUrl = false;
                                    }
                                } else if (eventType == XmlPullParser.TEXT) {
                                    //Log.i(TAG, "Text "+parser.getText());
                                    if (isControlUrl) {
                                        connection._controlUrl = parser.getText();
                                        Log.i(TAG, "Found controlURL " + connection._controlUrl);
                                        connection._status = STATUS_HASCONTROL;
                                        connection._dlnaStatus = DLNA_PLAY;
                                        HttpPost request = connection.getDLNARequest();
                                        connection.addDLNA_AVTransport(request, "http://video-js.zencoder.com/oceans-clip.mp4");
                                        connection.doRequest(request);

                                        break;
                                    } else if (inServiceType && parser.getText().equals("urn:schemas-upnp-org:service:RenderingControl:1")) {
                                        isRenderingControl = true;
                                    }
                                }
                                eventType = parser.next();
                            }
                        } catch (Exception ex) {
                            Log.i(TAG, ex.toString());
                        }
                    } else if(connection._dlnaStatus == DLNA_PLAY){
                        try {
                            connection._dlnaStatus = DLNA_DONE;
                            HttpPost request = connection.getDLNARequest();
                            connection.addDLNA_Play(request);
                            connection.doRequest(request);
                        } catch(Exception ex){
                            Log.e(TAG, ex.toString());
                        }
                    }

					break;
				case MSG_ERROR:
					Log.e(TAG, "ThreadHandler got error: " + msg.getData().getString("buffer"));
				}
				
				super.handleMessage(msg);
			}
		}
	}
	protected InnerThreadHandler m_threadHandler = new InnerThreadHandler(this);
	
	public Connection(){
		
	}
	
	public void searchDevice(int connectionType){

        _status = STATUS_DISCOVER;
		_connectionType = connectionType;
		
		new Thread(new Runnable(){

 			public void run() {
 				Log.i(TAG, "Start run");
 				Message msg = new Message();
 				Bundle bun = new Bundle();
 				
 				try {
					InetAddress serverAddr = InetAddress.getByName("239.255.255.250");
					
					DatagramSocket socket = new DatagramSocket();
					socket.setSoTimeout(10000);
					
					String ST = "";
					if(_connectionType == TYPE_DIAL){
						ST = "urn:dial-multiscreen-org:service:dial:1";
					} else if(_connectionType == TYPE_DLNA){
                        ST = "urn:schemas-upnp-org:device:MediaRenderer:1";
                    } else {
                        Log.e(TAG, "Bad connection type " + _connectionType);
                        return;
                    }

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
                    if(arrS.length > 0) {
                        for(int i = 0; i < arrS.length; i++){
                            String sTmp = arrS[i];
                            Log.i(TAG, sTmp);
                            int iPos = sTmp.indexOf(":");
                            if(iPos > 0){
                                if(sTmp.substring(0, iPos).equals("Location")){
                                    _ssdpLocation = sTmp.substring(iPos+1);
                                    break;
                                }
                            }
                        }
                    }
					
					InetAddress clientAddr = packetReceive.getAddress();
					String sTmp = clientAddr.toString();
					Log.i(TAG, "Got response from: " + sTmp);
					int iPos = sTmp.indexOf("/");
					if(iPos >= 0){
						_serverIp = sTmp.substring(iPos + 1);
						bun.putString("buffer", _serverIp);
						msg.what = MSG_FOUND_DEVICE;
					} else {
						msg.what = MSG_ERROR;
						bun.putString("buffer", "");
					}
					
				} catch (Exception ex) {
					Log.e("Connection", ex.toString());
					msg.what = MSG_ERROR;
					bun.putString("buffer", ex.toString());
					//ex.printStackTrace();
				}
 				
 				msg.setData(bun);
				m_threadHandler.sendMessage(msg);
				Log.i("Connection", "Done...");
 			}
		}).start();
	}

	public URI getURI(String url){
		try{
			return new URI("http://" + _serverIp + ":" + _serverPort + (url.startsWith("/") ? url : "/" + url));
		}catch(Exception e){
			return null;
		}
	}
	
	public void connect(){
        _status = STATUS_GETSERVICE;

		HttpGet request = new HttpGet();
		
		if(_connectionType == TYPE_DLNA){
            _dlnaStatus = DLNA_TRANSPORT;
            if(_ssdpLocation != null) {
                try {
                    request.setURI(new URI(_ssdpLocation));
                    //request.setURI(getURI("ws/app/" + _appId + "/connect"));
                    //addConvergenceHeaders(request);
                } catch (Exception ex){
                    Log.e(TAG, "Could not create URI from " + _ssdpLocation);
                    return;
                }
            }

		} else if(_connectionType == TYPE_DLNA){
			request.setURI(getURI("app/YouTube"));
		}
		doRequest(request);
	}

    public String getDLNA_Soap(){
        return "<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\"?>" +
                "<s:Envelope s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\" xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">" +
                "<s:Body>{{BODY}}</s:Body>" +
                "</s:Envelope>";
    }

    public void addDLNA_AVTransport(HttpPost request, String contentUrl) throws Exception{
        request.addHeader("Soapaction", "\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"");
        //request.addHeader("contentFeatures.dlna.org", "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=017000 00000000000000000000000000");

        String s = getDLNA_Soap().replace("{{BODY}}",
                "<u:SetAVTransportURI xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">" +
                "<InstanceID>0</InstanceID><CurrentURI><![CDATA[" + contentUrl + "]]></CurrentURI><CurrentURIMetaData></CurrentURIMetaData></u:SetAVTransportURI>");

        ByteArrayEntity entity = new ByteArrayEntity(s.getBytes("utf-8"));
        //entity.setContentType("text/xml");
        request.setEntity(entity);

        //request.
        Log.i(TAG, s);
        //request.addHeader("Content-Length", "" + xmlEntity.getContentLength());
        Log.i(TAG, "added DLNA transport " + contentUrl);
    }

    public void addDLNA_Play(HttpPost request) throws Exception{
        request.addHeader("Soapaction", "\"urn:schemas-upnp-org:service:AVTransport:1#Play\"");

        String s = getDLNA_Soap().replace("{{BODY}}",
                "<u:Play xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\"><InstanceID>0</InstanceID><Speed>1</Speed></u:Play>");

        ByteArrayEntity entity = new ByteArrayEntity(s.getBytes("utf-8"));
        //entity.setContentType("text/xml");
        request.setEntity(entity);

        Log.i(TAG, s);
        //request.addHeader("Content-Length", "" + xmlEntity.getContentLength());
        Log.i(TAG, "added DLNA play");
    }


    public HttpPost getDLNARequest(){

        try {
            URL url = new URL(_ssdpLocation);
            HttpPost request = new HttpPost(new URI(url.getProtocol() + "://" + url.getHost() + ":" + url.getPort() + (_controlUrl.startsWith("/") ? _controlUrl : "/" + _controlUrl)));
            request.addHeader("Content-type", "text/xml;charset=\"utf-8\"");
            //request.addHeader("User-Agent", "Android-Chess");
            //request.addHeader("Connection", "close");

            return request;
        } catch (Exception ex){
            Log.e(TAG, "getDLNARenderRequest - " + ex.toString());
            return null;
        }
    }

	public void doRequest(final Object request) {

        if(request instanceof HttpGet){
            Log.i(TAG, "doRequest GET " + ((HttpGet)request).getURI());
        } else {
            Log.i(TAG, "doRequest POST " + ((HttpPost)request).getURI());
        }

		new Thread(new Runnable() {
			public void run() {
				// prepare data for threadhandler
				Message msg = new Message();
				Bundle bun = new Bundle();
				
				BufferedReader in = null;
				try {

					HttpClient client 	= new DefaultHttpClient();
					HttpResponse response;
                    if(request instanceof HttpGet){
                        response = client.execute((HttpGet)request);
                    } else {
                        response = client.execute((HttpPost)request);
                    }

                    Log.i(TAG, "Status: " + response.getStatusLine());
					Header[] responseHeaders = response.getAllHeaders();
					for(int i = 0; i < responseHeaders.length; i++){
						Log.i(TAG, "header: " + responseHeaders[i].getName() + ": " + responseHeaders[i].getValue()); 
						if(responseHeaders[i].getName().toLowerCase().equals("status")){
							Log.i(TAG, "Response status " + responseHeaders[i].getValue());
							bun.putInt("status", Integer.parseInt(responseHeaders[i].getValue()));
						}
					}
					
					in = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
					StringBuffer sb = new StringBuffer("");
					String line = "";
		
					while ((line = in.readLine()) != null) {
						sb.append(line + "\n");
					}
					in.close();
					
					msg.what 	= MSG_SERVER_RESPONSE;
					bun.putString("buffer", sb.toString());
		
				} catch (Exception ex) {
					msg.what 	= MSG_ERROR;
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
				m_threadHandler.sendMessage(msg);
			}
		}).start();
	}
	
}
