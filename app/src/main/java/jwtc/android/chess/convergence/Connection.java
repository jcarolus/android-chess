package jwtc.android.chess.convergence;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class Connection {

	public static final String TAG = "convergence.Connection";
	public static final int MSG_ERROR 				= -1;
	public static final int MSG_FOUND_DEVICE 		= 1;
	public static final int MSG_SERVER_RESPONSE		= 2;
	public static final int TYPE_CONVERGENCE		= 3;
	public static final int TYPE_DIAL				= 4;
	
	private String _serverIp, _appId = "Convergence_Tutorial_TV"; //"Chess_Convergence";
	private int _serverPort = 80, _msgNumber = 1;
	private int _connectionType = TYPE_CONVERGENCE;
	
	
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
					Log.i(TAG, "ThreadHandler got: " + msg.getData().getString("buffer"));
					Log.i(TAG, "Status " + msg.getData().getInt("status"));
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
					
					String ST = "urn:samsung.com:service:MultiScreenService:1";
					if(_connectionType == TYPE_DIAL){
						ST = "urn:dial-multiscreen-org:service:dial:1";
					}
					
					byte[] bufSend = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 3\r\nST: " + ST + "\r\n\r\n").getBytes();
					DatagramPacket packetSend = new DatagramPacket(bufSend, bufSend.length, serverAddr, 1900);
					
					socket.send(packetSend);
					
					Log.i(TAG, "Packet was send");
					
					byte[] bufReceive = new byte[1024];
					DatagramPacket packetReceive = new DatagramPacket(bufReceive, bufReceive.length);
					socket.receive(packetReceive);
					
					String s = new String(packetReceive.getData()).substring(0, packetReceive.getLength());
					
					Log.i(TAG, s);
					
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
			return new URI("http://" + _serverIp + ":" + _serverPort + "/" + url);
		}catch(Exception e){
			return null;
		}
	}
	
	public void connect(){
		HttpPost request = new HttpPost();
		
		if(_connectionType == TYPE_CONVERGENCE){
			request.setURI(getURI("ws/app/" + _appId + "/connect"));
			addConvergenceHeaders(request);
		} else {
			request.setURI(getURI("app/YouTube"));
		}
		doRequest(request);
	}
		
	public void disconnect(){

		HttpPost request = new HttpPost();
		request.setURI(getURI("ws/app/" + _appId + "/disconnect"));
		addConvergenceHeaders(request);
		
		doRequest(request);
	}
	
	public void queue(String sData){
		List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
		nameValuePairs.add(new BasicNameValuePair("data", sData));
		
		String s = "";
		for (int i = 0; i < nameValuePairs.size(); i++) {
			s += nameValuePairs.get(i) + "\n";
		}
		Log.i(TAG, "queue: " + s);
		
		HttpPost request = new HttpPost();
		request.setURI(getURI("ws/app/" + _appId + "/queue"));
		addConvergenceHeaders(request);
		try {
			request.setEntity(new UrlEncodedFormEntity(nameValuePairs));
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		doRequest(request);
	}
	
	private void addConvergenceHeaders(HttpPost request){
		request.setHeader("SLDeviceID", "12345");
		request.setHeader("VendorID", 	"VendorMe");
		request.setHeader("DeviceName", "IE-Client");
		request.setHeader("GroupID", 	"feiGroup");
		request.setHeader("ProductID", 	"SMARTDev");
		request.setHeader("connection", "close");
		request.setHeader("msgNumber", 	"" + _msgNumber);
	}
	
	public void doRequest(final HttpPost request) {
		
		new Thread(new Runnable() {
			public void run() {
				// prepare data for threadhandler
				Message msg = new Message();
				Bundle bun = new Bundle();
				
				BufferedReader in = null;
				try {
		
					_msgNumber++;
		
					HttpClient client 	= new DefaultHttpClient();
					HttpResponse response = client.execute(request);
					
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
