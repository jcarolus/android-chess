package jwtc.android.chess.engine;

import android.util.Log;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import jwtc.chess.JNI;
import jwtc.chess.Move;
import jwtc.chess.Pos;

public class LocalEngine extends EngineApi {
    private static final String TAG = "LocalEngine";

    private Thread enginePeekThread = null;
    private Thread engineSearchThread = null;

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

        JNI jni = JNI.getInstance();
        if (jni.isEnded() != 0) {
            Log.d(TAG, "ended!");
            return;
        }

        for (EngineListener listener: listeners) {
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

            try {
                synchronized (this) {
                    engineSearchThread.interrupt();
                    JNI.getInstance().interrupt();
                }
                engineSearchThread.join();

            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            for (EngineListener listener: listeners) {
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
                if (jni.isEnded() != 0) {
                    Log.d(TAG, "search called while game was ended");
                    return;
                }
                long lMillies = System.currentTimeMillis();
                if (ply > 0) {
                    jni.searchDepth(ply, quiescentSearchOn ? 1 : 0);
                } else if (msecs > 0){
                    jni.searchMove(msecs, quiescentSearchOn ? 1 : 0);
                } else {
                    Log.d(TAG, "No ply and no msecs to work with");
                    return;
                }

                int move = jni.getMove();
                sendMoveMessageFromThread(move, jni.getDuckMove());

                int value = jni.peekSearchBestValue();
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
                sendMessageFromThread(s);

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
                while (jni.peekSearchDone() == 0) {
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

                    s = s + "\t\t" + String.format("%.2f", fValue) /*+ "\t@ " + ply*/;

                    sendMessageFromThread(s);

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
            try {
                synchronized (this) {
                    enginePeekThread.interrupt();
                }
                enginePeekThread.join();

            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
}
