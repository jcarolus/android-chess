package jwtc.android.chess.engine;

import android.util.Log;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import jwtc.chess.JNI;
import jwtc.chess.Move;

public class LocalEngine extends EngineApi {
    private static final String TAG = "LocalEngine";

    private Thread engineThread = null;

    public void setOpeningDb(String sFileName) {
        Log.d(TAG, "setOpeningDb " + sFileName);
        JNI jni = JNI.getInstance();
        jni.loadDB(sFileName, 17); // todo - number of plies
    }

    public void installDb(final InputStream in, final String outFilename) {
        Log.d(TAG, "installDb " + outFilename);

        new Thread(new Runnable() {
            public void run() {
                try {
                    OutputStream out = new FileOutputStream(outFilename);
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }

                    out.close();
                    in.close();

                    setOpeningDb(outFilename);

                } catch (Exception e) {
                    Log.d(TAG, "installDb exception: " + e.getMessage());
                }
            }
        }).start();
    }

    @Override
    public void play() {
        Log.d(TAG, "play " + msecs + ", " + ply);
        for (EngineListener listener: listeners) {
            listener.OnEngineStarted();
        }

        engineThread = new Thread(new RunnableImp());
        engineThread.start();
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void abort() {
        if (engineThread != null) {
            try {
                synchronized (this) {
                    engineThread.interrupt();
                    JNI.getInstance().interrupt();
                }
                engineThread.join();
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    @Override
    public void destroy() {
        engineThread = null;
    }

    private class RunnableImp implements Runnable {
        @Override
        public void run() {
            try {
                JNI jni = JNI.getInstance();
                if (jni.isEnded() != 0)
                    return;

                if (ply > 0) {
                    jni.searchDepth(ply);
                } else {
                    jni.searchMove(msecs);
                }

                long lMillies = System.currentTimeMillis();
                int move, tmpMove, value, ply = 1, evalCnt, j, iSleep = 1000, iNps;
                String s;
                float fValue;
                while (jni.peekSearchDone() == 0) {
                    Thread.sleep(iSleep);

                    ply = jni.peekSearchDepth();

                    value = jni.peekSearchBestValue();
//                    evalCnt = jni.getEvalCount();
                    fValue = (float) value / 100.0F;

                    s = "";

                    if (ply > 5) {
                        ply = 5;
                    }
                    for (j = 0; j < ply; j++) {
                        tmpMove = jni.peekSearchBestMove(j);
                        if (tmpMove != 0)
                            s += Move.toDbgString(tmpMove).replace("[", "").replace("]", "") + " ";
                    }
                    if (ply == 5) {
                        s += "...";
                    }

                    s = s + "\n\t" + String.format("%.2f", fValue) /*+ "\t@ " + ply*/;

                    sendMessageFromThread(s);
                }
                move = jni.getMove();
                sendMoveMessageFromThread(move);

                value = jni.peekSearchBestValue();
                fValue = (float) value / 100.0F;
                evalCnt = jni.getEvalCount();

                if (evalCnt == 0) {
                    s = "From opening book";
                } else {
                    //s = "";
                    int iTime = (int) ((System.currentTimeMillis() - lMillies) / 1000);
                    iNps = (int) (evalCnt / iTime);
                    s = iNps + " N/s (" + iTime + " s)" + "\n\t" + String.format("%.2f", fValue);
                }
                sendMessageFromThread(s);

                ///////////////////////////////////////////////////////////////////////
            } catch (Exception ex) {
                ex.printStackTrace(System.out);
            }
        }
    }
}
