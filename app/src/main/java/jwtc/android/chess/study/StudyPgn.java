package jwtc.android.chess.study;

import java.util.Map;
import jwtc.chess.Pgn;
import jwtc.chess.PgnDocument;
import jwtc.chess.PgnFingerprint;

public final class StudyPgn {
    static final class Chapter {
        final Map<String, String> tags;
        final String pgn;

        final String fingerprint;

        Chapter(PgnDocument document, String pgn) {
            this.tags = document.tags;
            this.pgn = pgn;
            this.fingerprint = PgnFingerprint.of(document);
        }
    }

    public static boolean needsTitle(Map<String, String> tags) {
        return value(tags, "StudyTitle") == null;
    }

    public static String topic(Map<String, String> tags) {
        String topic = value(tags, "StudyTopic");
        return topic == null ? value(tags, "Topic") : topic;
    }

    static String value(Map<String, String> tags, String name) {
        String value = tags.getOrDefault(name, "").trim();
        return value.isEmpty() || value.equals("?") ? null : value;
    }

    static Chapter parseChapter(String pgn) {
        if (pgn.length() > Pgn.MAX_GAME_SIZE) {
            throw new IllegalArgumentException("Study chapter exceeds PGN size limit");
        }
        PgnDocument document = Pgn.parse(pgn);
        String variant = document.tags.getOrDefault("Variant", "Standard");
        if (!"Standard".equalsIgnoreCase(variant)
                && !"From Position".equalsIgnoreCase(variant)
                && !"Chess960".equalsIgnoreCase(variant)
                && !"Fischerandom".equalsIgnoreCase(variant)) {
            throw new IllegalArgumentException("Unsupported study variant " + variant);
        }
        return new Chapter(document, pgn);
    }

    static String chapterTitle(Map<String, String> tags) {
        String name = value(tags, "ChapterName");
        return name == null ? value(tags, "Event") : name;
    }

    private StudyPgn() {}
}
