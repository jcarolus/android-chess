package jwtc.android.chess.tools;

import android.app.Instrumentation;
import android.os.Handler;
import android.os.Looper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Real JSON/PGN sources and main-thread progress delivery, without an Activity. */
public final class ImportPipelineInstrumentation {
    public static void verify(Instrumentation instrumentation) throws Exception {
        verifySource(
                instrumentation,
                new OpeningJsonSource(
                        input(
                                "[{\"name\":\"First\",\"moves\":\"1."
                                    + " e4\"},{\"name\":\"Second\",\"moves\":\"1. d4\"}]")),
                true);
        verifySource(
                instrumentation,
                PgnSource.file(input("[Event \"First\"]\n1. e4 *\n\n[Event \"Second\"]\n1. d4 *")),
                false);
        verifyOpeningBatch();
    }

    private static void verifyOpeningBatch() {
        ImportApi api = new ImportApi();
        OpeningImportHandler handler = new OpeningImportHandler(api);
        check(
                handler.handle(new ImportItem("1. e4 e5 (1... c5) 2. Nf3 *", "Opening"))
                        == ImportHandler.Result.INSERTED,
                "Legal opening was skipped");
        check(
                handler.handle(new ImportItem("1. e4 e5 2. Ng1f3 *", "Duplicate"))
                        == ImportHandler.Result.FAILED,
                "An opening at the same position was inserted twice");
        check(
                handler.handle(new ImportItem("1. d4 d5 (1... Ke4) *", "Illegal"))
                        == ImportHandler.Result.FAILED,
                "An illegal variation was accepted");
        check(api.getHashMap().size() == 1, "Opening import stored the wrong number of positions");
    }

    private static void verifySource(
            Instrumentation instrumentation, ImportSource source, boolean named) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<ImportPipeline> pipeline = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean();
        AtomicBoolean callbacksOnMain = new AtomicBoolean(true);
        List<ImportItem> items = new ArrayList<>();
        try {
            instrumentation.runOnMainSync(
                    () -> {
                        Handler main = new Handler(Looper.getMainLooper());
                        ImportListener listener =
                                new ImportListener() {
                                    private void checkThread() {
                                        if (Looper.myLooper() != Looper.getMainLooper()) {
                                            callbacksOnMain.set(false);
                                        }
                                    }

                                    public void OnImportStarted(int mode) {
                                        checkThread();
                                    }

                                    public void OnImportProgress(
                                            int mode,
                                            int succeeded,
                                            int failed,
                                            int duplicates,
                                            int updated) {
                                        checkThread();
                                    }

                                    public void OnImportFinished(int mode) {
                                        checkThread();
                                        completed.set(true);
                                        done.countDown();
                                    }

                                    public void OnImportFatalError(int mode) {
                                        checkThread();
                                        done.countDown();
                                    }
                                };
                        ImportPipeline owner =
                                new ImportPipeline(
                                        command -> new Thread(command, "ImportSourceCheck").start(),
                                        main::post,
                                        listener);
                        pipeline.set(owner);
                        owner.start(
                                4,
                                () ->
                                        new ImportJob(
                                                4,
                                                source,
                                                item -> {
                                                    if (Looper.myLooper()
                                                            == Looper.getMainLooper()) {
                                                        throw new AssertionError(
                                                                "Import handler ran on the main"
                                                                    + " thread");
                                                    }
                                                    items.add(item);
                                                    return ImportHandler.Result.INSERTED;
                                                }));
                    });
            check(done.await(10, TimeUnit.SECONDS), "Import did not complete");
            check(completed.get(), "Import source failed");
            check(callbacksOnMain.get(), "Progress was delivered off the main thread");
            check(items.size() == 2, "Import source lost an item");
            check(items.get(0).pgn.contains("1. e4"), "Source changed the first Game");
            check(items.get(1).pgn.contains("1. d4"), "Source changed the second Game");
            if (named) {
                check("First".equals(items.get(0).name), "Opening name was lost");
                check("Second".equals(items.get(1).name), "Opening order changed");
            }
        } finally {
            instrumentation.runOnMainSync(
                    () -> {
                        if (pipeline.get() != null) {
                            pipeline.get().cancel();
                        }
                    });
        }
    }

    private static ByteArrayInputStream input(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
