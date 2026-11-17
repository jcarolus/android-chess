package jwtc.android.chess.tools;

import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns one accepted Import job and its replayable progress. Admission, cancellation and replay are
 * called on the delivery thread; its executor must preserve submission order. Sources and handlers
 * run on the worker. Cancellation takes effect between items and suppresses queued delivery for
 * that job.
 */
public final class ImportPipeline {
    public interface JobFactory {
        ImportJob create() throws Exception;
    }

    private static final Logger LOG = Logger.getLogger(ImportPipeline.class.getName());
    private final Executor worker;
    private final Executor delivery;
    private final ImportListener listener;
    private Job latest;
    private Job active;

    private static final class Job {
        final int mode;
        volatile boolean cancelled;
        boolean workerFinished;
        int succeeded;
        int failed;
        int duplicates;
        int updated;
        Boolean outcome;

        Job(int mode) {
            this.mode = mode;
        }
    }

    public ImportPipeline(Executor worker, Executor delivery, ImportListener listener) {
        this.worker = worker;
        this.delivery = delivery;
        this.listener = listener;
    }

    /** Rejects busy requests before opening a source or creating a handler. */
    public synchronized boolean start(int mode, JobFactory factory) {
        if (active != null) {
            listener.OnImportRejected(mode);
            return false;
        }
        Job job = new Job(mode);
        active = latest = job;
        listener.OnImportStarted(mode);
        try {
            ImportJob input = factory.create();
            try {
                worker.execute(() -> run(job, input));
            } catch (RuntimeException error) {
                close(input.source);
                throw error;
            }
        } catch (Exception error) {
            LOG.log(Level.WARNING, "Could not start Import", error);
            finish(job, false);
        }
        return true;
    }

    /** Replays the latest accepted job; rejected requests never replace it. */
    public synchronized void replay(ImportListener target) {
        Job job = latest;
        if (job == null || job.cancelled) {
            return;
        }
        if (job.outcome == null) {
            target.OnImportStarted(job.mode);
        }
        if (job.outcome == null || job.outcome) {
            target.OnImportProgress(
                    job.mode, job.succeeded, job.failed, job.duplicates, job.updated);
        }
        if (Boolean.TRUE.equals(job.outcome)) {
            target.OnImportFinished(job.mode);
        } else if (Boolean.FALSE.equals(job.outcome)) {
            target.OnImportFatalError(job.mode);
        }
    }

    public synchronized void cancel() {
        if (active != null) {
            active.cancelled = true;
            if (active.workerFinished) {
                active = null;
            }
        }
    }

    private void run(Job job, ImportJob input) {
        int[] counts = new int[ImportHandler.Result.values().length];
        boolean succeeded = false;
        try {
            input.source.forEach(
                    item -> {
                        if (job.cancelled) {
                            return false;
                        }
                        counts[input.handler.handle(item).ordinal()]++;
                        int imported = counts[ImportHandler.Result.INSERTED.ordinal()];
                        int failed = counts[ImportHandler.Result.FAILED.ordinal()];
                        int duplicates = counts[ImportHandler.Result.DUPLICATE.ordinal()];
                        int updated = counts[ImportHandler.Result.UPDATED.ordinal()];
                        delivery.execute(
                                () -> progress(job, imported, failed, duplicates, updated));
                        return !job.cancelled;
                    });
            succeeded = true;
        } catch (Exception error) {
            LOG.log(Level.WARNING, "Import failed", error);
        } finally {
            // Ensure accepted sources are closed even when reading fails.
            close(input.source);
            synchronized (this) {
                job.workerFinished = true;
                if (job.cancelled && active == job) {
                    active = null;
                }
            }
        }
        boolean outcome = succeeded;
        delivery.execute(() -> finish(job, outcome));
    }

    private synchronized void progress(
            Job job, int succeeded, int failed, int duplicates, int updated) {
        if (latest != job || job.cancelled || job.outcome != null) {
            return;
        }
        job.succeeded = succeeded;
        job.failed = failed;
        job.duplicates = duplicates;
        job.updated = updated;
        listener.OnImportProgress(job.mode, succeeded, failed, duplicates, updated);
    }

    private synchronized void finish(Job job, boolean succeeded) {
        if (latest != job || job.cancelled || job.outcome != null) {
            return;
        }
        job.outcome = succeeded;
        if (active == job) {
            active = null;
        }
        if (succeeded) {
            listener.OnImportFinished(job.mode);
        } else {
            listener.OnImportFatalError(job.mode);
        }
    }

    private static void close(ImportSource source) {
        try {
            source.close();
        } catch (Exception error) {
            LOG.log(Level.WARNING, "Could not close Import source", error);
        }
    }
}
