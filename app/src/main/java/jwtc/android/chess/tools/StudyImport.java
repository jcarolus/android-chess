package jwtc.android.chess.tools;

import android.content.Context;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import jwtc.android.chess.study.StudyPgn;
import jwtc.android.chess.study.StudyStore;
import jwtc.chess.Pgn;

/** Collects the missing metadata before writing any chapters from the picked document. */
final class StudyImport implements ImportSource, ImportHandler {
    interface OpenSource {
        PgnSource open() throws IOException;
    }

    interface Prompt {
        boolean ask(String filename, Defaults defaults) throws Exception;
    }

    static final class Defaults {
        boolean needsTitle, needsTopic;
        String title, topic;
    }

    private final Context context;
    private final String filename;
    private final OpenSource source;
    private final Prompt prompt;
    private final Map<String, Defaults> files = new LinkedHashMap<>();
    private volatile boolean cancelled;

    StudyImport(Context context, String filename, OpenSource source, Prompt prompt) {
        this.context = context;
        this.filename = filename;
        this.source = source;
        this.prompt = prompt;
    }

    @Override
    public void forEach(Sink sink) throws Exception {
        try (PgnSource input = source.open()) {
            input.forEach(item -> {
                Defaults defaults = files.computeIfAbsent(item.sourceName, key -> new Defaults());
                Map<String, String> tags = Pgn.readTags(item.pgn);
                defaults.needsTitle |= StudyPgn.needsTitle(tags);
                defaults.needsTopic |= StudyPgn.topic(tags) == null;
                return !cancelled;
            });
        }
        for (Map.Entry<String, Defaults> file : files.entrySet()) {
            Defaults defaults = file.getValue();
            if (cancelled || ((defaults.needsTitle || defaults.needsTopic)
                    && !prompt.ask(filename(file.getKey()), defaults))) {
                return;
            }
        }
        if (!cancelled) {
            try (PgnSource input = source.open()) {
                input.forEach(sink);
            }
        }
    }

    @Override
    public boolean handle(ImportItem item) {
        Defaults defaults = files.get(item.sourceName);
        if (defaults == null) {
            return false;
        }
        return StudyStore.importChapter(
                context, item.pgn, filename(item.sourceName), item.sourcePosition,
                defaults.title, defaults.topic);
    }

    private String filename(String sourceName) {
        return sourceName == null ? filename : sourceName.substring(sourceName.lastIndexOf('/') + 1);
    }

    @Override
    public void close() {
        cancelled = true;
    }
}
