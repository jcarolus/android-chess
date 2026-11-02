package jwtc.android.chess.tools;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.Test;

public class ImportPipelineTest {
    private final Queue worker = new Queue();
    private final Queue delivery = new Queue();
    private final Events events = new Events();
    private final ImportPipeline pipeline = new ImportPipeline(worker, delivery, events);

    private static class Queue implements Executor {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        public void execute(Runnable task) {
            tasks.add(task);
        }

        void drain() {
            while (!tasks.isEmpty()) {
                tasks.remove().run();
            }
        }
    }

    private static class Events implements ImportListener {
        final List<String> values = new ArrayList<>();

        public void OnImportStarted(int mode) {
            values.add("start:" + mode);
        }

        public void OnImportProgress(int mode, int ok, int failed) {
            values.add("progress:" + mode + ":" + ok + ":" + failed);
        }

        public void OnImportFinished(int mode) {
            values.add("finish:" + mode);
        }

        public void OnImportFatalError(int mode) {
            values.add("error:" + mode);
        }

        public void OnImportRejected(int mode) {
            values.add("reject:" + mode);
        }
    }

    private static class Input extends ByteArrayInputStream {
        boolean closed;

        Input(String text) {
            super(text.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private ImportJob job(int mode, String pgn, ImportHandler handler) {
        return new ImportJob(
                mode, PgnSource.file(new Input("[Event \"Import\"]\n" + pgn)), handler);
    }

    private void complete() {
        worker.drain();
        delivery.drain();
    }

    @Test
    public void busyRejectionDoesNotConstructOrReplaceTheAcceptedJob() {
        assertTrue(pipeline.start(2, () -> job(2, "1. e4 *", item -> true)));
        assertFalse(
                pipeline.start(
                        4,
                        () -> {
                            throw new AssertionError("Rejected request opened a source");
                        }));
        Events attached = new Events();
        pipeline.replay(attached);
        assertEquals(List.of("start:2", "progress:2:0:0"), attached.values);
        assertEquals(List.of("start:2", "reject:4"), events.values);
        complete();
        assertEquals("finish:2", events.values.get(events.values.size() - 1));
        attached.values.clear();
        pipeline.replay(attached);
        assertEquals(List.of("finish:2"), attached.values);
    }

    @Test
    public void countsAndLateListenerReplayBelongToTheAcceptedJob() {
        int[] handled = {0};
        pipeline.start(
                2,
                () ->
                        job(
                                2,
                                "[Event \"One\"]\n1. e4 *\n\n[Event \"Two\"]\n1. d4 *",
                                item -> ++handled[0] == 1));
        worker.drain();
        delivery.tasks.remove().run();
        Events attached = new Events();
        pipeline.replay(attached);
        assertEquals(List.of("start:2", "progress:2:1:0"), attached.values);
        delivery.drain();
        assertTrue(events.values.contains("progress:2:1:1"));
        assertEquals("finish:2", events.values.get(events.values.size() - 1));
    }

    @Test
    public void cancelAfterWorkerFinishesSuppressesQueuedEventsAndAllowsNextJob() {
        pipeline.start(2, () -> job(2, "1. e4 *", item -> true));
        worker.drain();
        pipeline.cancel();
        assertTrue(pipeline.start(3, () -> job(3, "1. d4 *", item -> false)));
        complete();
        assertEquals(List.of("start:2", "start:3", "progress:3:0:1", "finish:3"), events.values);
    }

    @Test
    public void cancelDuringAnItemFinishesItButDoesNotHandleTheNextItem() {
        int[] handled = {0};
        Input input = new Input("[Event \"One\"]\n1. e4 *\n\n[Event \"Two\"]\n1. d4 *");
        pipeline.start(
                2,
                () ->
                        new ImportJob(
                                2,
                                PgnSource.file(input),
                                item -> {
                                    handled[0]++;
                                    pipeline.cancel();
                                    return true;
                                }));
        complete();
        assertEquals(1, handled[0]);
        assertTrue(input.closed);
        assertEquals(List.of("start:2"), events.values);
        Events attached = new Events();
        pipeline.replay(attached);
        assertTrue(attached.values.isEmpty());
        assertTrue(pipeline.start(3, () -> job(3, "1. d4 *", item -> true)));
        complete();
        assertEquals("finish:3", events.values.get(events.values.size() - 1));
    }

    @Test
    public void cancellationBeforeReadingKeepsTheSlotUntilTheWorkerStops() {
        Input input = new Input("[Event \"Import\"]\n1. e4 *");
        pipeline.start(
                2,
                () ->
                        new ImportJob(
                                2,
                                PgnSource.file(input),
                                item -> {
                                    throw new AssertionError("Cancelled job processed an item");
                                }));
        pipeline.cancel();
        assertFalse(
                pipeline.start(
                        3,
                        () -> {
                            throw new AssertionError("Worker is still reserved");
                        }));
        complete();
        assertTrue(input.closed);
        assertEquals(List.of("start:2", "reject:3"), events.values);
        assertTrue(pipeline.start(3, () -> job(3, "1. d4 *", item -> true)));
        complete();
    }

    @Test
    public void openingFailureIsReplayableAndDoesNotReserveTheWorker() {
        pipeline.start(
                2,
                () -> {
                    throw new IOException("Cannot open input");
                });
        Events attached = new Events();
        pipeline.replay(attached);
        assertEquals(List.of("error:2"), attached.values);
        assertTrue(worker.tasks.isEmpty());
        assertTrue(pipeline.start(4, () -> job(4, "1. e4 *", item -> true)));
        complete();
    }

    @Test
    public void sourceFailureClosesInputAndIsReplayable() {
        Input input = new Input("[Event \"Import\"]\n1. e4 *");
        pipeline.start(
                2,
                () ->
                        new ImportJob(
                                2,
                                PgnSource.file(input),
                                item -> {
                                    throw new IllegalArgumentException("Rejected");
                                }));
        complete();
        assertTrue(input.closed);
        Events attached = new Events();
        pipeline.replay(attached);
        assertEquals(List.of("error:2"), attached.values);
    }

    @Test
    public void rejectedWorkerClosesTheAcceptedSource() {
        Input input = new Input("[Event \"Import\"]\n1. e4 *");
        ImportPipeline rejecting =
                new ImportPipeline(
                        task -> {
                            throw new java.util.concurrent.RejectedExecutionException();
                        },
                        delivery,
                        events);
        rejecting.start(2, () -> new ImportJob(2, PgnSource.file(input), item -> true));
        assertTrue(input.closed);
        assertEquals(List.of("start:2", "error:2"), events.values);
    }
}
