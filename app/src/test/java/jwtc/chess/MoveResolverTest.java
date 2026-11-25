package jwtc.chess;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.Arrays;
import java.util.List;
import jwtc.chess.MoveResolver.Resolution;
import jwtc.chess.board.BoardConstants;
import org.junit.Test;

public class MoveResolverTest {
    private static final LegalMove KNIGHT_B1_D2 = move(Move.makeMove(sq("b1"), sq("d2")), "Nbd2");
    private static final LegalMove KNIGHT_F3_D2 = move(Move.makeMove(sq("f3"), sq("d2")), "Nfd2");
    private static final LegalMove KNIGHT_G1_F3 = move(Move.makeMove(sq("g1"), sq("f3")), "Nf3");
    private static final LegalMove PAWN_E4_D5 = move(Move.makeMoveHit(sq("e4"), sq("d5")), "exd5");
    private static final LegalMove PAWN_E4_E5 = move(Move.makeMove(sq("e4"), sq("e5")), "e5");
    private static final LegalMove SHORT_CASTLE =
            move(Move.makeMoveOO(sq("e1"), sq("g1")), "e1g1", "O-O");
    private static final LegalMove LONG_CASTLE =
            move(Move.makeMoveOOO(sq("e1"), sq("c1")), "e1c1", "O-O-O+");

    @Test
    public void sanResolvesDisambiguatedPieces() {
        List<LegalMove> moves = Arrays.asList(KNIGHT_B1_D2, KNIGHT_F3_D2);
        assertSame(KNIGHT_B1_D2, san(moves, "Nbd2"));
        assertSame(KNIGHT_F3_D2, san(moves, "Nfd2"));
        assertNull("two knights reach d2", san(moves, "Nd2"));
    }

    @Test
    public void sanAcceptsMoreDisambiguationThanNeeded() {
        List<LegalMove> moves = Arrays.asList(KNIGHT_G1_F3, PAWN_E4_E5);
        assertSame(KNIGHT_G1_F3, san(moves, "Ngf3"));
        assertSame(KNIGHT_G1_F3, san(moves, "N1f3"));
        assertSame(KNIGHT_G1_F3, san(moves, "Ng1f3"));
        assertNull("wrong origin file", san(moves, "Nhf3"));
    }

    @Test
    public void sanIgnoresCheckMarksAnnotationsAndWhitespace() {
        List<LegalMove> moves = Arrays.asList(KNIGHT_G1_F3);
        assertSame(KNIGHT_G1_F3, san(moves, "Nf3+"));
        assertSame(KNIGHT_G1_F3, san(moves, " Nf3!? "));
        assertSame(KNIGHT_G1_F3, san(moves, "Nf3#?!"));
    }

    @Test
    public void sanIsStrictAboutPieceLettersAndCaptures() {
        List<LegalMove> moves = Arrays.asList(KNIGHT_G1_F3, PAWN_E4_D5, PAWN_E4_E5);
        assertNull(san(moves, "nf3"));
        assertNull("a capture must be written", san(moves, "ed5"));
        assertNull("not a capture", san(moves, "exe5"));
        assertSame(PAWN_E4_D5, san(moves, "exd5"));
        assertSame(PAWN_E4_E5, san(moves, "e5"));
    }

    @Test
    public void sanResolvesPromotionWithOrWithoutEquals() {
        List<LegalMove> moves = promotions(sq("e7"), sq("e8"), "e8");
        assertEquals(BoardConstants.KNIGHT, promotionOf(san(moves, "e8=N")));
        assertEquals(BoardConstants.QUEEN, promotionOf(san(moves, "e8Q+")));
        assertNull("the piece must be written", san(moves, "e8"));
    }

    @Test
    public void sanResolvesCastlingWithLettersOrZeros() {
        List<LegalMove> moves = Arrays.asList(SHORT_CASTLE, LONG_CASTLE);
        assertSame(SHORT_CASTLE, san(moves, "O-O"));
        assertSame(SHORT_CASTLE, san(moves, "0-0+"));
        assertSame(LONG_CASTLE, san(moves, "O-O-O"));
        assertSame(LONG_CASTLE, san(moves, "0-0-0#"));
        assertNull(san(Arrays.asList(SHORT_CASTLE), "O-O-O"));
    }

    @Test
    public void sanReadsTheDuckPlacement() {
        List<LegalMove> moves = Arrays.asList(KNIGHT_G1_F3, SHORT_CASTLE);
        MoveResolver.SanMove knight = MoveResolver.san(moves, "Nf3@d4+");
        assertSame(KNIGHT_G1_F3, knight.move);
        assertEquals(sq("d4"), knight.duckSquare);
        assertEquals(sq("a6"), MoveResolver.san(moves, "O-O@a6").duckSquare);
        assertEquals(-1, MoveResolver.san(moves, "Nf3").duckSquare);
    }

    @Test
    public void sanRejectsWhatIsNotAMove() {
        List<LegalMove> moves = Arrays.asList(KNIGHT_G1_F3);
        assertNull(MoveResolver.san(moves, null));
        assertNull(san(moves, ""));
        assertNull(san(moves, "1-0"));
        assertNull(san(moves, "Nf9"));
    }

    @Test
    public void coordinatesCastleFromKingToTheRookCorner() {
        List<LegalMove> moves = Arrays.asList(SHORT_CASTLE, LONG_CASTLE);
        assertSame(SHORT_CASTLE, coordinates(moves, Variant.STANDARD, "e1", "h1", -1).move);
        assertSame(LONG_CASTLE, coordinates(moves, Variant.STANDARD, "e1", "a1", -1).move);
        assertEquals(
                Resolution.Kind.NONE, coordinates(moves, Variant.STANDARD, "e1", "g1", -1).kind);
    }

    @Test
    public void coordinatesCastleInChess960AsTheBoardWritesIt() {
        LegalMove castle = move(Move.makeMoveOO(sq("e1"), sq("g1")), "e1f1", "O-O");
        List<LegalMove> moves = Arrays.asList(castle);
        assertSame(castle, coordinates(moves, Variant.CHESS_960, "e1", "f1", -1).move);
        assertEquals(
                Resolution.Kind.NONE, coordinates(moves, Variant.CHESS_960, "e1", "h1", -1).kind);
    }

    @Test
    public void coordinatesAskForAnOmittedPromotionPiece() {
        List<LegalMove> moves = promotions(sq("a7"), sq("a8"), "a8");
        Resolution omitted = coordinates(moves, Variant.STANDARD, "a7", "a8", -1);
        assertEquals(Resolution.Kind.NEEDS_PROMOTION, omitted.kind);
        assertEquals(sq("a7"), omitted.from);
        assertEquals(sq("a8"), omitted.to);
        Resolution knight = coordinates(moves, Variant.STANDARD, "a7", "a8", BoardConstants.KNIGHT);
        assertEquals(BoardConstants.KNIGHT, Move.getPromotionPiece(knight.move.packed));
    }

    @Test
    public void coordinatesWithoutALegalMoveResolveToNothing() {
        List<LegalMove> moves = Arrays.asList(PAWN_E4_E5);
        assertSame(PAWN_E4_E5, coordinates(moves, Variant.STANDARD, "e4", "e5", -1).move);
        assertEquals(
                Resolution.Kind.NONE, coordinates(moves, Variant.STANDARD, "e4", "e6", -1).kind);
        assertEquals(
                Resolution.Kind.NONE,
                coordinates(moves, Variant.STANDARD, "e4", "e5", BoardConstants.QUEEN).kind);
    }

    @Test
    public void spokenSanIgnoresCaseCapturesAndChecks() {
        List<LegalMove> moves = Arrays.asList(KNIGHT_G1_F3, PAWN_E4_D5);
        assertSame(KNIGHT_G1_F3, MoveResolver.spokenSan(moves, "nf3").move);
        assertSame(PAWN_E4_D5, MoveResolver.spokenSan(moves, "ed5").move);
        assertSame(PAWN_E4_D5, MoveResolver.spokenSan(moves, "exd5").move);
    }

    @Test
    public void spokenSanRejectsWhatCaseNoLongerTellsApart() {
        LegalMove bishop = move(Move.makeMoveHit(sq("f1"), sq("c4")), "Bxc4");
        LegalMove pawn = move(Move.makeMoveHit(sq("b3"), sq("c4")), "bxc4");
        assertEquals(
                Resolution.Kind.NONE,
                MoveResolver.spokenSan(Arrays.asList(bishop, pawn), "bxc4").kind);
    }

    @Test
    public void spokenCastleNeedsASingleCastlingMove() {
        assertSame(
                SHORT_CASTLE,
                MoveResolver.spokenSan(Arrays.asList(SHORT_CASTLE, KNIGHT_G1_F3), "castle").move);
        assertEquals(
                Resolution.Kind.NONE,
                MoveResolver.spokenSan(Arrays.asList(SHORT_CASTLE, LONG_CASTLE), "castle").kind);
        assertSame(
                LONG_CASTLE,
                MoveResolver.spokenSan(Arrays.asList(SHORT_CASTLE, LONG_CASTLE), "o-o-o").move);
    }

    @Test
    public void spokenSanAsksForAnOmittedPromotionPiece() {
        List<LegalMove> moves = promotions(sq("e7"), sq("e8"), "e8");
        Resolution omitted = MoveResolver.spokenSan(moves, "e8");
        assertEquals(Resolution.Kind.NEEDS_PROMOTION, omitted.kind);
        assertEquals(sq("e7"), omitted.from);
        assertEquals(sq("e8"), omitted.to);
        assertEquals(
                BoardConstants.ROOK,
                Move.getPromotionPiece(MoveResolver.spokenSan(moves, "e8r").move.packed));
    }

    @Test
    public void spokenSanRefusesAPromotionTwoPawnsCouldMake() {
        List<LegalMove> moves = new java.util.ArrayList<>(promotions(sq("c7"), sq("d8"), "cxd8"));
        moves.addAll(promotions(sq("e7"), sq("d8"), "exd8"));
        assertEquals(Resolution.Kind.NONE, MoveResolver.spokenSan(moves, "d8").kind);
        assertEquals(Resolution.Kind.NEEDS_PROMOTION, MoveResolver.spokenSan(moves, "cxd8").kind);
    }

    private static LegalMove san(List<LegalMove> moves, String token) {
        MoveResolver.SanMove resolved = MoveResolver.san(moves, token);
        return resolved == null ? null : resolved.move;
    }

    private static Resolution coordinates(
            List<LegalMove> moves, Variant variant, String from, String to, int promotion) {
        return MoveResolver.coordinates(moves, variant, sq(from), sq(to), promotion);
    }

    private static int promotionOf(LegalMove move) {
        return Move.getPromotionPiece(move.packed);
    }

    private static List<LegalMove> promotions(int from, int to, String san) {
        boolean capture = san.contains("x");
        int[] pieces = {
            BoardConstants.QUEEN, BoardConstants.ROOK, BoardConstants.BISHOP, BoardConstants.KNIGHT
        };
        String letters = "QRBN";
        LegalMove[] moves = new LegalMove[pieces.length];
        for (int i = 0; i < pieces.length; i++) {
            moves[i] =
                    move(
                            Move.makeMovePromotion(from, to, pieces[i], capture),
                            san + "=" + letters.charAt(i));
        }
        return Arrays.asList(moves);
    }

    private static LegalMove move(int packed, String san) {
        return move(
                packed, Pos.toString(Move.getFrom(packed)) + Pos.toString(Move.getTo(packed)), san);
    }

    private static LegalMove move(int packed, String coordinate, String san) {
        return new LegalMove(packed, coordinate, san);
    }

    private static int sq(String square) {
        try {
            return Pos.fromString(square);
        } catch (Exception invalid) {
            throw new IllegalArgumentException(square, invalid);
        }
    }
}
