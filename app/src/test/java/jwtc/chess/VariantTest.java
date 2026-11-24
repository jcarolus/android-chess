package jwtc.chess;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import jwtc.chess.board.BoardPosition;
import org.junit.Test;

public class VariantTest {
    private static final String CHESS_960_FEN =
            "bqnrknrb/pppppppp/8/8/8/8/PPPPPPPP/BQNRKNRB w GDgd - 0 1";

    @Test
    public void readsEveryTagSpelling() {
        assertEquals(Variant.CHESS_960, Variant.fromPgnTag("Chess960"));
        assertEquals(Variant.CHESS_960, Variant.fromPgnTag("chess 960"));
        assertEquals(Variant.CHESS_960, Variant.fromPgnTag("Fischerandom"));
        assertEquals(Variant.CHESS_960, Variant.fromPgnTag("Fischer Random"));
        assertEquals(Variant.DUCK, Variant.fromPgnTag(" duck "));
        assertEquals(Variant.KING_OF_THE_HILL, Variant.fromPgnTag("King of the Hill"));
        assertEquals(Variant.KING_OF_THE_HILL, Variant.fromPgnTag("kingOfTheHill"));
    }

    @Test
    public void leavesStandardAndUnknownTagsToTheStartingPosition() {
        assertNull(Variant.fromPgnTag(null));
        assertNull(Variant.fromPgnTag(""));
        assertNull(Variant.fromPgnTag("Standard"));
        assertNull(Variant.fromPgnTag("From Position"));
        assertNull(Variant.fromPgnTag("Atomic"));
    }

    @Test
    public void writtenTagsReadBack() {
        for (Variant variant : Variant.values()) {
            if (variant == Variant.STANDARD) {
                assertNull(variant.pgnTag());
            } else {
                assertEquals(variant, Variant.fromPgnTag(variant.pgnTag()));
            }
        }
        assertEquals("Chess960", Variant.CHESS_960.pgnTag());
    }

    @Test
    public void infersFromTheStartingPosition() {
        assertEquals(Variant.STANDARD, Variant.infer(null));
        assertEquals(Variant.STANDARD, Variant.infer(new BoardPosition(BoardPosition.START_FEN)));
        assertEquals(Variant.DUCK, Variant.infer(new BoardPosition(BoardPosition.DUCK_START_FEN)));
        assertEquals(Variant.CHESS_960, Variant.infer(new BoardPosition(CHESS_960_FEN)));
    }

    @Test
    public void namedTagWinsOverThePosition() {
        BoardPosition standard = new BoardPosition(BoardPosition.START_FEN);
        assertEquals(Variant.KING_OF_THE_HILL, Variant.of("King of the Hill", standard));
        assertEquals(Variant.CHESS_960, Variant.of("Chess960", standard));
        assertEquals(Variant.CHESS_960, Variant.of("Standard", new BoardPosition(CHESS_960_FEN)));
    }

    @Test
    public void nativeCodesAndWireNamesAreStable() {
        assertEquals(0, Variant.STANDARD.ordinal());
        assertEquals(1, Variant.CHESS_960.ordinal());
        assertEquals(2, Variant.DUCK.ordinal());
        assertEquals(3, Variant.KING_OF_THE_HILL.ordinal());
        assertEquals("CHESS_960", Variant.CHESS_960.name());
    }
}
