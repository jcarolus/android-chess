package jwtc.chess;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import jwtc.chess.board.BoardPosition;
import org.junit.Test;

public class PgnFingerprintTest {
    @Test
    public void ignoresTagsAnnotationsVariationsAndPresentation() {
        String plain = "1. e4 e5 2. Nf3 *";
        String annotated =
                "[Event \"Different\"]\n\n{intro} 1.e4! $1 {comment} e5 (1... c5)\n2.Nf3 1-0";
        assertNotNull(PgnFingerprint.of(plain));
        assertEquals(PgnFingerprint.of(plain), PgnFingerprint.of(annotated));
    }

    @Test
    public void normalizesDefaultFenAndCastlingSpelling() {
        String start = "[FEN \"" + BoardPosition.START_FEN + "\"]\n";
        assertEquals(
                PgnFingerprint.of("1. O-O *"),
                PgnFingerprint.of(start + "[Variant \"Standard\"]\n1. 0-0 *"));
        assertEquals(
                PgnFingerprint.of("[Variant \"Duck\"]\n1. e4@d5 *"),
                PgnFingerprint.of("[FEN \"" + BoardPosition.DUCK_START_FEN + "\"]\n1. e4@d5 *"));
    }

    @Test
    public void distinguishesMovesVariantsAndStartingPositions() {
        String plain = PgnFingerprint.of("1. e4 e5 *");
        assertNotEquals(plain, PgnFingerprint.of("1. e4 c5 *"));
        assertNotEquals(plain, PgnFingerprint.of("[Variant \"King of the Hill\"]\n1. e4 e5 *"));
        assertNotEquals(
                plain,
                PgnFingerprint.of(
                        "[FEN \""
                                + BoardPosition.START_FEN.replace(" w ", " b ")
                                + "\"]\n1. e4 e5 *"));
        assertEquals(
                PgnFingerprint.of("[Variant \"Fischerandom\"]\n1. O-O *"),
                PgnFingerprint.of("[Variant \"Chess960\"]\n1. O-O *"));
    }

    @Test
    public void distinguishesExplicitCastlingRooksAndInfersChess960() {
        String inner = "[FEN \"4k3/8/8/8/8/8/8/4KR1R w F - 0 1\"]\n*";
        String outer = inner.replace(" w F ", " w H ");
        assertNotEquals(PgnFingerprint.of(inner), PgnFingerprint.of(outer));
        assertEquals(
                PgnFingerprint.of(inner), PgnFingerprint.of("[Variant \"Chess960\"]\n" + inner));
        assertEquals(
                PgnFingerprint.of(outer),
                PgnFingerprint.of("[Variant \"Chess960\"]\n" + outer.replace(" w H ", " w K ")));
    }

    @Test
    public void toleratesUnparseableLegacyPgnAndKeepsSyntaxOnlyImports() {
        assertNull(PgnFingerprint.of("1. ???"));
        assertNull(PgnFingerprint.of((String) null));
        assertNotNull(PgnFingerprint.of("[FEN \"broken\"]\n1. Ke4 *"));
    }
}
