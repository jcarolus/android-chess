package jwtc.android.chess.lichess.study;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jwtc.chess.Pgn;
import jwtc.chess.PgnSplitter;

final class StudyPgn {
    private static final Pattern CHAPTER_URL =
            Pattern.compile("https://lichess\\.org/study/([a-zA-Z0-9]{8})/([a-zA-Z0-9]{8})");
    private static final Pattern BROADCAST_GAME_URL =
            Pattern.compile(
                    "https://lichess\\.org/broadcast/[^/]+/[^/]+/[a-zA-Z0-9]{8}/([a-zA-Z0-9]{8})");
    private static final Pattern EVALUATION =
            Pattern.compile("\\[%eval\\s+(#?[+-]?\\d+(?:\\.\\d+)?)(?:,\\d+)?\\s*\\]");
    private static final Pattern DIRECTIVE =
            Pattern.compile("\\[%(?:anno|cal|csl|clk|emt|eval)\\s+[^\\]]*\\]");

    static final class Chapter {
        final String id;
        final String title;
        final String pgn;

        Chapter(String id, String title, String pgn) {
            this.id = id;
            this.title = title;
            this.pgn = pgn;
        }
    }

    static List<Chapter> chapters(String studyId, boolean broadcast, Reader reader)
            throws IOException {
        PgnSplitter splitter = new PgnSplitter(reader);
        List<Chapter> chapters = new ArrayList<>();
        String pgn;
        while ((pgn = splitter.next()) != null) {
            Map<String, String> tags = Pgn.readTags(pgn);
            Matcher url =
                    broadcast
                            ? BROADCAST_GAME_URL.matcher(tags.getOrDefault("GameURL", ""))
                            : CHAPTER_URL.matcher(tags.getOrDefault("ChapterURL", ""));
            if (pgn.length() > Pgn.MAX_GAME_SIZE
                    || !url.matches()
                    || (!broadcast && !studyId.equals(url.group(1)))) {
                continue;
            }
            String variant = tags.getOrDefault("Variant", "Standard");
            if (!"Standard".equalsIgnoreCase(variant)
                    && !"From Position".equalsIgnoreCase(variant)
                    && !"Chess960".equalsIgnoreCase(variant)
                    && !"Fischerandom".equalsIgnoreCase(variant)) {
                continue;
            }
            chapters.add(new Chapter(url.group(broadcast ? 1 : 2), chapterTitle(tags), pgn));
        }
        return chapters;
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
