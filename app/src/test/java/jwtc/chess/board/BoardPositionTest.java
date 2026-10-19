package jwtc.chess.board;

import org.junit.Test;
import java.util.HashSet;
import java.util.Set;
import jwtc.chess.Move;
import static org.junit.Assert.*;

public class BoardPositionTest {
    @Test public void incompleteSetupRoundTripsWithoutRequiringKings() {
        String fen = "8/8/8/3$4/8/8/4P3/8 b - - 17 42";
        BoardPosition position = new BoardPosition(fen);
        assertEquals(fen, position.fen());
        assertEquals(27, position.duck());
        position.squares[60] = 'K';
        assertEquals('K', new BoardPosition(position.fen()).squares[60]);
    }
    @Test public void rejectsMalformedPlacementAndCoordinates() {
        for (String fen : new String[]{"9/8/8/8/8/8/8/8 w - - 0 1", "8/8/8/8/8/8/8/8 x - - 0 1",
                "8/8/8/8/8/8/8/8 w - a0 0 1"}) {
            try { new BoardPosition(fen); fail(fen); } catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void allChess960SeedsAreUniqueAndHaveOppositeBishopsAndKingBetweenRooks() {
        Set<String> positions = new HashSet<>();
        for (int seed = 0; seed < 960; seed++) {
            String fen = BoardPosition.chess960Fen(seed);
            assertTrue(positions.add(fen));
            char[] squares = new BoardPosition(fen).squares;
            int bishopColour = -1, firstRook = -1, king = -1, lastRook = -1;
            for (int i = 0; i < 8; i++) {
                if (squares[i] == 'b') {
                    if (bishopColour < 0) bishopColour = i % 2;
                    else assertNotEquals(bishopColour, i % 2);
                }
                if (squares[i] == 'r') { if (firstRook < 0) firstRook = i; lastRook = i; }
                if (squares[i] == 'k') king = i;
            }
            assertTrue(firstRook < king && king < lastRook);
        }
        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", BoardPosition.chess960Fen(518));
    }
    @Test public void encodesSpecialMovesWithoutCppMoveArray() {
        BoardPosition position = new BoardPosition("4k3/P7/8/3pP3/8/8/8/1R2K1R1 w KQ d6 0 1");
        assertTrue(Move.isEP(position.packedMove("e5d6", "exd6")));
        int castle = position.packedMove("e1g1", "O-O");
        assertTrue(Move.isOO(castle));
        assertEquals(62, Move.getTo(castle));
        assertTrue(Move.isOOO(position.packedMove("e1b1", "O-O-O+")));
        int promotion = position.packedMove("a7a8q", "a8=Q+");
        assertEquals(BoardConstants.QUEEN, Move.getPromotionPiece(promotion));
        assertTrue(Move.isCheck(promotion));
        assertTrue(Move.isFirstPawnMove(new BoardPosition(BoardPosition.chess960Fen(518)).packedMove("e2e4", "e4")));
    }
    @Test public void ecoInitialHashRetainsBookConvention() {
        String fen = BoardPosition.chess960Fen(518);
        assertEquals(EcoHash.initial(fen), EcoHash.initial(fen.replace(" w KQkq ", " b - ")));
    }
}
