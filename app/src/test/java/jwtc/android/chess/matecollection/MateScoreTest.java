package jwtc.android.chess.matecollection;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MateScoreTest {
    @Test
    public void solvedCountsAsPlayedAndSolved() {
        MateScore score = MateScore.NONE.afterSolved();
        assertEquals(1, score.played);
        assertEquals(1, score.solved);
    }

    @Test
    public void wrongCountsAsPlayedOnly() {
        MateScore score = MateScore.NONE.afterWrong();
        assertEquals(1, score.played);
        assertEquals(0, score.solved);
    }

    @Test
    public void solvingAfterAWrongReplyCountsPlayedTwice() {
        MateScore score = MateScore.NONE.afterWrong().afterSolved();
        assertEquals(2, score.played);
        assertEquals(1, score.solved);
        assertEquals(50f, score.percentage(), 0.001f);
    }

    @Test
    public void percentageIsZeroWithoutPlays() {
        assertEquals(0f, MateScore.NONE.percentage(), 0.001f);
    }
}
