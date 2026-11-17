package jwtc.android.chess.tools;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import jwtc.chess.Pgn;

/** Collects per-file Topic defaults before delivering any games to the import handler. */
final class TopicImport implements ImportSource {
    interface OpenSource {
        PgnSource open() throws IOException;
    }

    interface Prompt {
        boolean ask(String filename, DefaultTopic defaults) throws Exception;
    }

    static final class DefaultTopic {
        final boolean required;
        String value;

        DefaultTopic(boolean required) {
            this.required = required;
        }
    }

    private final String filename;
    private final OpenSource source;
    private final Prompt prompt;
    private final boolean studies;
    private volatile boolean cancelled;

    TopicImport(String filename, OpenSource source, Prompt prompt, boolean studies) {
        this.filename = filename;
        this.source = source;
        this.prompt = prompt;
        this.studies = studies;
    }

    @Override
    public void forEach(Sink sink) throws Exception {
        Map<String, DefaultTopic> defaults = new LinkedHashMap<>();
        try (PgnSource input = source.open()) {
            input.forEach(
                    item -> {
                        if (Pgn.optionalTagValue(Pgn.readHeaderTags(item.pgn).get("Topic"))
                                == null) {
                            defaults.computeIfAbsent(
                                    item.sourceName, key -> new DefaultTopic(studies));
                        }
                        return !cancelled;
                    });
        }
        for (Map.Entry<String, DefaultTopic> entry : defaults.entrySet()) {
            if (cancelled || !prompt.ask(filename(entry.getKey()), entry.getValue())) {
                return;
            }
        }
        if (!cancelled) {
            try (PgnSource input = source.open()) {
                input.forEach(
                        item -> {
                            if (cancelled) {
                                return false;
                            }
                            DefaultTopic topic = defaults.get(item.sourceName);
                            String pgn =
                                    topic == null
                                            ? item.pgn
                                            : Pgn.withDefaultTag(item.pgn, "Topic", topic.value);
                            if (studies) {
                                pgn = Pgn.withDefaultTag(pgn, "Event", filename(item.sourceName));
                            }
                            return sink.accept(
                                    new ImportItem(
                                            pgn, item.name, item.sourceName, item.sourcePosition));
                        });
            }
        }
    }

    private String filename(String sourceName) {
        String name =
                sourceName == null
                        ? filename
                        : sourceName.substring(sourceName.lastIndexOf('/') + 1);
        return Pgn.optionalTagValue(name) == null ? "PGN" : name;
    }

    @Override
    public void close() {
        cancelled = true;
    }
}
