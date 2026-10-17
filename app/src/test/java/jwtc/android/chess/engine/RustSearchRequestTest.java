package jwtc.android.chess.engine;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import jwtc.chess.Move;
import jwtc.chess.Pos;
import org.junit.Test;

public class RustSearchRequestTest {
    @Test
    public void chess960FilesLocatesTheOutermostRookOnEachSide() {
        int[] files = RustSearchRequest.chess960Files("4k3/8/8/8/8/8/8/1R2K1R1 w KQ - 0 1", true);
        assertArrayEquals(new int[] {4, 1, 6}, files);
    }

    @Test
    public void chess960FilesAreIndependentPerColour() {
        int[] white = RustSearchRequest.chess960Files("r3k3/8/8/8/8/8/8/4K1R1 w Kq - 0 1", true);
        int[] black = RustSearchRequest.chess960Files("r3k3/8/8/8/8/8/8/4K1R1 w Kq - 0 1", false);
        assertArrayEquals(new int[] {4, -1, 6}, white);
        assertArrayEquals(new int[] {4, 0, -1}, black);
    }

    @Test
    public void standardCastlingStaysKingToDestination() throws Exception {
        int move = Move.makeMoveOO(Pos.fromString("e1"), Pos.fromString("g1"));
        assertEquals("e1g1", RustSearchRequest.toRustCoordinate(move, null));
    }

    @Test
    public void chess960CastlingBecomesKingToRook() throws Exception {
        int move = Move.makeMoveOO(Pos.fromString("e1"), Pos.fromString("g1"));
        int[] files = {4, 0, 7};
        assertEquals("e1h1", RustSearchRequest.toRustCoordinate(move, files));
        int longMove = Move.makeMoveOOO(Pos.fromString("e1"), Pos.fromString("c1"));
        assertEquals("e1a1", RustSearchRequest.toRustCoordinate(longMove, files));
    }

    @Test
    public void ordinaryMovesAreUnaffectedByFiles() throws Exception {
        int move = Move.makeMove(Pos.fromString("e2"), Pos.fromString("e4"));
        int[] files = {4, 0, 7};
        assertEquals("e2e4", RustSearchRequest.toRustCoordinate(move, files));
    }

    @Test
    public void fromRustCoordinateRewritesKingToRookBackToDestination() {
        int[] files = {4, 0, 7};
        assertEquals("e1g1", RustSearchRequest.fromRustCoordinate("e1h1", files, 7));
        assertEquals("e1c1", RustSearchRequest.fromRustCoordinate("e1a1", files, 7));
    }

    @Test
    public void fromRustCoordinateLeavesOrdinaryMovesUnchanged() {
        int[] files = {4, 0, 7};
        assertEquals("e2e4", RustSearchRequest.fromRustCoordinate("e2e4", files, 7));
        assertEquals("e1d1", RustSearchRequest.fromRustCoordinate("e1d1", files, 7));
    }

    @Test
    public void fromRustCoordinateIgnoresAPieceThatOnlyCoincidentallySharesFiles() {
        // Files for the back rank (king on e, rook on h); a queen elsewhere on the e-file
        // sliding to the h-file on its OWN rank must not be reinterpreted as e1h1's castle.
        int[] files = {4, 0, 7};
        assertEquals("e5h5", RustSearchRequest.fromRustCoordinate("e5h5", files, 7));
    }

    @Test
    public void fromRustCoordinateIsTheInverseOfToRustCoordinateForCastling() throws Exception {
        int[] files = {4, 1, 6};
        int shortCastle = Move.makeMoveOO(Pos.fromString("e1"), Pos.fromString("g1"));
        String rust = RustSearchRequest.toRustCoordinate(shortCastle, files);
        assertEquals("e1g1", RustSearchRequest.fromRustCoordinate(rust, files, 7));
    }

}
