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
    private volatile long nativeSearchId = 0;

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

        long token = JNI.getInstance().prepareSearch(ply, msecs, quiescentSearchOn ? 1 : 0);
        if (token == 0) return;
        nativeSearchId = token;
        long id = beginSearch(SearchPurpose.PLAY);
        engineSearchThread = new Thread(new RunnableSearch(id, token));
        engineSearchThread.start();

        enginePeekThread = new Thread(new RunnablePeeker(id, token));
        enginePeekThread.start();
        for (EngineListener listener : listeners) {
            listener.OnEngineStarted();
        }
    }

    @Override
    protected boolean canDeliverMove(long id) {
        return isCurrentSearch(id) && JNI.getInstance().isSearchCurrent(nativeSearchId);
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
        JNI.getInstance().interruptSearch(nativeSearchId);
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
        private final long token;

        RunnableSearch(long id, long token) {
            this.id = id;
            this.token = token;
        }

        @Override
        public void run() {
            try {
                JNI jni = JNI.getInstance();
                long lMillies = System.currentTimeMillis();
                jni.runSearch(token);

                finishPeek();
                int[] result = jni.getSearchResult(token);
                int move = result[0];
                int value = result[2];
                if (move != 0) {
                    sendMoveMessageFromThread(id, move, result[1], value);
                }

                float fValue = (float) value / 100.0F;
                int evalCnt = result[4];

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

        private final long token;

        RunnablePeeker(long id, long token) {
            this.id = id;
            this.token = token;
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
                    int[] result = jni.getSearchResult(token);
                    ply = result[3];
                    value = result[2];
                    fValue = (float) value / 100.0F;

                    s = "";

                    if (ply > 5) {
                        ply = 5;
                    }
                    for (j = 0; j < ply; j++) {
                        move = result[5 + j];
                        if (move != 0) {
                            s += Move.toDbgString(move).replace("[", "").replace("]", "");
                            duckMove = result[25 + j];
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
