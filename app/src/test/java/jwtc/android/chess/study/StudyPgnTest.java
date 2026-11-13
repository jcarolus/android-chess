package jwtc.android.chess.study;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import jwtc.android.chess.tools.PgnSource;
import jwtc.chess.Pgn;
import jwtc.chess.PgnDocument;
import org.junit.Test;

public class StudyPgnTest {
    private static final String HEADERS =
            "[Event \"An annotated lesson\"]\n"
                    + "[CollectionId \"club-lessons\"]\n"
                    + "[CollectionName \"Club Lessons\"]\n"
                    + "[StudyId \"center\"]\n"
                    + "[StudyTitle \"Playing in the center\"]\n"
                    + "[StudyAuthor \"Example Author\"]\n"
                    + "[StudyTopic \"Pawn structures\"]\n"
                    + "[ChapterId \"intro\"]\n"
                    + "[ChapterName \"Introduction\"]\n"
                    + "[ChapterOrder \"2\"]\n"
                    + "[SourceURL \"https://example.org/club/center\"]\n"
                    + "[License \"CC-BY-4.0\"]\n"
                    + "[Orientation \"black\"]\n";
    private static final String MOVES =
            "\n{Watch the center.} 1. e4 {Occupy the center.} ({Try d4.} 1. d4) *";

    @Test
    public void readsCollectionMetadataAndPreservesTheOriginalChapter() {
        String pgn = HEADERS + MOVES;
        StudyPgn.Chapter chapter = StudyPgn.parseChapter(pgn);
        assertEquals("club-lessons", chapter.tags.get("CollectionId"));
        assertEquals("Pawn structures", chapter.tags.get("StudyTopic"));
        assertEquals("intro", chapter.tags.get("ChapterId"));
        assertEquals("CC-BY-4.0", chapter.tags.get("License"));
        assertEquals(pgn, chapter.pgn);
        PgnDocument document = Pgn.parse(chapter.pgn);
        assertEquals("Watch the center.", document.rootComment);
        assertEquals("Occupy the center.", document.mainLine.get(0).comment);
        assertEquals("Try d4.", document.mainLine.get(0).variations.get(0).get(0).leadingComment);
    }

    @Test
    public void readsMultipleChaptersThroughTheSharedZipSource() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry("center.pgn"));
            String second = HEADERS.replace("\"intro\"", "\"chapter-2\"");
            zip.write((HEADERS + MOVES + "\n\n" + second + MOVES).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        List<String> ids = new ArrayList<>();
        try (PgnSource source = PgnSource.zip(new ByteArrayInputStream(buffer.toByteArray()))) {
            source.forEach(
                    item -> {
                        ids.add(StudyPgn.parseChapter(item.pgn).tags.get("ChapterId"));
                        return true;
                    });
        }
        assertEquals(List.of("intro", "chapter-2"), ids);
    }

    @Test
    public void acceptsStandardTagsAndIgnoresChapterOrder() {
        StudyPgn.Chapter chapter = StudyPgn.parseChapter(
                "[Event \"Round 1\"]\n[Topic \"Tactics\"]\n[ChapterOrder \"ignored\"]\n\n1. e4 *");
        assertTrue(StudyPgn.needsTitle(chapter.tags));
        assertEquals("Tactics", StudyPgn.topic(chapter.tags));
        assertEquals("Round 1", StudyPgn.chapterTitle(chapter.tags));
        assertNull(StudyPgn.value(chapter.tags, "StudyAuthor"));
    }

    @Test
    public void prefersStudyTopicAndFallsBackToTopicForBlankValues() {
        assertEquals("Pawn structures", StudyPgn.topic(Pgn.readTags(HEADERS + "[Topic \"Other\"]")));
        assertEquals("Other", StudyPgn.topic(Pgn.readTags("[StudyTopic \"  \"]\n[Topic \"Other\"]")));
    }

    @Test
    public void rejectsInvalidPgnAndUnsupportedVariants() {
        assertThrows(
                IllegalArgumentException.class,
                () -> StudyPgn.parseChapter(HEADERS + "\n1. invalid *"));
        assertThrows(
                IllegalArgumentException.class,
                () -> StudyPgn.parseChapter(HEADERS + "[Variant \"Atomic\"]\n" + MOVES));
    }

    @Test
    public void separatesStudyTextFromPgnDirectivesAndScores() {
        String annotation =
                "[%anno \"Author\", user] Protect the knight. [%cal Ge2e4][%csl Ge4]\n[%eval -0.25,22]";
        assertEquals("Protect the knight.", StudyPgn.comment(annotation));
        assertEquals("-0.25", StudyPgn.evaluation(annotation));
        assertEquals("#-3", StudyPgn.evaluation("[%eval #-3]"));
        assertEquals("", StudyPgn.evaluation("No score."));
    }
}
