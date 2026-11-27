package jwtc.android.chess.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import jwtc.chess.Pgn;
import jwtc.chess.PgnSplitter;

/** Selects and imports each PGN file in order, applying missing Topic and Event defaults. */
final class TopicImport implements ImportSource {
    interface OpenSource {
        PgnSource open() throws IOException;
    }

    interface Prompt {
        boolean ask(String filename, DefaultTopic defaults) throws Exception;
    }

    interface SelectionPrompt {
        Range ask(String filename, int count) throws Exception;
    }

    static final class Range {
        final int from;
        final int until;

        Range(int from, int until) {
            if (from < 1 || until < from) {
                throw new IllegalArgumentException("Invalid game range");
            }
            this.from = from;
            this.until = until;
        }
    }

    static final class DefaultTopic {
        String value;
    }

    private final String filename;
    private final OpenSource source;
    private final Prompt prompt;
    private final SelectionPrompt selection;
    private final File directory;
    private final boolean studies;
    private volatile boolean cancelled;

    TopicImport(
            String filename,
            OpenSource source,
            Prompt prompt,
            SelectionPrompt selection,
            File directory,
            boolean studies) {
        this.filename = filename;
        this.source = source;
        this.prompt = prompt;
        this.selection = selection;
        this.directory = directory;
        this.studies = studies;
    }

    @Override
    public void forEach(Sink sink) throws Exception {
        boolean[] found = {false};
        try (PgnSource input = source.open()) {
            input.forEachFile(
                    directory,
                    () -> cancelled,
                    (file, sourceName) -> {
                        int count = 0;
                        try (Reader reader = reader(file)) {
                            PgnSplitter splitter = new PgnSplitter(reader);
                            while (!cancelled && splitter.next() != null) {
                                count++;
                            }
                        }
                        if (cancelled) {
                            return false;
                        }
                        if (count == 0) {
                            return true;
                        }
                        found[0] = true;
                        Range range = selection.ask(filename(sourceName), count);
                        if (cancelled || range == null) {
                            return false;
                        }
                        if (range.until > count) {
                            throw new IllegalArgumentException("Game range exceeds file count");
                        }
                        DefaultTopic topic = new DefaultTopic();
                        boolean[] missingTopic = {false};
                        readSelection(
                                file,
                                sourceName,
                                range,
                                item -> {
                                    if (Pgn.optionalTagValue(
                                                    Pgn.readHeaderTags(item.pgn).get("Topic"))
                                            == null) {
                                        missingTopic[0] = true;
                                        return false;
                                    }
                                    return true;
                                });
                        if (cancelled
                                || (missingTopic[0] && !prompt.ask(filename(sourceName), topic))) {
                            return false;
                        }
                        return readSelection(
                                file,
                                sourceName,
                                range,
                                item -> {
                                    String pgn = Pgn.withDefaultTag(item.pgn, "Topic", topic.value);
                                    if (studies) {
                                        pgn =
                                                Pgn.withDefaultTag(
                                                        pgn, "Event", filename(sourceName));
                                    }
                                    return sink.accept(
                                            new ImportItem(
                                                    pgn,
                                                    item.name,
                                                    sourceName,
                                                    item.sourcePosition));
                                });
                    });
        }
        if (!cancelled && !found[0]) {
            selection.ask(filename, 0);
        }
    }

    private boolean readSelection(File file, String sourceName, Range range, Sink sink)
            throws IOException {
        try (Reader reader = reader(file)) {
            PgnSplitter splitter = new PgnSplitter(reader);
            for (int position = 1; !cancelled && position <= range.until; position++) {
                String game = splitter.next();
                if (game == null) {
                    break;
                }
                if (position >= range.from
                        && !sink.accept(new ImportItem(game, null, sourceName, position))) {
                    return false;
                }
            }
        }
        return !cancelled;
    }

    private static Reader reader(File file) throws IOException {
        return new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
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
