package jwtc.android.chess.engine;

import jwtc.chess.JNI;
import jwtc.chess.Move;

public class LocalEngine extends EngineApi {
    private static final String TAG = "LocalEngine";
    private int msecs = 0;
    private int ply = 0;
    private Thread engineThread = null;

    @Override
    public void play(int msecs, int ply) {
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
                JNI _jni = JNI.getInstance();
                if (_jni.isEnded() != 0)
                    return;


                if (ply > 0) {
                    _jni.searchDepth(ply);
                    int move = _jni.getMove();
                    sendMoveMessageFromThread(move);

                    int evalCnt = _jni.getEvalCount();
                    String s;

                    if (evalCnt == 0) {
                        s = "From opening book";
                    } else {
                        s = "Searched at " + ply + " ply";
                    }
                    sendMessageFromThread(s);

                } else {
                    _jni.searchMove(msecs);

                    long lMillies = System.currentTimeMillis();
                    int move, tmpMove, value, ply = 1, evalCnt, j, iSleep = 1000, iNps;
                    String s;
                    float fValue;
                    while (_jni.peekSearchDone() == 0) {
                        Thread.sleep(iSleep);

                        ply = _jni.peekSearchDepth();

                        value = _jni.peekSearchBestValue();
                        evalCnt = _jni.getEvalCount();
                        fValue = (float) value / 100.0F;

                        s = "";

                        if (ply > 5) {
                            ply = 5;
                        }
                        for (j = 0; j < ply; j++) {
                            tmpMove = _jni.peekSearchBestMove(j);
                            if (tmpMove != 0)
                                s += Move.toDbgString(tmpMove).replace("[", "").replace("]", "") + " ";
                        }
                        if (ply == 5) {
                            s += "...";
                        }

                        s = s + "\n\t" + String.format("%.2f", fValue) /*+ "\t@ " + ply*/;

                        sendMessageFromThread(s);
                    }

                    move = _jni.getMove();
                    sendMoveMessageFromThread(move);

                    value = _jni.peekSearchBestValue();
                    fValue = (float) value / 100.0F;
                    evalCnt = _jni.getEvalCount();

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
