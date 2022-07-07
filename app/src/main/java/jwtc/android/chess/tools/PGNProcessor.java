package jwtc.android.chess.tools;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.*;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;


public abstract class PGNProcessor {
    private static final String TAG = "PGNProcessor";

    protected Handler m_threadUpdateHandler;
    protected Thread m_thread = null;
    protected int mode, successCount, failCount;

    public static final int MSG_STARTED = 1;
    public static final int MSG_PROCESSED_PGN = 2;
    public static final int MSG_FAILED_PGN = 3;
    public static final int MSG_FINISHED = 4;
    public static final int MSG_FATAL_ERROR = 5;

    PGNProcessor(int mode, Handler updateHandler) {
        this.mode = mode;
        this.successCount = 0;
        this.failCount = 0;
        this.m_threadUpdateHandler = updateHandler;
    }

    public void processZipFile(final InputStream is) {
        Log.d(TAG, "processZipfile");

        m_thread = new Thread(new Runnable() {
            public void run() {
                sendMessage(MSG_STARTED);
                ZipInputStream zis = new ZipInputStream(is);
                ZipEntry entry;

                try {
                    while ((entry = zis.getNextEntry()) != null) {
                        if (entry.isDirectory() || (false == entry.getName().endsWith(".pgn"))) {
                            continue;
                        } else {

                            Log.d(TAG, "hasEntry " + entry.getName());

                            StringBuffer sb = new StringBuffer();
                            byte[] buffer = new byte[2048];
                            int len;

                            while ((len = zis.read(buffer, 0, buffer.length)) != -1) {
                                sb.append(new String(buffer, 0, len));
                            }

                            processPGNPart(sb);

                            sendMessage(MSG_FINISHED);
                        }
                    }
                } catch (IOException e) {
                    sendMessage(MSG_FATAL_ERROR);

                    Log.e(TAG, "Failed " + e.toString());
                }
            }
        });
        m_thread.start();
    }

    public void stopProcessing() {
        if (m_thread != null) {
            m_thread.stop();
            m_thread = null;
        }
    }

    public void processPGNFile(final InputStream is) {
        Log.d(TAG, "processPGNFile");

        new Thread(new Runnable() {
            public void run() {
            sendMessage(MSG_STARTED);
                try {

                    StringBuffer sb = new StringBuffer();
                    int len;
                    byte[] buffer = new byte[2048];

                    while ((len = is.read(buffer, 0, buffer.length)) != -1) {
                        sb.append(new String(buffer, 0, len));

                        processPGNPart(sb);
                    }

                    sendMessage(MSG_FINISHED);

                } catch (Exception e) {
                    sendMessage(MSG_FATAL_ERROR);

                    Log.e(TAG, e.toString());
                }
            }
        }).start();
    }

    public void processPGNPart(final StringBuffer sb) {
        int pos1 = 0, pos2 = 0;
        String s;
        pos1 = sb.indexOf("[Event \"");
        while (pos1 >= 0) {
            pos2 = sb.indexOf("[Event \"", pos1 + 10);
            if (pos2 == -1)
                break;
            s = sb.substring(pos1, pos2);

            if (processPGN(s)) {
                successCount++;
                sendMessage(MSG_PROCESSED_PGN);
            } else {
                failCount++;
                sendMessage(MSG_FAILED_PGN);
            }

            sb.delete(0, pos2);

            pos1 = sb.indexOf("[Event \"");
        }
    }

    protected void sendMessage(int what) {
        Message m = new Message();
        Bundle data = new Bundle();
        data.putInt("mode", mode);
        data.putInt("successCount", successCount);
        data.putInt("failCount", failCount);
        m.what = what;
        m.setData(data);

        m_threadUpdateHandler.sendMessage(m);
    }

    public abstract boolean processPGN(final String sPGN);

    public abstract String getString();
}
