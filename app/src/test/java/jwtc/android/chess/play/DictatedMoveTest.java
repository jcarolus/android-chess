package jwtc.android.chess.play;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import javax.xml.parsers.DocumentBuilderFactory;
import jwtc.chess.board.BoardConstants;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class DictatedMoveTest {
    @Test
    public void acceptsOmittedSanPromotionWithoutIgnoringExplicitPieces() {
        for (String text : new String[] {"e8", "pawn e8"}) {
            for (String piece : new String[] {"Q", "R", "B", "N"}) {
                assertTrue(parse(text, "en").matchesSanWithoutPromotion("e8=" + piece));
            }
        }
        assertTrue(parse("e takes d8", "en").matchesSanWithoutPromotion("exd8=N+"));
        assertTrue(parse("e8+", "en").matchesSanWithoutPromotion("e8=Q+"));
        assertFalse(parse("e8+", "en").matchesSanWithoutPromotion("e8=N"));
        assertFalse(parse("e8Q", "en").matchesSanWithoutPromotion("e8=N"));
        assertFalse(parse("e8", "en").matchesSanWithoutPromotion("e8"));
        assertEquals(-1, parse("e7e8", "en").promotion);
    }

    @Test
    public void acceptsCompactCoordinatesAndPromotions() {
        assertEquals("e2 e4", parse("e2e4", "en").describe());
        assertEquals(BoardConstants.QUEEN, parse("e7e8Q", "en").promotion);
        assertEquals(BoardConstants.KNIGHT, parse("e7e8=N", "en").promotion);
    }

    @Test
    public void acceptsSanWithOptionalCheckAndPromotionEquals() {
        assertTrue(parse("Nf6", "en").matchesSan("Nf6+"));
        assertTrue(parse("Nf6+", "en").matchesSan("Nf6+"));
        assertFalse(parse("Nf6+", "en").matchesSan("Nf6"));
        assertTrue(parse("e8Q", "en").matchesSan("e8=Q+"));
        assertTrue(parse("e8=Q", "en").matchesSan("e8=Q"));
        assertTrue(parse("O-O", "en").matchesSan("O-O+"));
        assertTrue(parse("R1e2", "en").matchesSan("R1e2"));
        assertFalse(parse("Re2", "en").matchesSan("R1e2"));
    }

    @Test
    public void acceptsSpokenPiecesCapturesAndPromotion() {
        assertTrue(parse("Knight f3", "en").matchesSan("Nf3"));
        assertTrue(parse("pawn e8 queen", "en").matchesSan("e8=Q"));
        assertTrue(parse("Knight takes f3", "en").matchesSan("Nxf3"));
        assertTrue(parse("bishop captures g7", "en").matchesSan("Bxg7+"));
        assertTrue(parse("pawn e captures d8 queen", "en").matchesSan("exd8=Q+"));
        assertFalse(parse("Knight takes f3", "en").matchesSan("Nf3"));
        assertTrue(parse("Nxf3", "en").matchesSan("Nxf3"));
    }

    @Test
    public void acceptsLocalizedSanInEveryVocabulary() {
        for (String language :
                new String[] {
                    "en", "nl", "de", "fr", "es", "it", "pt", "ro", "ru", "tr", "ar", "fa", "zh",
                    "zh-TW"
                }) {
            DictatedMove.Vocabulary vocabulary = vocabulary(language);
            assertTrue(DictatedMove.parse("Nf3", vocabulary).matchesSan("Nf3+"));
        }
        assertTrue(parse("paard slaat f3", "nl").matchesSan("Nxf3"));
        assertTrue(parse("pion e8 dame", "nl").matchesSan("e8=Q"));
        assertTrue(parse("Springer schlägt f3", "de").matchesSan("Nxf3"));
    }

    @Test
    public void acceptsNatoFileNamesWithLocalizedRanks() {
        assertEquals("a1 b2", parse("Alfa one Bravo two", "en").describe());
        assertEquals("c3 d4", parse("Charlie drie Delta vier", "nl").describe());
        assertEquals("e1 e2", parse("Echo één Echo twee", "nl").describe());
        assertEquals("e1 e2", parse("Echo 1 Echo 2", "nl").describe());
        assertEquals("f5 g6", parse("Foxtrot 5 Golf 6", "nl").describe());
        assertEquals("h7 h8", parse("Hotel zeven h8", "nl").describe());
        assertNull(parse("1 2", "nl"));
    }

    @Test
    public void acceptsHyphenBeforeHandwrittenPromotionPiece() {
        DictatedMove move = parse("B7.-B8-Dame", "nl");
        assertEquals("b7 b8", move.describe());
        assertEquals(BoardConstants.QUEEN, move.promotion);
        assertNull(parse("B7-B8--Dame", "nl"));
    }

    @Test
    public void recognizesDutchHandwritingWhenKeyboardLanguageIsUnknown() {
        DictatedMove move =
                DictatedMove.parseUnambiguous(
                        "B7-B8.Dame",
                        Arrays.asList(
                                        vocabulary("en"),
                                        vocabulary("nl"),
                                        vocabulary("fr"),
                                        vocabulary("de"))
                                .iterator());
        assertEquals("b7 b8", move.describe());
        assertEquals(BoardConstants.QUEEN, move.promotion);
    }

    @Test
    public void rejectsConflictingInterpretationsWhenKeyboardLanguageIsUnknown() {
        DictatedMove.Vocabulary alternate =
                new DictatedMove.Vocabulary(
                        "e",
                        new String[] {
                            "one", "three", "two", "four", "five", "six", "seven", "eight"
                        },
                        new String[] {"knight", "bishop", "rook", "queen"},
                        new String[] {"pawn", "knight", "bishop", "rook", "queen", "king"},
                        "takes|captures");
        assertNull(
                DictatedMove.parseUnambiguous(
                        "e two e four", Arrays.asList(vocabulary("en"), alternate).iterator()));
    }

    @Test
    public void parsesPackagedVocabularies() {
        String[][] examples = {
            {"ar", "e اثنين e أربعة", "ملكة"},
            {"de", "e zwei e vier", "dame"},
            {"es", "e dos e cuatro", "reina"},
            {"fa", "e دو e چهار", "وزیر"},
            {"fr", "e deux e quatre", "dame"},
            {"it", "e due e quattro", "regina"},
            {"nl", "e twee e vier", "konining"},
            {"pt", "e dois e quatro", "rainha"},
            {"ro", "e două e patru", "regină"},
            {"ru", "e два e четыре", "ферзь"},
            {"tr", "e iki e dört", "vezir"},
            {"zh", "e二e四", "后"},
            {"zh-TW", "e兩e四", "皇后"}
        };
        for (String[] example : examples) {
            String locale = example[0];
            assertEquals(locale, "e2 e4", parse(example[1], locale).describe());
            assertEquals(
                    locale, BoardConstants.QUEEN, parse("b7 b8 " + example[2], locale).promotion);
            assertEquals(locale, "e2 e4", parse("ｅ٢-ｅ４", locale).describe());
        }
    }

    @Test
    public void acceptsDutchQueenAliasesInHandwritingInput() {
        for (String name : new String[] {"dame", "koningin", "konining"}) {
            DictatedMove move = parse("B7-B8." + name, "nl-NL");
            assertEquals("b7 b8", move.describe());
            assertEquals(BoardConstants.QUEEN, move.promotion);
        }
        assertNull(parse("B7-B8.koning", "nl-NL"));
    }

    @Test
    public void replacesUnsupportedCharactersWithSpaces() {
        assertEquals("e2 e4", parse("e2,e4.", "en").describe());
        assertEquals("e2 e4", parse("e.2 / e,4!", "en").describe());
        assertEquals("e2 e4", parse("e twee, e vier.", "nl").describe());
        assertNull(parse("e2, to, e4", "en"));
        assertNull(parse("e2, e9", "en"));
    }

    @Test
    public void acceptsOneHyphenBetweenSquares() {
        assertEquals("e2 e4", parse("e2-e4", "en").describe());
        assertEquals("e2 e4", parse("e2 - e4", "nl").describe());
        assertNull(parse("e2--e4", "en"));
    }

    @Test
    public void normalizesEnglishFileAliasAndPunctuation() {
        DictatedMove move = parse("ee two E. 4", "en-US");
        assertEquals("e2 e4", move.describe());
        assertNull(parse("zee two E. 4", "en"));
        assertNull(parse("ee two to E. 4", "en"));
    }

    @Test
    public void usesDutchRanksAndPromotionNames() {
        assertEquals("e2 e4", parse("e twee e vier", "nl-NL").describe());
        assertEquals("e2 e4", parse("e2 e4", "nl-BE").describe());
        assertEquals(BoardConstants.KNIGHT, parse("e zeven e acht paard", "nl").promotion);
        assertNull(parse("ee two E. 4", "nl"));
        assertNull(parse("e twee e vier", "en"));
    }

    @Test
    public void fallsBackToEnglishWhenKeyboardLanguageIsUnavailable() {
        assertEquals("e2 e4", parse("e two e four", "").describe());
        assertEquals("e2 e4", parse("e two e four", "ja-JP").describe());
    }

    @Test
    public void parsesDigitAndSpokenRanks() {
        DictatedMove digits = parse("e2 e4", "en");
        DictatedMove spoken = parse("  E two  e FOUR ", "en");
        assertEquals(52, digits.from);
        assertEquals(36, digits.to);
        assertEquals(digits.from, spoken.from);
        assertEquals(digits.to, spoken.to);
        assertEquals(-1, spoken.promotion);
    }

    @Test
    public void preservesKingToRookCoordinates() {
        DictatedMove move = parse("e1 h1", "en");
        assertEquals(60, move.from);
        assertEquals(63, move.to);
        assertEquals(-1, move.promotion);
    }

    @Test
    public void parsesPromotion() {
        DictatedMove move = parse("e seven e eight knight", "en");
        assertEquals(12, move.from);
        assertEquals(4, move.to);
        assertEquals(BoardConstants.KNIGHT, move.promotion);
    }

    @Test
    public void rejectsAmbiguousOrIncompletePhrases() {
        assertNull(parse("e2 to e4", "en"));
        assertNull(parse("e9 e4", "en"));
        assertNull(parse("e2 e", "en"));
        assertNull(parse("e7 e8 king", "en"));
    }

    private static DictatedMove parse(String text, String languageTag) {
        return DictatedMove.parse(text, vocabulary(languageTag));
    }

    private static DictatedMove.Vocabulary vocabulary(String languageTag) {
        Locale locale = Locale.forLanguageTag(languageTag);
        String language = locale.getLanguage();
        File resources = new File("src/main/res");
        File localized = new File(resources, "values-" + language + "/strings.xml");
        File regional =
                new File(
                        resources,
                        "values-" + language + "-r" + locale.getCountry() + "/strings.xml");
        File source = localized.isFile() ? localized : new File(resources, "values/strings.xml");
        if (regional.isFile()) {
            source = regional;
        }
        try {
            Document document =
                    DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(source);
            Element file = (Element) document.getElementsByTagName("string").item(0);
            NodeList strings = document.getElementsByTagName("string");
            for (int i = 0; i < strings.getLength(); i++) {
                Element candidate = (Element) strings.item(i);
                if ("move_input_file_e".equals(candidate.getAttribute("name"))) {
                    file = candidate;
                    break;
                }
            }
            return new DictatedMove.Vocabulary(
                    file.getTextContent(),
                    words(document, "move_input_ranks"),
                    words(document, "move_input_promotions"),
                    new String[] {
                        string(document, "piece_pawn"),
                        string(document, "piece_knight"),
                        string(document, "piece_bishop"),
                        string(document, "piece_rook"),
                        string(document, "piece_queen"),
                        string(document, "piece_king")
                    },
                    string(document, "move_input_captures"));
        } catch (Exception error) {
            throw new AssertionError("Could not load vocabulary from " + source, error);
        }
    }

    private static String string(Document document, String name) {
        NodeList strings = document.getElementsByTagName("string");
        for (int i = 0; i < strings.getLength(); i++) {
            Element element = (Element) strings.item(i);
            if (name.equals(element.getAttribute("name"))) {
                return element.getTextContent();
            }
        }
        throw new AssertionError("Missing string " + name);
    }

    private static String[] words(Document document, String name) {
        NodeList arrays = document.getElementsByTagName("string-array");
        for (int i = 0; i < arrays.getLength(); i++) {
            Element array = (Element) arrays.item(i);
            if (name.equals(array.getAttribute("name"))) {
                NodeList items = array.getElementsByTagName("item");
                String[] words = new String[items.getLength()];
                for (int j = 0; j < words.length; j++) {
                    words[j] = items.item(j).getTextContent();
                }
                return words;
            }
        }
        throw new AssertionError("Missing array " + name);
    }
}
