package jwtc.android.chess.study;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jwtc.chess.Pgn;

final class StudyPgn {
    private static final Pattern EVALUATION =
            Pattern.compile("\\[%eval\\s+(#?[+-]?\\d+(?:\\.\\d+)?)(?:,\\d+)?\\s*\\]");
    private static final Pattern DIRECTIVE =
            Pattern.compile("\\[%(?:anno|cal|csl|clk|emt|eval)\\s+[^\\]]*\\]");

    static final class Chapter {
        final Map<String, String> tags;
        final int order;
        final String pgn;

        Chapter(Map<String, String> tags, int order, String pgn) {
            this.tags = tags;
            this.order = order;
            this.pgn = pgn;
        }
    }

    static Chapter parseChapter(String pgn) {
        if (pgn.length() > Pgn.MAX_GAME_SIZE) {
            throw new IllegalArgumentException("Study chapter exceeds PGN size limit");
        }
        Map<String, String> tags = Pgn.readTags(pgn);
        for (String key :
                new String[] {
                    "CollectionId", "CollectionName", "StudyId", "StudyTitle", "StudyAuthor",
                    "StudyTopic", "ChapterId", "ChapterName", "ChapterOrder"
                }) {
            if (tags.getOrDefault(key, "").trim().isEmpty()) {
                throw new IllegalArgumentException("Missing study PGN tag " + key);
            }
        }
        int order = Integer.parseInt(tags.get("ChapterOrder"));
        if (order < 1) {
            throw new IllegalArgumentException("ChapterOrder must be positive");
        }
        String variant = tags.getOrDefault("Variant", "Standard");
        if (!"Standard".equalsIgnoreCase(variant)
                && !"From Position".equalsIgnoreCase(variant)
                && !"Chess960".equalsIgnoreCase(variant)
                && !"Fischerandom".equalsIgnoreCase(variant)) {
            throw new IllegalArgumentException("Unsupported study variant " + variant);
        }
        Pgn.parse(pgn);
        return new Chapter(tags, order, pgn);
    }

    static String chapterTitle(Map<String, String> tags) {
        String name = tags.get("ChapterName");
        if (name != null) {
            return name;
        }
        String white = tags.get("White");
        String black = tags.get("Black");
        return white != null && black != null
                ? white + " – " + black
                : tags.getOrDefault("Event", "");
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
