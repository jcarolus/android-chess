package jwtc.chess;

import static org.junit.Assert.*;

import org.junit.Test;

public class PgnTagEditTest {
    @Test
    public void replacesTopicAndPreservesOtherTagsAndMovetextExactly() {
        String moves = "1. e4 {study [Topic \"comment\"]} (1. d4 d5) e5 $1 *";
        String pgn = "[Event \"Study\"]\r\n[Topic \"Old\"]\r\n\r\n" + moves;
        assertEquals(
                "[Event \"Study\"]\r\n[Topic \"New\"]\r\n\r\n" + moves,
                Pgn.withTag(pgn, "Topic", "New"));
    }

    @Test
    public void insertsTopicAfterBomAndEscapesItsValue() {
        String pgn = "\uFEFF[Event \"Study\"]\n\n1. e4 *";
        String topic = "A \"quote\" and \\ path";
        String edited = Pgn.withTag(pgn, "Topic", topic);
        assertEquals(topic, Pgn.readHeaderTags(edited).get("Topic"));
        assertTrue(edited.startsWith("\uFEFF[Topic "));
        assertTrue(edited.endsWith(pgn.substring(1)));
    }

    @Test
    public void clearsEveryDuplicateTopicWithoutTouchingComments() {
        String pgn =
                "[Topic \"A\"]\n[Event \"Study\"]\n[Topic \"B\"]\n\n"
                        + "1. e4 { [Topic \"Keep\"] } *";
        assertEquals(
                "\n[Event \"Study\"]\n\n\n1. e4 { [Topic \"Keep\"] } *",
                Pgn.withTag(pgn, "Topic", null));
        String replaced = Pgn.withTag(pgn, "Topic", "C");
        assertEquals("C", Pgn.readHeaderTags(replaced).get("Topic"));
        assertFalse(replaced.contains("[Topic \"B\"]"));
    }

    @Test
    public void clearingMissingTopicLeavesPgnUnchanged() {
        String pgn = "[Event \"Study\"]\n\n1. e4 { [Topic \"Keep\"] } *";
        assertEquals(pgn, Pgn.withTag(pgn, "Topic", null));
        assertEquals("", Pgn.withTag("", "Topic", null));
    }

    @Test
    public void insertsTopicIntoHeaderlessGameAndNormalizesNewlinesInValue() {
        String edited = Pgn.withTag("1. e4 *", "Topic", "A\nB\rC");
        assertEquals("[Topic \"A B C\"]\n1. e4 *", edited);
    }
}
