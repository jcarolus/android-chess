package jwtc.android.chess.play;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.xml.parsers.DocumentBuilderFactory;
import jwtc.chess.LegalMove;
import jwtc.chess.Move;
import jwtc.chess.MoveResolver;
import jwtc.chess.Pos;
import jwtc.chess.board.BoardConstants;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class DictatedMoveTest {
    @Test
    public void acceptsLocalizedCastlingInEveryVocabulary() {
        String[][] examples = {
            {"en", "castle", "castle short", "castle long"},
            {"nl", "rokade", "korte rokade", "lange rokade"},
            {"de", "Rochade", "kurze Rochade", "lange Rochade"},
            {"fr", "roque", "petit roque", "grand roque"},
            {"es", "enroque", "enroque corto", "enroque largo"},
            {"it", "arrocco", "arrocco corto", "arrocco lungo"},
            {"pt", "roque", "roque curto", "roque longo"},
            {"ro", "rocadă", "rocadă scurtă", "rocadă lungă"},
            {"ru", "рокировка", "короткая рокировка", "длинная рокировка"},
            {"tr", "rok", "kısa rok", "uzun rok"},
            {"ar", "تبييت", "تبييت قصير", "تبييت طويل"},
            {"fa", "قلعه", "قلعه‌ی کوتاه", "قلعه‌ی بلند"},
            {"zh", "王车易位", "短易位", "长易位"},
            {"zh-TW", "王車易位", "王車短易位", "王車長易位"}
        };
        for (String[] example : examples) {
            String language = example[0];
            DictatedMove generic = parse(example[1], language);
            assertTrue(language, matchesSan(generic, "O-O+"));
            assertTrue(language, matchesSan(generic, "O-O-O"));
            assertFalse(language, matchesSan(generic, "Ke2"));
            DictatedMove shortCastle = parse(example[2], language);
            assertTrue(language, matchesSan(shortCastle, "O-O"));
            assertFalse(language, matchesSan(shortCastle, "O-O-O"));
            DictatedMove longCastle = parse(example[3], language);
            assertTrue(language, matchesSan(longCastle, "O-O-O#"));
            assertFalse(language, matchesSan(longCastle, "O-O"));
            for (String notation : new String[] {"oo", "00", "ooo", "000"}) {
                assertTrue(
                        language,
                        matchesSan(
                                parse(notation, language),
                                notation.length() == 2 ? "O-O" : "O-O-O"));
            }
        }
        assertTrue(matchesSan(parse("rokeren", "nl"), "O-O"));
        assertTrue(matchesSan(parse("roquer", "fr"), "O-O-O"));
        assertTrue(matchesSan(parse("易位", "zh-TW"), "O-O"));
        assertNull(parse("korte rokade", "en"));
        assertNull(parse("grand roque court", "fr"));
    }

    @Test
    public void acceptsShortCastlingAliases() {
        for (String text :
                new String[] {"castle short", "oo", "00", "O-O", "0-0", " Castle   SHORT "}) {
            DictatedMove move = parse(text, "en");
            assertTrue(text, matchesSan(move, "O-O"));
            assertTrue(text, matchesSan(move, "O-O+"));
            assertFalse(text, matchesSan(move, "O-O-O"));
            assertEquals("o-o", move.san);
        }
    }

    @Test
    public void acceptsLongCastlingAliases() {
        for (String text : new String[] {"castle long", "ooo", "000", "O-O-O", "0-0-0"}) {
            DictatedMove move = parse(text, "en");
            assertTrue(text, matchesSan(move, "O-O-O"));
            assertTrue(text, matchesSan(move, "O-O-O#"));
            assertFalse(text, matchesSan(move, "O-O"));
            assertEquals("o-o-o", move.san);
        }
        assertTrue(matchesSan(parse("ooo+", "en"), "O-O-O+"));
        assertFalse(matchesSan(parse("ooo+", "en"), "O-O-O"));
        assertTrue(matchesSan(parse("00#", "en"), "O-O#"));
    }

    @Test
    public void unspecifiedCastlingMatchesBothSidesForLegalMoveResolution() {
        DictatedMove move = parse("castle", "en");
        assertTrue(matchesSan(move, "O-O"));
        assertTrue(matchesSan(move, "O-O-O"));
        assertTrue(matchesSan(move, "O-O+"));
        assertTrue(matchesSan(move, "O-O-O#"));
        assertFalse(matchesSan(move, "Ke2"));
        assertFalse(matchesSanWithoutPromotion(move, "e8=Q"));
    }

    @Test
    public void rejectsIncompleteOrMalformedCastling() {
        for (String text :
                new String[] {
                    "o",
                    "0",
                    "oooo",
                    "0000",
                    "castle shorts",
                    "castleshort",
                    "castle long short",
                    "oo-",
                    "o--o"
                }) {
            assertNull(text, parse(text, "en"));
        }
    }

    @Test
    public void acceptsOmittedSanPromotionWithoutIgnoringExplicitPieces() {
        for (String text : new String[] {"e8", "pawn e8"}) {
            for (String piece : new String[] {"Q", "R", "B", "N"}) {
                assertTrue(matchesSanWithoutPromotion(parse(text, "en"), "e8=" + piece));
            }
        }
        assertTrue(matchesSanWithoutPromotion(parse("e takes d8", "en"), "exd8=N+"));
        assertTrue(matchesSanWithoutPromotion(parse("e8+", "en"), "e8=Q+"));
        assertFalse(matchesSanWithoutPromotion(parse("e8+", "en"), "e8=N"));
        assertFalse(matchesSanWithoutPromotion(parse("e8Q", "en"), "e8=N"));
        assertFalse(matchesSanWithoutPromotion(parse("e8", "en"), "e8"));
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
        assertTrue(matchesSan(parse("Nf6", "en"), "Nf6+"));
        assertTrue(matchesSan(parse("Nf6+", "en"), "Nf6+"));
        assertFalse(matchesSan(parse("Nf6+", "en"), "Nf6"));
        assertTrue(matchesSan(parse("e8Q", "en"), "e8=Q+"));
        assertTrue(matchesSan(parse("e8=Q", "en"), "e8=Q"));
        assertTrue(matchesSan(parse("O-O", "en"), "O-O+"));
        assertTrue(matchesSan(parse("R1e2", "en"), "R1e2"));
        assertFalse(matchesSan(parse("Re2", "en"), "R1e2"));
    }

    @Test
    public void acceptsSpokenPiecesCapturesAndPromotion() {
        assertTrue(matchesSan(parse("Knight f3", "en"), "Nf3"));
        assertTrue(matchesSan(parse("pawn e8 queen", "en"), "e8=Q"));
        assertTrue(matchesSan(parse("Knight takes f3", "en"), "Nxf3"));
        assertTrue(matchesSan(parse("bishop captures g7", "en"), "Bxg7+"));
        assertTrue(matchesSan(parse("pawn e captures d8 queen", "en"), "exd8=Q+"));
        assertFalse(matchesSan(parse("Knight takes f3", "en"), "Nf3"));
        assertTrue(matchesSan(parse("Nxf3", "en"), "Nxf3"));
    }

    @Test
    public void acceptsOmittedCaptureMarkers() {
        for (String text : new String[] {"bf3", "Bf3", "bishop f3"}) {
            assertTrue(text, matchesSan(parse(text, "en"), "Bxf3"));
            assertTrue(text, matchesSan(parse(text, "en"), "Bxf3+"));
        }
        assertTrue(matchesSan(parse("loper f3", "nl"), "Bxf3"));
        assertTrue(matchesSan(parse("ef3", "en"), "exf3"));
        assertTrue(matchesSan(parse("pawn e d8 queen", "en"), "exd8=Q+"));
        assertTrue(matchesSanWithoutPromotion(parse("ed8", "en"), "exd8=N+"));
    }

    @Test
    public void omittedCaptureMarkersPreserveOtherSanRequirements() {
        assertFalse(matchesSan(parse("Bf3+", "en"), "Bxf3"));
        assertFalse(matchesSan(parse("Rf3", "en"), "Rbxf3"));
        assertFalse(matchesSan(parse("Rf3", "en"), "Rfxf3"));
        assertTrue(matchesSan(parse("Rbf3", "en"), "Rbxf3"));
        assertFalse(matchesSan(parse("Rbf3", "en"), "Rfxf3"));
        assertFalse(matchesSan(parse("f3", "en"), "exf3"));
        assertFalse(matchesSan(parse("Bxf3", "en"), "Bf3"));
        assertFalse(matchesSan(parse("bishop captures f3", "en"), "Bf3"));
        assertFalse(matchesSan(parse("ed8Q", "en"), "exd8=N"));
    }

    @Test
    public void acceptsPieceHomophonesInMovesAndPromotions() {
        String[][] examples = {
            {"en", "night", "N"},
            {"nl", "paart", "N"},
            {"fr", "renne", "Q"},
            {"fr", "rêne", "Q"}
        };
        for (String[] example : examples) {
            String language = example[0];
            String alias = example[1];
            String piece = example[2];
            assertTrue(matchesSan(parse(alias + " f3", language), piece + "f3"));
            assertTrue(matchesSan(parse("e8 " + alias, language), "e8=" + piece));
            assertEquals(
                    piece.equals("N") ? BoardConstants.KNIGHT : BoardConstants.QUEEN,
                    parse("e7 e8 " + alias, language).promotion);
        }
        assertTrue(matchesSan(parse("Night takes f3", "en-US"), "Nxf3+"));
        assertNull(parse("night f3", "nl"));
        assertNull(parse("paart f3", "en"));
        assertNull(parse("renne f3", "en"));
        assertNull(parse("nightingale f3", "en"));
    }

    @Test
    public void acceptsLocalizedSanInEveryVocabulary() {
        for (String language :
                new String[] {
                    "en", "nl", "de", "fr", "es", "it", "pt", "ro", "ru", "tr", "ar", "fa", "zh",
                    "zh-TW"
                }) {
            DictatedMove.Vocabulary vocabulary = vocabulary(language);
            assertTrue(matchesSan(DictatedMove.parse("Nf3", vocabulary), "Nf3+"));
        }
        assertTrue(matchesSan(parse("paard slaat f3", "nl"), "Nxf3"));
        assertTrue(matchesSan(parse("pion e8 dame", "nl"), "e8=Q"));
        assertTrue(matchesSan(parse("Springer schlägt f3", "de"), "Nxf3"));
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
                        new String[] {
                            "one", "three", "two", "four", "five", "six", "seven", "eight"
                        },
                        new String[] {"knight", "bishop", "rook", "queen"},
                        new String[] {"pawn", "knight", "bishop", "rook", "queen", "king"},
                        "takes|captures",
                        new String[] {"castle", "castle short", "castle long"});
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
    public void normalizesFileLettersAndPunctuation() {
        DictatedMove move = parse("e two E. 4", "en-US");
        assertEquals("e2 e4", move.describe());
        assertNull(parse("zee two E. 4", "en"));
        assertNull(parse("ee two E. 4", "en"));
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

    private static boolean matchesSanWithoutPromotion(DictatedMove move, String legalSan) {
        int promotion =
                Move.makeMovePromotion(
                        Pos.fromColAndRow(4, 1),
                        Pos.fromColAndRow(4, 0),
                        BoardConstants.QUEEN,
                        false);
        return move.san != null
                && MoveResolver.spokenSan(List.of(new LegalMove(promotion, "", legalSan)), move.san)
                                .kind
                        == MoveResolver.Resolution.Kind.NEEDS_PROMOTION;
    }

    private static boolean matchesSan(DictatedMove move, String legalSan) {
        return move.san != null
                && MoveResolver.spokenSan(List.of(new LegalMove(0, "", legalSan)), move.san).kind
                        == MoveResolver.Resolution.Kind.MOVE;
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
            return new DictatedMove.Vocabulary(
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
                    string(document, "move_input_captures"),
                    new String[] {
                        string(document, "move_input_castle"),
                        string(document, "tts_move_castle_short"),
                        string(document, "tts_move_castle_long")
                    });
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
