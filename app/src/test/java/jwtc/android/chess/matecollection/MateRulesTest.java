package jwtc.android.chess.matecollection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import jwtc.chess.board.BoardConstants;

public class MateRulesTest {
    @Test
    public void plyBudgetShrinksWithEachMovePlayed() {
        assertEquals(4, MateRules.plyBudget(0));
        assertEquals(3, MateRules.plyBudget(1));
        assertEquals(2, MateRules.plyBudget(2));
    }

    @Test
    public void usersTurnNeedsAMateScore() {
        assertTrue(MateRules.isMateReply(BoardConstants.VALUATION_MATE, true));
        assertFalse(MateRules.isMateReply(-BoardConstants.VALUATION_MATE, true));
        assertFalse(MateRules.isMateReply(0, true));
    }

    @Test
    public void opponentsTurnNeedsAMatedScore() {
        assertTrue(MateRules.isMateReply(-BoardConstants.VALUATION_MATE, false));
        assertFalse(MateRules.isMateReply(BoardConstants.VALUATION_MATE, false));
        assertFalse(MateRules.isMateReply(250, false));
    }

    @Test
    public void restoreKeepsAValidPosition() {
        assertEquals(0, MateRules.restore(0, 5));
        assertEquals(4, MateRules.restore(4, 5));
    }

    @Test
    public void restoreWrapsToFirstPastTheEnd() {
        assertEquals(0, MateRules.restore(5, 5));
        assertEquals(0, MateRules.restore(9, 5));
    }

    @Test
    public void restoreHandlesNegativeAndEmpty() {
        assertEquals(0, MateRules.restore(-1, 5));
        assertEquals(0, MateRules.restore(3, 0));
    }

    @Test
    public void nextStopsAtTheLastPosition() {
        assertTrue(MateRules.hasNext(3, 5));
        assertFalse(MateRules.hasNext(4, 5));
        assertFalse(MateRules.hasNext(0, 0));
    }

    @Test
    public void previousStopsAtTheFirstPosition() {
        assertEquals(2, MateRules.previous(3));
        assertEquals(0, MateRules.previous(0));
    }
}
