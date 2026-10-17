package jwtc.android.chess.tools;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Runs one {@link ImportJob} at a time on its own thread, and reports to the listener on the main
 * thread. Every job starts with fresh counts. Cancelling stops between two items, never in the
 * middle of one.
 */
public final class ImportPipeline {
    private static final String TAG = "ImportPipeline";

    private final ImportListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean running = false;
    private volatile boolean cancelled = false;

    public ImportPipeline(ImportListener listener) {
        this.listener = listener;
    }

    /**
     * Starts the job. While another job runs, the new one is reported as a fatal error for its
     * mode and the running job carries on.
     *
     * @return whether the job started
     */
    public boolean start(ImportJob job) {
        synchronized (this) {
            if (running) {
                try {
                    job.source.close();
                } catch (Exception e) {
                    Log.e(TAG, "Could not close the source of a rejected job " + e);
                }
                mainHandler.post(() -> listener.OnImportFatalError(job.mode));
                return false;
            }
            running = true;
            cancelled = false;
        }
        new Thread(() -> run(job), "ImportPipeline").start();
        return true;
    }

    /** Asks the running job, if any, to stop after the item it is on; no further events follow. */
    public void cancel() {
        cancelled = true;
    }

    private void run(ImportJob job) {
        final int mode = job.mode;
        final int[] counts = new int[2]; // succeeded, failed
        mainHandler.post(() -> listener.OnImportStarted(mode));
        try {
            job.source.forEach(item -> {
                if (cancelled) {
                    return false;
                }
                if (job.handler.handle(item)) {
                    counts[0]++;
                } else {
                    counts[1]++;
                }
                final int succeeded = counts[0];
                final int failed = counts[1];
                mainHandler.post(() -> {
                    if (!cancelled) {
                        listener.OnImportProgress(mode, succeeded, failed);
                    }
                });
                return true;
            });
            mainHandler.post(() -> {
                if (!cancelled) {
                    listener.OnImportFinished(mode);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Import failed " + e);
            mainHandler.post(() -> {
                if (!cancelled) {
                    listener.OnImportFatalError(mode);
                }
            });
        } finally {
            synchronized (this) {
                running = false;
            }
        }
    }
}
