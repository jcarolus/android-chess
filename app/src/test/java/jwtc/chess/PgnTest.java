package jwtc.chess;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class PgnTest {
    private static List<String> sans(List<PgnDocument.Move> line) {
        List<String> out = new ArrayList<>();
        for (PgnDocument.Move move : line) out.add(move.san);
        return out;
    }

    private static PgnSyntaxError syntaxError(String pgn) {
        try {
            Pgn.parse(pgn);
        } catch (PgnSyntaxError ex) {
            return ex;
        }
        fail("expected a syntax error for: " + pgn);
        return null;
    }

    @Test
    public void parsesTagsMovesAndResult() {
        PgnDocument doc = Pgn.parse("[Event \"Casual\"]\n[White \"A\"]\n\n1. e4 e5 2. Nf3 Nc6 1-0");
        assertEquals(Arrays.asList("Event", "White"), new ArrayList<>(doc.tags.keySet()));
        assertEquals("Casual", doc.tags.get("Event"));
        assertEquals(Arrays.asList("e4", "e5", "Nf3", "Nc6"), sans(doc.mainLine));
        assertEquals("1-0", doc.result);
    }

    @Test
    public void emptyTextIsAnEmptyGame() {
        PgnDocument doc = Pgn.parse("");
        assertTrue(doc.mainLine.isEmpty());
        assertTrue(doc.tags.isEmpty());
        assertNull(doc.result);
    }

    @Test
    public void unescapesTagValues() {
        PgnDocument doc = Pgn.parse("[White \"Joe \\\"The Rook\\\" Smith\"]\n[Site \"C:\\\\x\"]\n*");
        assertEquals("Joe \"The Rook\" Smith", doc.tags.get("White"));
        assertEquals("C:\\x", doc.tags.get("Site"));
    }

    @Test
    public void tagValueMayContainBrackets() {
        assertEquals("a]b", Pgn.parse("[Event \"a]b\"]\n*").tags.get("Event"));
    }

    @Test
    public void variationsAreAlternativesToTheMoveTheyFollow() {
        PgnDocument doc = Pgn.parse("1. e4 e5 (1... c5 2. Nf3) (1... e6) 2. Nf3 *");
        assertEquals(Arrays.asList("e4", "e5", "Nf3"), sans(doc.mainLine));
        PgnDocument.Move e5 = doc.mainLine.get(1);
        assertEquals(2, e5.variations.size());
        assertEquals(Arrays.asList("c5", "Nf3"), sans(e5.variations.get(0)));
        assertEquals(Arrays.asList("e6"), sans(e5.variations.get(1)));
        assertTrue(doc.mainLine.get(0).variations.isEmpty());
    }

    @Test
    public void variationsNest() {
        PgnDocument doc = Pgn.parse("1. e4 e5 (1... c5 2. Nf3 (2. c3 d5) 2... d6) *");
        PgnDocument.Move c5 = doc.mainLine.get(1).variations.get(0).get(0);
        PgnDocument.Move nf3 = doc.mainLine.get(1).variations.get(0).get(1);
        assertTrue(c5.variations.isEmpty());
        assertEquals(Arrays.asList("c3", "d5"), sans(nf3.variations.get(0)));
        assertEquals(Arrays.asList("c5", "Nf3", "d6"), sans(doc.mainLine.get(1).variations.get(0)));
    }

    @Test
    public void commentsAttachToRootMoveAndVariationStart() {
        PgnDocument doc = Pgn.parse("{opening} 1. e4 {best} {by test} e5 ({alt} 1... c5) *");
        assertEquals("opening", doc.rootComment);
        assertEquals("best\nby test", doc.mainLine.get(0).comment);
        assertEquals("alt", doc.mainLine.get(1).variations.get(0).get(0).leadingComment);
        assertEquals("", doc.mainLine.get(1).comment);
    }

    @Test
    public void commentAfterResultStillAttaches() {
        PgnDocument doc = Pgn.parse("1. e4 1-0 {resigned}");
        assertEquals("resigned", doc.mainLine.get(0).comment);
    }

    @Test
    public void commentsMayContainPunctuation() {
        PgnDocument doc = Pgn.parse("1. e4 {(see [Event \"x\"]) $1} e5 *");
        assertEquals("(see [Event \"x\"]) $1", doc.mainLine.get(0).comment);
        assertEquals(2, doc.mainLine.size());
    }

    @Test
    public void glyphsAndNagsAreCollectedInOrder() {
        PgnDocument doc = Pgn.parse("1. e4!? $14 $10 e5 *");
        assertEquals(Arrays.asList(5, 14, 10), doc.mainLine.get(0).nags);
        assertTrue(doc.mainLine.get(1).nags.isEmpty());
        assertEquals("e4", doc.mainLine.get(0).san);
    }

    @Test
    public void castlingAcceptsLettersAndZeros() {
        assertEquals(Arrays.asList("O-O", "0-0-0", "O-O-O+"),
            sans(Pgn.parse("1. O-O 0-0-0 2. O-O-O+ *").mainLine));
    }

    @Test
    public void duckPlacementAndPromotionHaveSanShape() {
        assertTrue(Pgn.isSan("e4@e5"));
        assertTrue(Pgn.isSan("exd8=Q+"));
        assertTrue(Pgn.isSan("Nbd7"));
        assertFalse(Pgn.isSan("Zz9"));
        assertFalse(Pgn.isSan("e9"));
    }

    @Test
    public void resultCanComeFromTagOrMovetext() {
        assertEquals("0-1", Pgn.parse("[Result \"0-1\"]\n1. e4 e5").result);
        assertEquals("1/2-1/2", Pgn.parse("1. e4 e5 1/2-1/2").result);
        assertEquals("*", Pgn.parse("[Result \"*\"]\n1. e4 *").result);
        assertNull(Pgn.parse("1. e4 e5").result);
    }

    @Test
    public void rejectsDisagreeingOrInvalidResult() {
        assertEquals("Result tag and movetext disagree", syntaxError("[Result \"1-0\"]\n1. e4 0-1").getMessage());
        assertEquals("Invalid Result tag", syntaxError("[Result \"win\"]\n1. e4").getMessage());
    }

    @Test
    public void rejectsMalformedVariations() {
        assertTrue(syntaxError("1. e4 (1. d4 e5").getMessage().startsWith("Unclosed variation"));
        assertTrue(syntaxError("1. e4 ) e5").getMessage().startsWith("Unmatched or empty variation"));
        assertTrue(syntaxError("1. e4 e5 ()").getMessage().startsWith("Unmatched or empty variation"));
        assertTrue(syntaxError("(1. d4) 1. e4").getMessage().startsWith("Variation must follow a move"));
        assertTrue(syntaxError("1. e4 ((1. d4) 1. c4)").getMessage().startsWith("Variation must follow a move"));
        assertTrue(syntaxError("1. e4 e5 (1... c5 1-0)").getMessage().startsWith("Result inside variation"));
    }

    @Test
    public void rejectsVariationsNestedTooDeeply() {
        StringBuilder deep = new StringBuilder("1. e4 e5 ");
        for (int i = 0; i < 129; i++) deep.append("(1... c5 ");
        assertTrue(syntaxError(deep.toString()).getMessage().startsWith("Variation must follow a move"));

        StringBuilder ok = new StringBuilder("1. e4 e5 ");
        for (int i = 0; i < 128; i++) ok.append("(1... c5 ");
        for (int i = 0; i < 128; i++) ok.append(")");
        assertEquals(1, Pgn.parse(ok.toString()).mainLine.get(1).variations.size());
    }

    @Test
    public void rejectsMisplacedAnnotationsAndTokens() {
        assertTrue(syntaxError("$1 1. e4").getMessage().startsWith("Invalid NAG"));
        assertTrue(syntaxError("1. e4 ($1 1. d4)").getMessage().startsWith("NAG must follow a move"));
        assertTrue(syntaxError("1. e4 $256").getMessage().startsWith("Invalid NAG"));
        assertTrue(syntaxError("1. e4 $").getMessage().startsWith("Invalid NAG"));
        assertTrue(syntaxError("1. e4 1-0 e5").getMessage().startsWith("Unexpected token after result"));
        assertTrue(syntaxError("1. e4 [Event \"late\"]").getMessage().startsWith("Unexpected token"));
        assertTrue(syntaxError("1. Zz9").getMessage().startsWith("Illegal or unsupported move: Zz9"));
        assertTrue(syntaxError("1. e4!!!").getMessage().startsWith("Illegal or unsupported move"));
        assertTrue(syntaxError("[Event \"x\"").getMessage().startsWith("Unclosed tag"));
        assertTrue(syntaxError("{no end").getMessage().startsWith("Unclosed comment"));
    }

    @Test
    public void errorsCarryTheOffset() {
        PgnSyntaxError error = syntaxError("1. e4 e5 1. Zz9");
        assertEquals(12, error.getOffset());
        assertTrue(error.getMessage().endsWith("at character 12"));
    }

    @Test
    public void moveOffsetsPointAtTheToken() {
        PgnDocument doc = Pgn.parse("1. e4 e5");
        assertEquals(3, doc.mainLine.get(0).offset);
        assertEquals(6, doc.mainLine.get(1).offset);
    }

    @Test
    public void readTagsIsLenient() {
        Map<String, String> tags = Pgn.readTags("[Event \"a]b\"]\n[White \"x \\\"y\\\"\"]\n[Broken\n1. e4 e5");
        assertEquals("a]b", tags.get("Event"));
        assertEquals("x \"y\"", tags.get("White"));
        assertEquals(2, tags.size());
    }

    @Test
    public void readTagsSkipsLeadingJunkAndStopsAtMovetext() {
        Map<String, String> tags = Pgn.readTags("\uFEFF; exported\n[Event \"x\"]\n[Result \"1-0\"]\n\n1. e4 {[Site \"no\"]} 1-0");
        assertEquals(Arrays.asList("Event", "Result"), new ArrayList<>(tags.keySet()));
    }

    @Test
    public void readTagsAcceptsWhatParseRejects() {
        assertEquals("1-0", Pgn.readTags("[Result \"1-0\"]\n1. Zz9 (").get("Result"));
    }

    @Test
    public void readTagsOfNothingIsEmpty() {
        assertTrue(Pgn.readTags(null).isEmpty());
        assertTrue(Pgn.readTags("").isEmpty());
        assertTrue(Pgn.readTags("1. e4 e5").isEmpty());
    }

    @Test
    public void writeTagsOrdersRosterThenSortedRest() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("PlyCount", "4");
        tags.put("White", "A");
        tags.put("ECO", "C20");
        assertEquals("[Event \"?\"]\n[Site \"?\"]\n[Date \"?\"]\n[Round \"?\"]\n[White \"A\"]\n[Black \"?\"]\n"
            + "[Result \"?\"]\n[ECO \"C20\"]\n[PlyCount \"4\"]\n", Pgn.writeTags(tags));
    }

    @Test
    public void writeTagsEscapesAndRoundTrips() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("Event", "a \"quoted\" \\ name ]");
        tags.put("White", "line\nbreak");
        tags.put("Result", "*");
        String written = Pgn.writeTags(tags);
        Map<String, String> back = Pgn.readTags(written);
        assertEquals("a \"quoted\" \\ name ]", back.get("Event"));
        assertEquals("line break", back.get("White"));
        assertEquals(back, Pgn.parse(written + "*").tags);
    }
}
