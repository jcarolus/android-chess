package jwtc.android.chess.play;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
                        new String[] {"knight", "bishop", "rook", "queen"});
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
                    words(document, "move_input_promotions"));
        } catch (Exception error) {
            throw new AssertionError("Could not load vocabulary from " + source, error);
        }
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
