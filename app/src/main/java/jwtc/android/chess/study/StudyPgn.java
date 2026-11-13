package jwtc.android.chess.study;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jwtc.chess.Pgn;

public final class StudyPgn {
    private static final Pattern EVALUATION =
            Pattern.compile("\\[%eval\\s+(#?[+-]?\\d+(?:\\.\\d+)?)(?:,\\d+)?\\s*\\]");
    private static final Pattern DIRECTIVE =
            Pattern.compile("\\[%(?:anno|cal|csl|clk|emt|eval)\\s+[^\\]]*\\]");

    static final class Chapter {
        final Map<String, String> tags;
        final String pgn;

        Chapter(Map<String, String> tags, String pgn) {
            this.tags = tags;
            this.pgn = pgn;
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
        Map<String, String> tags = Pgn.parse(pgn).tags;
        String variant = tags.getOrDefault("Variant", "Standard");
        if (!"Standard".equalsIgnoreCase(variant)
                && !"From Position".equalsIgnoreCase(variant)
                && !"Chess960".equalsIgnoreCase(variant)
                && !"Fischerandom".equalsIgnoreCase(variant)) {
            throw new IllegalArgumentException("Unsupported study variant " + variant);
        }
        return new Chapter(tags, pgn);
    }

    static String chapterTitle(Map<String, String> tags) {
        String name = value(tags, "ChapterName");
        return name == null ? value(tags, "Event") : name;
    }

    static String comment(String annotation) {
        return DIRECTIVE.matcher(annotation).replaceAll("").trim();
    }

    static String evaluation(String annotation) {
        Matcher matcher = EVALUATION.matcher(annotation);
        return matcher.find() ? matcher.group(1) : "";
    }

    private StudyPgn() {}
}
