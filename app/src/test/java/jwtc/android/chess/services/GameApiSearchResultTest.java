package jwtc.android.chess.services;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GameApiSearchResultTest {

    /** move, duck, value, depth, evaluations, 20 PV moves, 20 duck moves, has-evaluation, completed depth. */
    private static int[] rawResult(int move, int duck, int value, int depth, int evalCount,
                                    int[] pv, int[] duckPv, int hasEvaluation, int completedDepth) {
        int[] raw = new int[47];
        raw[0] = move;
        raw[1] = duck;
        raw[2] = value;
        raw[3] = depth;
        raw[4] = evalCount;
        System.arraycopy(pv, 0, raw, 5, pv.length);
        System.arraycopy(duckPv, 0, raw, 25, duckPv.length);
        raw[45] = hasEvaluation;
        raw[46] = completedDepth;
        return raw;
    }

    @Test
    public void decodesScalarFieldsAndTruncatesThePvToTheReportedDepth() {
        int[] pv = {101, 102, 103, 0, 0};
        int[] duckPv = {-1, -1, -1, 0, 0};
        int[] raw = rawResult(101, -1, 100, 3, 50000, pv, duckPv, 1, 6);

        GameApi.SearchResult result = GameApi.SearchResult.decode(raw);

        assertEquals(101, result.move);
        assertEquals(-1, result.duckMove);
        assertEquals(100, result.score);
        assertEquals(3, result.depth);
        assertEquals(50000, result.evalCount);
        assertTrue(result.hasEvaluation);
        assertEquals(6, result.completedDepth);
        assertArrayEquals(new int[] {101, 102, 103}, result.principalVariation);
        assertArrayEquals(new int[] {-1, -1, -1}, result.principalDuckVariation);
    }

    @Test
    public void zeroDepthYieldsAnEmptyPrincipalVariation() {
        int[] raw = rawResult(0, -1, 0, 0, 0, new int[0], new int[0], 0, 0);

        GameApi.SearchResult result = GameApi.SearchResult.decode(raw);

        assertFalse(result.hasEvaluation);
        assertArrayEquals(new int[0], result.principalVariation);
        assertArrayEquals(new int[0], result.principalDuckVariation);
    }

    @Test
    public void depthIsClampedToTheTwentyMoveBuffer() {
        int[] pv = new int[20];
        int[] duckPv = new int[20];
        for (int i = 0; i < 20; i++) {
            pv[i] = i + 1;
            duckPv[i] = -1;
        }
        int[] raw = rawResult(pv[0], -1, 100, 25, 1000, pv, duckPv, 1, 25);

        GameApi.SearchResult result = GameApi.SearchResult.decode(raw);

        assertEquals(20, result.principalVariation.length);
        assertArrayEquals(pv, result.principalVariation);
    }
}
