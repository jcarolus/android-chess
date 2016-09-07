package jwtc.android.chess.ics;

import java.net.*;
import java.io.*;
import java.util.concurrent.ExecutionException;

import android.os.AsyncTask;
import android.util.Log;

//public class TelnetSocket extends Socket    
public class TelnetSocket extends jwtc.android.timeseal.TimesealingSocket
{
	private static final String TAG = "TelnetSocket";

	protected static byte[] _inBytes;
	protected static byte[] _outBytes;

	TelnetSocket (String host, int port) throws UnknownHostException, IOException{
		//super (host, port);
		super(InetAddress.getByName(host), port);
		//super (host, port, "TIMESTAMP|openseal|Running on Android|");
		
		_inBytes = new byte[2048];
		_outBytes = new byte[128];
		
		/// own openseal
		//String hello = "TIMESTAMP|openseal|Running on Android|";
		//sendString(hello);
	}
	
	public String readString(){
		String data = null;
		try	{
			InputStream is = getInputStream();
			if(is != null){
				int num = is.read (_inBytes);
				if(num > 0){
					data = new String(_inBytes, 0, num);
				}
			}
		} catch (Exception e)	{
			Log.e(TAG, "readString " + e.toString());
			return null;
		}
		return data;
	}
	
	public boolean sendString (String data){

		for (int i = 0; i < data.length(); i++)
		{
			_outBytes[i] = (byte)data.charAt(i);
			//Log.d(TAG, "_outBytes ->" + data.charAt(i) + "    i->" + i);
		}

		boolean result = false;
		ATsendString asObj = new ATsendString();
		try {
			 result = asObj.execute(data).get();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
		return result;
	}


	private class ATsendString extends AsyncTask<String, Void, Boolean>
	{

		@Override
		protected Boolean doInBackground(String... strings) {

			try
			{
				getOutputStream().write(_outBytes, 0, strings[0].length());
				getOutputStream().flush();
				//Log.i("TelnetSocket", "sendString: " + data);
				return true;
			}
			catch (Exception e)
			{
				Log.e("TelnetSocket", "sendString: " + e.toString());
				return false;
			}

		}

		@Override
		protected void onPostExecute(Boolean aBoolean) {
			super.onPostExecute(aBoolean);
		}
	}
}
