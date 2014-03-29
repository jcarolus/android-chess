package jwtc.android.chess.tools;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.*;

import android.os.Handler;
import android.os.Message;
import android.util.Log;


public abstract class PGNProcessor {

	protected Handler m_threadUpdateHandler;
	protected Thread m_thread = null;
	
	public static final int MSG_PROCESSED_PGN = 1;
	public static final int MSG_FAILED_PGN = 2;
	//public static final int MSG_EXCEPTION = 3;
	public static final int MSG_FINISHED = 4;
	public static final int MSG_PROCESSED_FILE = 5;
	public static final int MSG_FATAL_ERROR = 6;
	
	public void processZipFile(final InputStream is){
		
		m_thread = new Thread(new Runnable(){
        	public void run(){
				ZipInputStream zis = new ZipInputStream(is);
				ZipEntry entry;
				
				try {
					while((entry = zis.getNextEntry()) != null){
						if(entry.isDirectory() || (false == entry.getName().endsWith(".pgn"))) {
					        continue;
						} else {
							
							Log.i("hasEntry", entry.getName());
							
							StringBuffer sb = new StringBuffer();
							byte[] buffer = new byte[2048];
							int len;
						
							while((len = zis.read(buffer, 0, buffer.length)) != -1) {
								sb.append(new String(buffer, 0, len));
								
								processPGNPart(sb);
							}
							Message m = new Message();
							if(processPGN(sb.toString())){
								m.what = MSG_PROCESSED_PGN;
							} else {
								m.what = MSG_FAILED_PGN;
							}
							m_threadUpdateHandler.sendMessage(m);
							
							m = new Message();
							m.what = MSG_FINISHED;
							m_threadUpdateHandler.sendMessage(m);
						}
					}
				} catch (IOException e) {
					Message m = new Message();
					m.what = MSG_FATAL_ERROR;
					m_threadUpdateHandler.sendMessage(m);
					
					Log.e("PGNProcessor", e.toString());
				}
        	}
		});
		m_thread.start();
	}
	
	public void stopProcessing(){
		if(m_thread != null){
			m_thread.stop();
			m_thread = null;
		}
	}
	
	public void processPGNFile(final InputStream is){
		
		new Thread(new Runnable(){
        	public void run(){
        	
				try {
					
					StringBuffer sb = new StringBuffer();
					int len;
					byte[] buffer = new byte[2048];
					
					//Log.i("processPGN", "available " + is.available());
					
					while((len = is.read(buffer, 0, buffer.length)) != -1){
						sb.append(new String(buffer, 0, len));
						
						processPGNPart(sb);
						
						//Log.i("processPGN", "loop stringbuffer " + len);
					}
				
					Message m = new Message();
					if(processPGN(sb.toString())){
						m.what = MSG_PROCESSED_PGN;
					} else {
						m.what = MSG_FAILED_PGN;
					}
					m_threadUpdateHandler.sendMessage(m);
					
					m = new Message();
					m.what = MSG_FINISHED;
					m_threadUpdateHandler.sendMessage(m);
					
				} catch(Exception e){
					Message m = new Message();
					m.what = MSG_FATAL_ERROR;
					m_threadUpdateHandler.sendMessage(m);
					
					Log.e("PGNProcessor", e.toString());
				}
	        }
		}).start();
	}
	
	public void processPGNPart(StringBuffer sb){
		int pos1 = 0, pos2 = 0;
		String s;
		pos1 = sb.indexOf("[Event \"");
		while(pos1 >= 0){
			pos2 = sb.indexOf("[Event \"", pos1+10);
			if(pos2 == -1)
				break;
			s = sb.substring(pos1, pos2);
			
			Message m = new Message();
			
			if(processPGN(s)){
				m.what = MSG_PROCESSED_PGN;
			} else {
				m.what = MSG_FAILED_PGN;
			}
				
			m_threadUpdateHandler.sendMessage(m);
			
			sb.delete(0, pos2);
			
			pos1 = sb.indexOf("[Event \"");
		}
	}
	
	public abstract boolean processPGN(final String sPGN);
	public abstract String getString();
}
