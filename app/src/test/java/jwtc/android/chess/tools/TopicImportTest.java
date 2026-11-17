package jwtc.android.chess.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import jwtc.chess.Pgn;
import org.junit.Test;

public class TopicImportTest {
    private static final String MOVES = "{Intro} 1. e4 {Try the center} (1. d4) *";
    private static final String LEGACY =
            "[StudyTitle \"Legacy title\"]\n[StudyTopic \"Legacy topic\"]\n"
                    + "[ChapterName \"Original chapter\"]\n";
    private static final String TAGGED =
            "[Event \"Original event\"]\n[Topic \"Original topic\"]\n\n" + "1. d4 *";

    @Test
    public void fillsMissingCanonicalTagsWithoutRewritingTheRestOfTheGame() throws Exception {
        String pgn = "[Event \"?\"]\n[Topic \" \"]\n" + LEGACY + "\n" + MOVES;
        List<String> prompted = new ArrayList<>();
        TopicImport source =
                new TopicImport(
                        "lesson.pgn",
                        () -> pgn(pgn + "\n\n" + TAGGED),
                        (filename, defaults) -> {
                            assertTrue(defaults.required);
                            prompted.add(filename);
                            defaults.value = "Minor \"pieces\" \\ tactics";
                            return true;
                        },
                        true);

        List<String> imported = games(source);
        assertEquals(List.of("lesson.pgn"), prompted);
        assertEquals(2, imported.size());
        assertTrue(imported.get(0).contains(LEGACY));
        assertTrue(imported.get(0).trim().endsWith(MOVES));
        assertEquals("Original chapter", Pgn.parse(imported.get(0)).tags.get("ChapterName"));
        assertEquals("Minor \"pieces\" \\ tactics", Pgn.parse(imported.get(0)).tags.get("Topic"));
        assertEquals("lesson.pgn", Pgn.parse(imported.get(0)).tags.get("Event"));
        assertEquals(Pgn.parse(TAGGED).tags, Pgn.parse(imported.get(1)).tags);
    }

    @Test
    public void defaultsArePerZipEntryAndFullyTaggedEntriesNeedNoPrompt() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            writeEntry(zip, "lessons/one.pgn", MOVES);
            writeEntry(zip, "two.pgn", MOVES);
            writeEntry(zip, "tagged.pgn", TAGGED);
        }
        TopicImport source =
                new TopicImport(
                        "archive.zip",
                        () -> PgnSource.zip(new ByteArrayInputStream(bytes.toByteArray())),
                        (filename, defaults) -> {
                            defaults.value = "Topic for " + filename;
                            return true;
                        },
                        true);
        List<String> imported = games(source);
        assertEquals(3, imported.size());
        assertEquals(
                Map.of("Topic", "Topic for one.pgn", "Event", "one.pgn"),
                Pgn.parse(imported.get(0)).tags);
        assertEquals(
                Map.of("Topic", "Topic for two.pgn", "Event", "two.pgn"),
                Pgn.parse(imported.get(1)).tags);
        assertEquals(Pgn.parse(TAGGED).tags, Pgn.parse(imported.get(2)).tags);
    }

    @Test
    public void optionalTopicCanBeLeftBlankWithoutAddingTags() throws Exception {
        TopicImport source =
                new TopicImport(
                        "practice.pgn",
                        () -> pgn(MOVES),
                        (filename, defaults) -> {
                            assertFalse(defaults.required);
                            return true;
                        },
                        false);
        assertEquals(Map.of(), Pgn.parse(games(source).get(0)).tags);
    }

    @Test
    public void cancellingLeavesTheImportUnwritten() throws Exception {
        TopicImport source =
                new TopicImport(
                        "lesson.pgn", () -> pgn(MOVES), (filename, defaults) -> false, true);
        assertTrue(games(source).isEmpty());
    }

    private static List<String> games(TopicImport source) throws Exception {
        List<String> imported = new ArrayList<>();
        source.forEach(item -> imported.add(item.pgn));
        return imported;
    }

    private static PgnSource pgn(String pgn) {
        return PgnSource.file(new ByteArrayInputStream(pgn.getBytes(StandardCharsets.UTF_8)));
    }

    private static void writeEntry(ZipOutputStream zip, String filename, String pgn)
            throws Exception {
        zip.putNextEntry(new ZipEntry(filename));
        zip.write(pgn.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
