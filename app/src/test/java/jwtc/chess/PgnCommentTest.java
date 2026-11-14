package jwtc.chess;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PgnCommentTest {
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
