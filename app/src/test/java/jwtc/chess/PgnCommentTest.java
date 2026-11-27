package jwtc.chess;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class PgnCommentTest {
    @Test
    public void clocksAreHiddenFromReadableCommentsAndSurviveTextEdits() {
        String comment = "[%clk 0:00:30.123456] Good move. <pgndiagram>";
        assertEquals("Good move.", PgnComment.text(comment));
        assertEquals("", PgnComment.text("[%clk 0:00:30]"));
        assertEquals("[%clk 0:00:30.123456] Revised.", PgnComment.withText(comment, "Revised."));
    }

    @Test
    public void clocksExposeMillisecondsWithoutLosingOriginalPrecision() {
        assertEquals(Long.valueOf(30000), PgnComment.clockMillis("[%clk 0:00:30]"));
        assertEquals(Long.valueOf(30500), PgnComment.clockMillis("[%clk 0:00:30.5]"));
        assertEquals(Long.valueOf(30050), PgnComment.clockMillis("[%clk 0:00:30.05]"));
        assertEquals(Long.valueOf(30123), PgnComment.clockMillis("[%clk 0:00:30.123456]"));
        assertEquals(Long.valueOf(3723456), PgnComment.clockMillis("[%clk 1:02:03.456]"));
        assertEquals(Long.valueOf(360000000), PgnComment.clockMillis("[%clk 100:00:00]"));
        assertEquals(Long.valueOf(0), PgnComment.clockMillis("[%clk 0:00:00]"));
    }

    @Test
    public void firstValidClockWinsAndInvalidClocksAreUnavailable() {
        String[] invalid = {
            "",
            "[%clk ?]",
            "[%clk -1:00:00]",
            "[%clk 0:60:00]",
            "[%clk 0:00:60]",
            "[%clk 0:00:30.]",
            "[%clk 0:00:30junk]",
            "[%clk 0:00:30",
            "[%clk 999999999999999999999:00:00]",
            "[%clk 2562047788016:00:00]"
        };
        assertNull(PgnComment.clockMillis(null));
        for (String comment : invalid) {
            assertNull(comment, PgnComment.clockMillis(comment));
            assertEquals(Long.valueOf(1000), PgnComment.clockMillis(comment + " [%clk 0:00:01]"));
        }
        assertEquals(Long.valueOf(30000), PgnComment.clockMillis("[%clk 0:00:30] [%clk 0:00:20]"));
    }

    private static final String ANNOTATION =
            "[%anno \"Author\", user] Protect the knight. [%cal Ge2e4][%csl Ge4]\n[%eval -0.25,22]";

    @Test
    public void separatesTextFromCommandsAndScores() {
        assertEquals("Protect the knight.", PgnComment.text(ANNOTATION));
        assertEquals("-0.25", PgnComment.evaluation(ANNOTATION));
        assertEquals("#-3", PgnComment.evaluation("[%eval #-3]"));
        assertEquals("", PgnComment.evaluation("No score."));
        assertEquals("", PgnComment.text(null));
        assertEquals("", PgnComment.evaluation(null));
    }

    @Test
    public void collapsesWhitespaceAndRemovesDiagramMarkers() {
        assertEquals(
                "White is better.\nBlack must defend.",
                PgnComment.text(
                        "  White   is\tbetter. <pgndiagram>\r\n\n \n  Black must defend.\n\n"));
        assertEquals("Diagram follows.", PgnComment.text("Diagram <PGNDIAGRAM> follows."));
        assertEquals("Diagram follows.", PgnComment.text("Diagram [PGNDIAGRAM] follows."));
        assertEquals("Before\nafter", PgnComment.text("Before [%cal Ge2e4]\n\n[%csl Ge4] after"));
    }

    @Test
    public void savesTheCleanedText() {
        assertEquals(
                "[%eval 0.3] One line\nnext line",
                PgnComment.withText("[%eval 0.3]", " One   line [pgndiagram]\n\n\nnext line "));
    }

    @Test
    public void replacesTextAndKeepsCommands() {
        assertEquals(
                "[%anno \"Author\", user] [%cal Ge2e4] [%csl Ge4] [%eval -0.25,22] Guard e4.",
                PgnComment.withText(ANNOTATION, "  Guard e4. "));
        assertEquals("[%eval 0.3]", PgnComment.withText("[%eval 0.3] Old text", ""));
        assertEquals("New text", PgnComment.withText("", "New text"));
        assertEquals("New text", PgnComment.withText(null, "New text"));
        assertEquals("", PgnComment.withText("Old text", null));
    }
}
