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

import android.util.Log;

public class RestServer {
	private Thread m_thread;
	public static final String TAG = "RestServer";
	protected JNI _jni;
	protected int _portNumber;
	protected ServerSocket _serverSocket;
	protected boolean _run;
	
	public RestServer(int portNumber){
		//_jni = jni;
		_portNumber 	= portNumber;
		_run 			= true;
		_jni 			= new JNI();
		_serverSocket	= null;
	}
	
	public boolean start(){
		
		_run = true;
		
		try{
			_serverSocket = new ServerSocket(_portNumber);
		} catch(Exception ex){
			_serverSocket = null;
			Log.e(TAG, ex.toString());
			return false;
		}
		
		m_thread = new Thread(new Runnable(){

 			public void run() {
 				int i = 0;
 				try {
 					Log.i(TAG, "Trying to open socket");
	 				
	 				
	 				while(_run){
		 				Log.i(TAG, "Waiting for client socket");
		 			    Socket clientSocket = _serverSocket.accept();
		 			    Log.i(TAG, "Accepted");
		 			    PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
		 			    BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
		 			    Log.i(TAG, "Start read from client");
		 			    
		 			    boolean bValid = false;
		 			    String inputLine;
		 			    while ((inputLine = in.readLine()) != null) {
		 				   Log.i(TAG, "Got [" + inputLine + "]");
		 				   if(inputLine.indexOf("GET / ") == 0){
		 					   Log.i(TAG, "Valid request");
		 					  bValid = true;
		 				   }
		 				   if(inputLine.length() == 0){
		 					   break;
		 				   }
		 			    }
		 			    String outputLine;
		 			    if(bValid){
		 			    	outputLine = "HTTP/1.0 200 OK\r\n\r\nChessCallBack({\"FEN\":\"" + _jni.toFEN() + "\"});\r\n\r\n";
		 			    	i++;
		 			    } else {
		 			    	outputLine = "HTTP/1.0 200 OK\r\n\r\n-\r\n\r\n";
		 			    }
		 			    Log.i(TAG, "Writing response " + i);
		 			    out.println(outputLine);
		 			    clientSocket.close();
		 			    Log.i(TAG, "Closed client socket");
	 				}
	 				_serverSocket.close();
 				} catch(Exception ex){
 					Log.e(TAG, "Exception " + ex);
 				}
 			}
		});
		
		m_thread.start();
		
		return true;
	}
	
	public boolean isAlive(){
		if(m_thread == null){
			Log.i(TAG, "isAlive -> thread = null");
			return false;
		}
		if(m_thread.isAlive()){
			Log.i(TAG, "isAlive -> thread = " + m_thread.isAlive() + ", serversocket " + (_serverSocket != null));
			return  _serverSocket != null && (false == _serverSocket.isClosed());
		}
		return false;
	}
	
	public void stop(){
		_run = false;
		if(_serverSocket != null && (false == _serverSocket.isClosed())){
			try{
				_serverSocket.close();
			}catch(Exception ex){}
		}
		_serverSocket = null;
		m_thread = null;
	}
	
	public static String getIpAddress() { 
        try {
            for (Enumeration en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = (NetworkInterface)en.nextElement();
                for (Enumeration enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = (InetAddress)enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        String ipAddress=inetAddress.getHostAddress().toString();
                        Log.i("IP address",""+ipAddress);
                        if(ipAddress.startsWith("192.168.") || ipAddress.startsWith("172.16.") || ipAddress.startsWith("10.0.")){ 
                        	return ipAddress;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            Log.e("Socket exception in GetIP Address of Utilities", ex.toString());
        }
        return null; 
	}
}
