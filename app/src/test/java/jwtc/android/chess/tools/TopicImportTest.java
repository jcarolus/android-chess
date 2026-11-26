package jwtc.android.chess.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TopicImportTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
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
                            prompted.add(filename);
                            defaults.value = "Minor \"pieces\" \\ tactics";
                            return true;
                        },
                        (filename, count) -> new TopicImport.Range(1, count),
                        temporary.getRoot(),
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
    public void defaultsArePerZipEntryAndFullyTaggedEntriesNeedNoTopicPrompt() throws Exception {
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
                        (filename, count) -> new TopicImport.Range(1, count),
                        temporary.getRoot(),
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
    public void gameImportFillsMissingTopicsWithoutDefaultingEvents() throws Exception {
        TopicImport source =
                new TopicImport(
                        "games.pgn",
                        () -> pgn(MOVES + "\n\n" + TAGGED),
                        (filename, defaults) -> {
                            assertEquals("games.pgn", filename);
                            defaults.value = "My games";
                            return true;
                        },
                        (filename, count) -> new TopicImport.Range(1, count),
                        temporary.getRoot(),
                        false);
        List<String> imported = games(source);
        assertEquals(2, imported.size());
        assertEquals(Map.of("Topic", "My games"), Pgn.parse(imported.get(0)).tags);
        assertEquals(Pgn.parse(TAGGED).tags, Pgn.parse(imported.get(1)).tags);
        assertTrue(imported.get(0).trim().endsWith(MOVES));
    }

    @Test
    public void cancellingLeavesTheImportUnwritten() throws Exception {
        TopicImport source =
                new TopicImport(
                        "lesson.pgn",
                        () -> pgn(MOVES),
                        (filename, defaults) -> false,
                        (filename, count) -> new TopicImport.Range(1, count),
                        temporary.getRoot(),
                        true);
        assertTrue(games(source).isEmpty());
    }

    @Test
    public void inclusiveRangeKeepsSourcePositionsAndIgnoresExcludedMissingTopics()
            throws Exception {
        List<ImportItem> imported = new ArrayList<>();
        TopicImport source =
                new TopicImport(
                        "games.pgn",
                        () -> pgn(MOVES + "\n\n" + TAGGED + "\n\n" + TAGGED + "\n\n" + MOVES),
                        (filename, defaults) -> {
                            throw new AssertionError("Selected games have Topics");
                        },
                        (filename, count) -> {
                            assertEquals(4, count);
                            assertTrue(imported.isEmpty());
                            return new TopicImport.Range(2, 3);
                        },
                        temporary.getRoot(),
                        false);
        source.forEach(item -> imported.add(item));
        assertEquals(2, imported.size());
        assertEquals(2, imported.get(0).sourcePosition);
        assertEquals(3, imported.get(1).sourcePosition);
        assertEquals(Pgn.parse(TAGGED).tags, Pgn.parse(imported.get(0).pgn).tags);
        assertEquals(0, temporary.getRoot().list().length);
    }

    @Test
    public void zipRangesRestartPerFileAndEachFileImportsBeforeTheNextPrompt() throws Exception {
        List<ImportItem> imported = new ArrayList<>();
        List<String> prompts = new ArrayList<>();
        byte[] bytes = archive();
        TopicImport source =
                new TopicImport(
                        "archive.zip",
                        () -> PgnSource.zip(new ByteArrayInputStream(bytes)),
                        (filename, defaults) -> {
                            throw new AssertionError("Selected games have Topics");
                        },
                        (filename, count) -> {
                            prompts.add(filename);
                            assertEquals(prompts.size() - 1, imported.size());
                            assertEquals(2, count);
                            return new TopicImport.Range(2, 2);
                        },
                        temporary.getRoot(),
                        true);
        source.forEach(item -> imported.add(item));
        assertEquals(List.of("same.pgn", "same.pgn"), prompts);
        assertEquals(2, imported.size());
        assertEquals("one/same.pgn", imported.get(0).sourceName);
        assertEquals("two/same.pgn", imported.get(1).sourceName);
        assertEquals(2, imported.get(1).sourcePosition);
        assertEquals(0, temporary.getRoot().list().length);
    }

    @Test
    public void cancellingSelectionPreservesEarlierFiles() throws Exception {
        byte[] bytes = archive();
        List<String> imported = new ArrayList<>();
        TopicImport source =
                new TopicImport(
                        "archive.zip",
                        () -> PgnSource.zip(new ByteArrayInputStream(bytes)),
                        (filename, defaults) -> {
                            defaults.value = "Topic";
                            return true;
                        },
                        (filename, count) ->
                                imported.isEmpty() ? new TopicImport.Range(1, count) : null,
                        temporary.getRoot(),
                        false);
        source.forEach(item -> imported.add(item.pgn));
        assertEquals(2, imported.size());
        assertEquals(0, temporary.getRoot().list().length);
    }

    @Test
    public void cancellingTopicPreservesEarlierFiles() throws Exception {
        byte[] bytes = archive();
        List<String> imported = new ArrayList<>();
        TopicImport source =
                new TopicImport(
                        "archive.zip",
                        () -> PgnSource.zip(new ByteArrayInputStream(bytes)),
                        (filename, defaults) -> {
                            defaults.value = "Topic";
                            return imported.isEmpty();
                        },
                        (filename, count) -> new TopicImport.Range(1, count),
                        temporary.getRoot(),
                        false);
        source.forEach(item -> imported.add(item.pgn));
        assertEquals(2, imported.size());
        assertEquals(0, temporary.getRoot().list().length);
    }

    @Test
    public void emptyInputShowsOneNoGamesPrompt() throws Exception {
        List<Integer> counts = new ArrayList<>();
        TopicImport source =
                new TopicImport(
                        "empty.pgn",
                        () -> pgn("; comment only"),
                        (filename, defaults) -> {
                            throw new AssertionError("Empty input needs no Topic");
                        },
                        (filename, count) -> {
                            counts.add(count);
                            return null;
                        },
                        temporary.getRoot(),
                        false);
        assertTrue(games(source).isEmpty());
        assertEquals(List.of(0), counts);
        assertEquals(0, temporary.getRoot().list().length);
    }

    @Test
    public void sinkCanStopWithinAFileWithoutPromptingForLaterFiles() throws Exception {
        byte[] bytes = archive();
        List<String> prompts = new ArrayList<>();
        List<String> imported = new ArrayList<>();
        TopicImport source =
                new TopicImport(
                        "archive.zip",
                        () -> PgnSource.zip(new ByteArrayInputStream(bytes)),
                        (filename, defaults) -> {
                            defaults.value = "Topic";
                            return true;
                        },
                        (filename, count) -> {
                            prompts.add(filename);
                            return new TopicImport.Range(1, count);
                        },
                        temporary.getRoot(),
                        false);
        source.forEach(
                item -> {
                    imported.add(item.pgn);
                    return false;
                });
        assertEquals(1, imported.size());
        assertEquals(1, prompts.size());
        assertEquals(0, temporary.getRoot().list().length);
    }

    @Test
    public void rangeRejectsInvalidBoundsAndCleansUpWhenPromptFails() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new TopicImport.Range(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new TopicImport.Range(2, 1));
        TopicImport source =
                new TopicImport(
                        "games.pgn",
                        () -> pgn(TAGGED),
                        (filename, defaults) -> true,
                        (filename, count) -> new TopicImport.Range(1, 2),
                        temporary.getRoot(),
                        false);
        assertThrows(IllegalArgumentException.class, () -> games(source));
        assertEquals(0, temporary.getRoot().list().length);
    }

    @Test
    public void candidateCountDoesNotRequireValidMovetext() throws Exception {
        String broken = "[Event \"Broken\"]\n1. ??? *";
        assertThrows(jwtc.chess.PgnSyntaxError.class, () -> Pgn.parse(broken));
        TopicImport source = new TopicImport("games.pgn", () -> pgn(broken + "\n\n" + TAGGED),
                (filename, defaults) -> { throw new AssertionError("Excluded game needs no Topic"); },
                (filename, count) -> {
                    assertEquals(2, count);
                    return new TopicImport.Range(2, 2);
                }, temporary.getRoot(), false);
        assertEquals(1, games(source).size());
    }

    @Test
    public void emptyArchiveShowsOneNoGamesPromptAfterSkippingEntries() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            writeEntry(zip, "empty.pgn", "");
            writeEntry(zip, "comments.PGN", "; comment");
            writeEntry(zip, "readme.txt", MOVES);
        }
        List<Integer> counts = new ArrayList<>();
        TopicImport source = new TopicImport("empty.zip",
                () -> PgnSource.zip(new ByteArrayInputStream(bytes.toByteArray())),
                (filename, defaults) -> { throw new AssertionError("Empty input needs no Topic"); },
                (filename, count) -> {
                    assertEquals("empty.zip", filename);
                    counts.add(count);
                    return null;
                }, temporary.getRoot(), false);
        assertTrue(games(source).isEmpty());
        assertEquals(List.of(0), counts);
        assertEquals(0, temporary.getRoot().list().length);
    }

    private static byte[] archive() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            writeEntry(zip, "empty.pgn", "");
            writeEntry(zip, "readme.txt", MOVES);
            writeEntry(zip, "one/same.pgn", MOVES + "\n\n" + TAGGED);
            writeEntry(zip, "two/same.pgn", MOVES + "\n\n" + TAGGED);
        }
        return bytes.toByteArray();
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
