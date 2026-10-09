package jwtc.android.chess.engine;

import android.util.Log;

import java.util.ArrayList;

import jwtc.android.chess.services.GameApi;
import jwtc.chess.JNI;
import jwtc.chess.Move;
import jwtc.chess.Pos;

public class LocalEngine extends EngineApi {
    private static final String TAG = "LocalEngine";

    private final GameApi gameApi;
    private Thread enginePeekThread = null;
    private Thread engineSearchThread = null;
    private final ArrayList<Runnable> completionCallbacks = new ArrayList<>();
    private boolean stopping = false;

    public LocalEngine(GameApi gameApi) {
        this.gameApi = gameApi;
    }

    @Override
    public synchronized void play() {
        Log.d(TAG, "play " + msecs + ", " + ply);

        if (engineSearchThread != null || gameApi.isEnded()) {
            Log.d(TAG, "ended!");
            return;
        }

        long id = beginSearch(SearchPurpose.PLAY);
        engineSearchThread = new Thread(new RunnableSearch(id, ply, msecs, quiescentSearchOn));
        engineSearchThread.start();

        enginePeekThread = new Thread(new RunnablePeeker(id));
        enginePeekThread.start();
        for (EngineListener listener : listeners) {
            listener.OnEngineStarted();
        }
    }

    @Override
    public boolean supportsAnalysis() {
        return false;
    }

    @Override
    public synchronized void analyze(String fen, int timeMillis) {
        validateAnalysisRequest(fen, timeMillis);
        if (engineSearchThread != null) {
            return;
        }
        long id = beginSearch(SearchPurpose.ANALYSIS);
        // Placeholder only: do not touch the JNI board or start a native search.
        sendMessageFromThread(id, "", 0.0F);
    }

    @Override
    public synchronized boolean isReady() {
        return engineSearchThread == null && JNI.getInstance().peekSearchDone() == 1;
    }

    @Override
    public synchronized void stop(Runnable onDone) {
        if (engineSearchThread == null) {
            if (onDone != null) {
                updateHandler.post(onDone);
            }
            return;
        }
        if (onDone != null) {
            completionCallbacks.add(onDone);
        }
        if (stopping) {
            return;
        }
        stopping = true;
        final Thread search = engineSearchThread;
        // Native begin() resets the interrupt flag. Repeat until completion so a stop
        // requested just before entering JNI cannot be lost during native startup.
        new Thread(() -> {
            try {
                while (true) {
                    synchronized (LocalEngine.this) {
                        if (engineSearchThread != search) {
                            return;
                        }
                        JNI.getInstance().interrupt();
                    }
                    search.join(20);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }, "LocalEngine-stop").start();
    }

    @Override
    public synchronized void abort(Runnable onDone) {
        discardSearchResults();
        abortPeek();
        boolean wasBusy = engineSearchThread != null;
        stop(onDone);
        if (wasBusy) {
            for (EngineListener listener : listeners) {
                listener.OnEngineAborted();
            }
        }
    }

    @Override
    public synchronized void destroy() {
        abort(null);
    }

    private class RunnableSearch implements Runnable {
        private final long id;
        private final int searchPly;
        private final int searchMillis;
        private final boolean quiescent;

        RunnableSearch(long id, int searchPly, int searchMillis, boolean quiescent) {
            this.id = id;
            this.searchPly = searchPly;
            this.searchMillis = searchMillis;
            this.quiescent = quiescent;
        }

        @Override
        public void run() {
            try {
                JNI jni = JNI.getInstance();
                if (!isCurrentSearch(id) || gameApi.isEnded()) {
                    Log.d(TAG, "search called while game was ended");
                    return;
                }
                long lMillies = System.currentTimeMillis();
                if (searchPly > 0) {
                    jni.searchDepth(searchPly, quiescent ? 1 : 0);
                } else if (searchMillis > 0) {
                    jni.searchMove(searchMillis, quiescent ? 1 : 0);
                } else {
                    Log.d(TAG, "No ply and no msecs to work with");
                    return;
                }

                finishPeek();
                int move = jni.getMove();
                int value = jni.peekSearchBestValue();
                if (move != 0) {
                    sendMoveMessageFromThread(id, move, jni.getDuckMove(), value);
                }

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
                sendMessageFromThread(id, s, fValue);

            } catch (Exception ex) {
                ex.printStackTrace(System.out);
            } finally {
                finishPeek();
                synchronized (LocalEngine.this) {
                    engineSearchThread = null;
                    stopping = false;
                    for (Runnable callback : completionCallbacks) {
                        updateHandler.post(callback);
                    }
                    completionCallbacks.clear();
                }
            }
        }
    }

    private class RunnablePeeker implements Runnable {
        private final long id;

        RunnablePeeker(long id) {
            this.id = id;
        }

        @Override
        public void run() {
            try {
                JNI jni = JNI.getInstance();
                Thread.sleep(300);

                int move, value, ply = 1, j, iSleep = 1000, duckMove;
                String s;
                float fValue;
                while (isCurrentSearch(id) && jni.peekSearchDone() == 0 && !Thread.currentThread().isInterrupted()) {
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

                    sendMessageFromThread(id, s, fValue);

                    Thread.sleep(iSleep);
                }

                ///////////////////////////////////////////////////////////////////////
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (Exception ex) {
                ex.printStackTrace(System.out);
            }
        }
    }

    /** Called by the search worker before its final result and completion callbacks. */
    private void finishPeek() {
        final Thread peeker;
        synchronized (this) {
            peeker = enginePeekThread;
            enginePeekThread = null;
        }
        if (peeker != null) {
            peeker.interrupt();
            try {
                peeker.join();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
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
