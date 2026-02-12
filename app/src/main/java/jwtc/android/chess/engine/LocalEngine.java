package jwtc.android.chess.engine;

import android.util.Log;

import jwtc.android.chess.services.GameApi;
import jwtc.chess.JNI;
import jwtc.chess.Move;
import jwtc.chess.Pos;

public class LocalEngine extends EngineApi {
    private static final String TAG = "LocalEngine";

    private final GameApi gameApi;
    private Thread enginePeekThread = null;
    private Thread engineSearchThread = null;

    public LocalEngine(GameApi gameApi) {
        this.gameApi = gameApi;
    }

    @Override
    public void play() {
        Log.d(TAG, "play " + msecs + ", " + ply);

        if (gameApi.isEnded()) {
            Log.d(TAG, "ended!");
            return;
        }

        for (EngineListener listener : listeners) {
            listener.OnEngineStarted();
        }

        engineSearchThread = new Thread(new RunnableSearch());
        engineSearchThread.start();

        enginePeekThread = new Thread(new RunnablePeeker());
        enginePeekThread.start();
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void abort() {
        abortPeek();

        if (engineSearchThread != null) {
            Log.d(TAG, "abort");

            synchronized (this) {
                engineSearchThread.interrupt();
                JNI.getInstance().interrupt();
            }

            for (EngineListener listener : listeners) {
                listener.OnEngineAborted();
            }
        }
    }

    @Override
    public void destroy() {
        enginePeekThread = null;
        engineSearchThread = null;
    }

    private class RunnableSearch implements Runnable {
        @Override
        public void run() {
            try {
                JNI jni = JNI.getInstance();
                if (gameApi.isEnded()) {
                    Log.d(TAG, "search called while game was ended");
                    return;
                }
                long lMillies = System.currentTimeMillis();
                if (ply > 0) {
                    jni.searchDepth(ply, quiescentSearchOn ? 1 : 0);
                } else if (msecs > 0) {
                    jni.searchMove(msecs, quiescentSearchOn ? 1 : 0);
                } else {
                    Log.d(TAG, "No ply and no msecs to work with");
                    return;
                }

                int move = jni.getMove();
                int value = jni.peekSearchBestValue();
                sendMoveMessageFromThread(move, jni.getDuckMove(), value);

                float fValue = (float) value / 100.0F;
                int evalCnt = jni.getEvalCount();

                String s;
                if (evalCnt == 0) {
                    s = "From opening book";
                } else {
                    //s = "";
                    int iTime = (int) ((System.currentTimeMillis() - lMillies) / 1000);
                    if (iTime > 0) {
                        int iNps = (int) (evalCnt / iTime);
                        s = iNps + " N/s (" + iTime + " s)";
                    } else {
                        s = evalCnt + " N";
                    }
                    s += "\n\t" + String.format("%.2f", fValue);
                }
                sendMessageFromThread(s, fValue);

            } catch (Exception ex) {
                ex.printStackTrace(System.out);
            }
        }
    }

    private class RunnablePeeker implements Runnable {
        @Override
        public void run() {
            try {
                JNI jni = JNI.getInstance();
                Thread.sleep(300);

                int move, value, ply = 1, j, iSleep = 1000, duckMove;
                String s;
                float fValue;
                while (jni.peekSearchDone() == 0 && !Thread.currentThread().isInterrupted()) {
                    ply = jni.peekSearchDepth();

                    value = jni.peekSearchBestValue();
                    fValue = (float) value / 100.0F;

                    s = "";

                    if (ply > 5) {
                        ply = 5;
                    }
                    for (j = 0; j < ply; j++) {
                        move = jni.peekSearchBestMove(j);
                        if (move != 0) {
                            s += Move.toDbgString(move).replace("[", "").replace("]", "");
                            duckMove = jni.peekSearchBestDuckMove(j);
                            if (duckMove != -1) {
                                s += "@" + Pos.toString(duckMove);
                            }
                            s += " ";
                        }
                    }

                    sendMessageFromThread(s, fValue);

                    Thread.sleep(iSleep);
                }

                ///////////////////////////////////////////////////////////////////////
            } catch (Exception ex) {
                ex.printStackTrace(System.out);
            }
        }
    }

    private void abortPeek() {
        if (enginePeekThread != null) {
            synchronized (this) {
                enginePeekThread.interrupt();
            }
        }
    }
}
