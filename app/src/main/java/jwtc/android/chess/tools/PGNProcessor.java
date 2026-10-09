package jwtc.android.chess.tools;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import jwtc.chess.PgnSplitter;


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
                        }

                        Log.d(TAG, "hasEntry " + entry.getName());

                        // the reader is not closed, that would close the zip stream with the entries still to read
                        processGames(new InputStreamReader(zis, StandardCharsets.UTF_8));
                    }
                    sendMessage(MSG_FINISHED);
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
                    processGames(new InputStreamReader(is, StandardCharsets.UTF_8));

                    sendMessage(MSG_FINISHED);

                } catch (Exception e) {
                    sendMessage(MSG_FATAL_ERROR);

                    Log.e(TAG, e.toString());
                }
            }
        }).start();
    }

    private void processGames(Reader reader) throws IOException {
        PgnSplitter splitter = new PgnSplitter(reader);
        String game;
        while ((game = splitter.next()) != null) {
            if (processPGN(game)) {
                successCount++;
                sendMessage(MSG_PROCESSED_PGN);
            } else {
                failCount++;
                sendMessage(MSG_FAILED_PGN);
            }
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
}
