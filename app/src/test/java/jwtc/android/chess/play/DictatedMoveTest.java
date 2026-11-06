package jwtc.android.chess.play;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import jwtc.chess.board.BoardConstants;
import org.junit.Test;

public class DictatedMoveTest {
    @Test
    public void parsesDigitAndSpokenRanks() {
        DictatedMove digits = DictatedMove.parse("e2 e4");
        DictatedMove spoken = DictatedMove.parse("  E two  e FOUR ");
        assertEquals(52, digits.from);
        assertEquals(36, digits.to);
        assertEquals(digits.from, spoken.from);
        assertEquals(digits.to, spoken.to);
        assertEquals(-1, spoken.promotion);
    }

    @Test
    public void parsesPromotion() {
        DictatedMove move = DictatedMove.parse("e seven e eight knight");
        assertEquals(12, move.from);
        assertEquals(4, move.to);
        assertEquals(BoardConstants.KNIGHT, move.promotion);
    }

    @Test
    public void rejectsAmbiguousOrIncompletePhrases() {
        assertNull(DictatedMove.parse("e2 to e4"));
        assertNull(DictatedMove.parse("e9 e4"));
        assertNull(DictatedMove.parse("e2 e"));
        assertNull(DictatedMove.parse("e7 e8 king"));
    }
}
