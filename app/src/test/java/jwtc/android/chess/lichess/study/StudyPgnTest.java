package jwtc.android.chess.lichess.study;

import static org.junit.Assert.assertEquals;

import java.io.StringReader;
import java.util.List;
import jwtc.chess.Pgn;
import jwtc.chess.PgnDocument;
import org.junit.Test;

public class StudyPgnTest {
    @Test
    public void exportsPlayableChaptersInStudyOrderWithoutLosingAnnotations() throws Exception {
        String export =
                "[Event \"Study: Introduction\"]\n"
                        + "[ChapterName \"Introduction\"]\n"
                        + "[ChapterURL \"https://lichess.org/study/abcd1234/chap0001\"]\n\n"
                        + "{Watch the center.} 1. e4 {Occupy the center.} ({Try d4.} 1. d4) *\n\n"
                        + "[Event \"Other variant\"]\n"
                        + "[ChapterURL \"https://lichess.org/study/abcd1234/chap0002\"]\n"
                        + "[Variant \"Atomic\"]\n\n*\n\n"
                        + "[Event \"A different study\"]\n"
                        + "[ChapterURL \"https://lichess.org/study/abcd5678/chap0003\"]\n\n*\n\n"
                        + "[Event \"Study: Second chapter\"]\n"
                        + "[ChapterName \"Second chapter\"]\n"
                        + "[ChapterURL \"https://lichess.org/study/abcd1234/chap0004\"]\n\n*";
        List<StudyPgn.Chapter> chapters =
                StudyPgn.chapters("abcd1234", false, new StringReader(export));
        assertEquals(2, chapters.size());
        assertEquals("chap0001", chapters.get(0).id);
        assertEquals("Introduction", chapters.get(0).title);
        assertEquals("chap0004", chapters.get(1).id);
        PgnDocument chapter = Pgn.parse(chapters.get(0).pgn);
        assertEquals("Watch the center.", chapter.rootComment);
        assertEquals("Occupy the center.", chapter.mainLine.get(0).comment);
        assertEquals("Try d4.", chapter.mainLine.get(0).variations.get(0).get(0).leadingComment);
    }

    @Test
    public void readsBroadcastGamesByTheirGameUrlWithoutStudyTags() throws Exception {
        String export =
                "[Event \"World Championship\"]\n"
                        + "[White \"Gukesh D\"]\n[Black \"Ding, Liren\"]\n"
                        + "[BroadcastURL \"https://lichess.org/broadcast/championship/game-1/round001\"]\n"
                        + "[GameURL \"https://lichess.org/broadcast/championship/game-1/round001/game0001\"]\n\n"
                        + "1. e4 {A comment.} *";
        List<StudyPgn.Chapter> games =
                StudyPgn.chapters("tour0001", true, new StringReader(export));
        assertEquals(1, games.size());
        assertEquals("game0001", games.get(0).id);
        assertEquals("Gukesh D – Ding, Liren", games.get(0).title);
        assertEquals("A comment.", Pgn.parse(games.get(0).pgn).mainLine.get(0).comment);
        assertEquals(0, StudyPgn.chapters("tour0001", false, new StringReader(export)).size());
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
