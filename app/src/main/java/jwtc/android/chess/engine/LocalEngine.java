package jwtc.android.chess.engine;

import android.util.Log;

import jwtc.chess.JNI;
import jwtc.chess.Move;

public class LocalEngine extends EngineApi {
    private static final String TAG = "LocalEngine";
    private int msecs = 0;
    private int ply = 0;
    private Thread engineThread = null;

    @Override
    public void play(int msecs, int ply) {
        Log.d(TAG, "play " + msecs + ", " + ply);
        this.msecs = msecs;
        this.ply = ply;

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
                    int move = jni.getMove();
                    sendMoveMessageFromThread(move);

                    int evalCnt = jni.getEvalCount();
                    String s;

                    if (evalCnt == 0) {
                        s = "From opening book";
                    } else {
                        s = "Searched at " + ply + " ply";
                    }
                    sendMessageFromThread(s);

                } else {
                    jni.searchMove(msecs / 1000);

                    long lMillies = System.currentTimeMillis();
                    int move, tmpMove, value, ply = 1, evalCnt, j, iSleep = 1000, iNps;
                    String s;
                    float fValue;
                    while (jni.peekSearchDone() == 0) {
                        Thread.sleep(iSleep);

                        ply = jni.peekSearchDepth();

                        value = jni.peekSearchBestValue();
                        evalCnt = jni.getEvalCount();
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

                } // else to level = 1
                ///////////////////////////////////////////////////////////////////////
            } catch (Exception ex) {
                ex.printStackTrace(System.out);
            }
        }
    }
}
