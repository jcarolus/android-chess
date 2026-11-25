package jwtc.chess;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import jwtc.chess.GameTree.Node;
import jwtc.chess.board.BoardConstants;
import org.junit.Before;
import org.junit.Test;

public class GameTreeTest {
    private static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private GameTree tree;

    @Before
    public void setUp() {
        tree = new GameTree(START, 0, 0);
    }

    private Node add(Node parent, String san, int move) {
        return tree.append(parent, new PGNEntry(san, "", move, -1), 0, 0, false);
    }

    @Test
    public void exportsMainLineWithWhiteMoveNumbers() {
        Node e4 = add(tree.getRoot(), "e4", 1);
        add(add(e4, "e5", 2), "Nf3", 3);
        assertEquals("1. e4 e5 2. Nf3", tree.exportMoves());
    }

    @Test
    public void exportsVariationsInParentheses() {
        Node e4 = add(tree.getRoot(), "e4", 1);
        add(tree.getRoot(), "d4", 4);
        Node e5 = add(e4, "e5", 2);
        add(e4, "c5", 5);
        add(e5, "Nf3", 3);
        assertEquals("1. e4 ( 1. d4 ) e5 ( c5 ) 2. Nf3", tree.exportMoves());
    }

    @Test
    public void exportsBlackMoveNumbersImmediatelyAfterComments() {
        Node e4 = add(tree.getRoot(), "e4", 1);
        tree.setAnnotation(e4, "main");
        Node e5 = add(e4, "e5", 2);
        Node c5 = add(e4, "c5", 3);
        tree.setLeadingComment(c5, "alternative");
        add(add(e5, "Nf3", 4), "Nc6", 5);

        assertEquals(
                "1. e4 {main} 1... e5 ( {alternative} 1... c5 ) 2. Nf3 Nc6", tree.exportMoves());
    }

    @Test
    public void replacingContinuationRemovesFutureBranchesAndKeepsEarlierMoves() {
        Node e4 = add(tree.getRoot(), "e4", 1);
        Node d4 = add(tree.getRoot(), "d4", 4);
        Node e5 = add(e4, "e5", 2);
        Node nf3 = add(e5, "Nf3", 3);
        Node c5 = add(e4, "c5", 5);
        tree.setFinalState(nf3, BoardConstants.DRAW_AGREEMENT);

        Node replacement = tree.replaceContinuation(e4, new PGNEntry("c6", "", 6, -1), 0, 0);

        assertTrue(tree.isMainLine(replacement));
        assertSame(replacement, tree.mainLineEnd());
        assertSame(e4, replacement.getParent());
        assertTrue(tree.contains(d4));
        assertFalse(tree.contains(e5));
        assertFalse(tree.contains(nf3));
        assertFalse(tree.contains(c5));
        assertEquals("1. e4 ( 1. d4 ) c6", tree.exportMoves());
    }

    @Test
    public void replacingWithTheSameMoveStillDiscardsItsFuture() {
        Node e4 = add(tree.getRoot(), "e4", 1);
        add(e4, "e5", 2);
        Node replacement =
                tree.replaceContinuation(tree.getRoot(), new PGNEntry("e4", "", 1, -1), 0, 0);
        assertFalse(tree.contains(e4));
        assertSame(replacement, tree.mainLineEnd());
        assertEquals("1. e4", tree.exportMoves());
    }

    @Test
    public void replacingAtAnEndpointClearsItsResult() {
        Node e4 = add(tree.getRoot(), "e4", 1);
        tree.setFinalState(e4, BoardConstants.DRAW_AGREEMENT);
        Node e5 = tree.replaceContinuation(e4, new PGNEntry("e5", "", 2, -1), 0, 0);
        assertEquals(-1, e4.getFinalState());
        assertSame(e5, tree.mainLineEnd());
    }

    @Test
    public void exportsCommentsAndNags() {
        tree.setRootComment("start");
        Node e4 = add(tree.getRoot(), "e4", 1);
        tree.addNag(e4, 1);
        tree.addNag(e4, 14);
        tree.setAnnotation(e4, "the {best} move");
        Node alt = add(tree.getRoot(), "d4", 4);
        tree.setLeadingComment(alt, "other");
        assertEquals(
                "{start} 1. e4 $1 $14 {the [best] move} ( {other} 1. d4 )", tree.exportMoves());
    }

    @Test
    public void exportsDuckPlacement() {
        tree.append(tree.getRoot(), new PGNEntry("e4", "", 1, 20), 0, 0, false);
        assertEquals("1. e4@" + Pos.toString(20), tree.exportMoves());
    }

    @Test
    public void exportsFromPositionWithAllRemainingVariationsAndAnnotations() {
        tree.setRootComment("old introduction");
        Node e4 = add(tree.getRoot(), "e4", 1);
        add(tree.getRoot(), "d4", 2);
        tree.setAnnotation(e4, "new introduction");
        tree.addNag(e4, 1);
        Node e5 = add(e4, "e5", 3);
        Node c5 = add(e4, "c5", 4);
        tree.setLeadingComment(c5, "Sicilian");
        tree.addNag(c5, 5);
        tree.setAnnotation(c5, "counterplay");
        add(e5, "Nf3", 5);
        Node nf3 = add(c5, "Nf3", 6);
        add(c5, "Nc3", 7);
        add(nf3, "d6", 8);

        assertEquals(
                "{new introduction} 1... e5 ( {Sicilian} 1... c5 $5 {counterplay} "
                        + "2. Nf3 ( 2. Nc3 ) d6 ) 2. Nf3",
                tree.exportMovesFrom(e4));
        assertEquals(8, tree.countMoves(tree.getRoot()));
    }

    @Test
    public void exportsFromVariationWithoutEarlierBranches() {
        Node e4 = add(tree.getRoot(), "e4", 1);
        add(e4, "e5", 2);
        Node c5 = add(e4, "c5", 3);
        tree.setLeadingComment(c5, "comment on removed move");
        add(c5, "Nf3", 4);
        assertEquals("2. Nf3", tree.exportMovesFrom(c5));
    }

    @Test
    public void exportsBlackStartWithOriginalNumberEvenWithoutComment() {
        tree = new GameTree("8/8/8/8/8/8/8/K6k w - - 0 23", 0, 0);
        Node ka2 = add(tree.getRoot(), "Ka2", 1);
        add(ka2, "Kg1", 2);
        assertEquals("23... Kg1", tree.exportMovesFrom(ka2));
    }

    @Test
    public void exportsEndpointAsPositionOnlyWithItsComment() {
        Node e4 = add(tree.getRoot(), "e4", 1);
        assertEquals("", tree.exportMovesFrom(e4));
        tree.setAnnotation(e4, "study this position");
        assertEquals("{study this position}", tree.exportMovesFrom(e4));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsExportFromDetachedPosition() {
        Node e4 = add(tree.getRoot(), "e4", 1);
        tree.removeContinuation(e4);
        tree.exportMovesFrom(e4);
    }

    @Test
    public void moveNumbersFollowTheStartingPosition() {
        GameTree black = new GameTree("8/8/8/8/8/8/8/K6k b - - 0 7", 0, 1);
        Node first = black.append(black.getRoot(), new PGNEntry("Kg1", "", 1, -1), 0, 0, false);
        Node second = black.append(first, new PGNEntry("Ka2", "", 2, -1), 0, 0, false);
        assertEquals("7...", black.moveNumber(first));
        assertEquals("8.", black.moveNumber(second));
        assertEquals("Kg1 8. Ka2", black.exportMoves());
        black.setRootComment("start");
        assertEquals("{start} 7... Kg1 8. Ka2", black.exportMoves());
    }

    @Test
    public void reusesAnIdenticalContinuationOnlyWhenAsked() {
        Node e4 = add(tree.getRoot(), "e4", 1);
        assertSame(e4, tree.append(tree.getRoot(), new PGNEntry("e4", "", 1, -1), 0, 0, true));
        assertEquals(1, tree.getRoot().getChildren().size());
        Node duplicate = tree.append(tree.getRoot(), new PGNEntry("e4", "", 1, -1), 0, 0, false);
        assertFalse(duplicate == e4);
        assertEquals(2, tree.getRoot().getChildren().size());
    }

    @Test
    public void mainLineFollowsFirstChildren() {
        Node e4 = add(tree.getRoot(), "e4", 1);
        Node d4 = add(tree.getRoot(), "d4", 4);
        Node e5 = add(e4, "e5", 2);
        assertEquals(2, tree.mainLine().size());
        assertSame(e5, tree.mainLineEnd());
        assertTrue(tree.isMainLine(e5));
        assertFalse(tree.isMainLine(d4));
    }

    @Test
    public void promotingAVariationMakesItTheMainLine() {
        Node e4 = add(tree.getRoot(), "e4", 1);
        Node d4 = add(tree.getRoot(), "d4", 4);
        assertTrue(tree.promoteVariation(d4));
        assertSame(d4, tree.getRoot().getNext());
        assertEquals("1. d4 ( 1. e4 )", tree.exportMoves());
        assertFalse(tree.promoteVariation(d4));
        assertTrue(tree.deleteVariation(e4));
        assertEquals("1. d4", tree.exportMoves());
    }

    @Test
    public void deletingTheMainContinuationIsRefused() {
        Node e4 = add(tree.getRoot(), "e4", 1);
        assertFalse(tree.deleteVariation(e4));
        assertTrue(tree.contains(e4));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNagsOutOfRange() {
        tree.addNag(add(tree.getRoot(), "e4", 1), 256);
    }

    @Test
    public void terminalResultsStayWithTheirLineWhenPromotedOrResumed() {
        Node e4 = add(tree.getRoot(), "e4", 1);
        Node d4 = add(tree.getRoot(), "d4", 2);
        assertTrue(tree.setFinalState(e4, BoardConstants.BLACK_RESIGNED));
        assertTrue(tree.setFinalState(d4, BoardConstants.WHITE_FORFEIT_TIME));
        assertEquals(BoardConstants.BLACK_RESIGNED, tree.mainLineEnd().getFinalState());

        assertTrue(tree.promoteVariation(d4));
        assertEquals(BoardConstants.WHITE_FORFEIT_TIME, tree.mainLineEnd().getFinalState());
        assertEquals(BoardConstants.BLACK_RESIGNED, e4.getFinalState());

        assertTrue(tree.setFinalState(d4, -1));
        assertEquals(-1, d4.getFinalState());
        assertEquals(BoardConstants.BLACK_RESIGNED, e4.getFinalState());
    }

    @Test
    public void extendingALineClearsItsOldResult() {
        Node e4 = add(tree.getRoot(), "e4", 1);
        tree.setFinalState(e4, BoardConstants.BLACK_RESIGNED);
        Node e5 = add(e4, "e5", 2);
        assertEquals(-1, e4.getFinalState());
        assertEquals(-1, e5.getFinalState());
        assertFalse(tree.setFinalState(e4, BoardConstants.WHITE_FORFEIT_TIME));
        tree.removeContinuation(e5);
        assertEquals(-1, e4.getFinalState());
    }

    @Test
    public void removingTheMainContinuationPromotesTheNextVariation() {
        Node e4 = add(tree.getRoot(), "e4", 1);
        Node e5 = add(e4, "e5", 2);
        Node c5 = add(e4, "c5", 3);
        add(e5, "Nf3", 4);

        assertTrue(tree.removeContinuation(e5));

        assertSame(c5, e4.getNext());
        assertFalse(tree.contains(e5));
        assertEquals("1. e4 c5", tree.exportMoves());
    }

    @Test
    public void countsMovesInSubtreeIncludingVariations() {
        Node e4 = add(tree.getRoot(), "e4", 1);
        Node e5 = add(e4, "e5", 2);
        add(e4, "c5", 3);
        Node nf3 = add(e5, "Nf3", 4);
        add(add(e5, "Bc4", 5), "Nf6", 6);
        add(nf3, "Nc6", 7);

        assertEquals(7, tree.countMoves(tree.getRoot()));
        assertEquals(5, tree.countMoves(e5));
        assertEquals(2, tree.countMoves(nf3));
    }

    @Test
    public void detectsVariationsAlongTheLine() {
        Node e4 = add(tree.getRoot(), "e4", 1);
        Node e5 = add(e4, "e5", 2);
        Node nf3 = add(e5, "Nf3", 3);
        add(nf3, "Nc6", 4);
        assertFalse(tree.hasVariationsFrom(e5));

        add(nf3, "d6", 5);
        assertTrue(tree.hasVariationsFrom(e5));
        assertFalse(tree.hasVariationsFrom(nf3.getNext()));
    }

    @Test
    public void anEmptyGameCanHaveATerminalResult() {
        assertTrue(tree.setFinalState(tree.getRoot(), BoardConstants.WHITE_FORFEIT_TIME));
        assertEquals(BoardConstants.WHITE_FORFEIT_TIME, tree.getRoot().getFinalState());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsResultsOnDetachedNodes() {
        Node e4 = add(tree.getRoot(), "e4", 1);
        tree.removeContinuation(e4);
        tree.setFinalState(e4, BoardConstants.WHITE_FORFEIT_TIME);
    }

    @Test
    public void enginePositionIncludesOnlySelectedVariationAndItsAncestors() throws Exception {
        Node e4 =
                add(
                        tree.getRoot(),
                        "e4",
                        Move.makeMove(Pos.fromString("e2"), Pos.fromString("e4")));
        add(e4, "e5", Move.makeMove(Pos.fromString("e7"), Pos.fromString("e5")));
        Node c5 = add(e4, "c5", Move.makeMove(Pos.fromString("c7"), Pos.fromString("c5")));
        add(c5, "Nf3", Move.makeMove(Pos.fromString("g1"), Pos.fromString("f3")));
        assertEquals("position fen " + START, tree.positionCommand(tree.getRoot()));
        assertEquals("position fen " + START + " moves e2e4 c7c5", tree.positionCommand(c5));
    }

    @Test
    public void enginePositionDistinguishesChess960CastlingFromAnOrdinaryKingMove()
            throws Exception {
        String fen = "4k3/8/8/8/8/8/8/5K1R w K - 0 1";
        tree = new GameTree(fen, 0, BoardConstants.WHITE);
        int from = Pos.fromString("f1"), to = Pos.fromString("g1");
        Node ordinary = add(tree.getRoot(), "Kg1", Move.makeMove(from, to));
        Node castle = add(tree.getRoot(), "O-O", Move.makeMoveOO(from, to));

        assertEquals("position fen " + fen + " moves f1g1", tree.positionCommand(ordinary));
        assertEquals("position fen " + fen + " moves f1h1", tree.positionCommand(castle));
        assertFalse(tree.positionCommand(ordinary).equals(tree.positionCommand(castle)));
    }

    @Test
    public void enginePositionDistinguishesBlackQueensideCastlingFromAnOrdinaryKingMove()
            throws Exception {
        String fen = "r2k4/8/8/8/8/8/8/4K3 b q - 0 1";
        tree = new GameTree(fen, 0, BoardConstants.BLACK);
        int from = Pos.fromString("d8"), to = Pos.fromString("c8");
        Node ordinary = add(tree.getRoot(), "Kc8", Move.makeMove(from, to));
        Node castle = add(tree.getRoot(), "O-O-O", Move.makeMoveOOO(from, to));

        assertEquals("position fen " + fen + " moves d8c8", tree.positionCommand(ordinary));
        assertEquals("position fen " + fen + " moves d8a8", tree.positionCommand(castle));
    }

    @Test
    public void enginePositionUsesExplicitVariantForAnOrthodoxChess960StartingPosition()
            throws Exception {
        String fen = "4k3/8/8/8/8/8/8/4K2R w K - 0 1";
        int castle = Move.makeMoveOO(Pos.fromString("e1"), Pos.fromString("g1"));
        tree = new GameTree(fen, 0, BoardConstants.WHITE, true);
        Node chess960 = add(tree.getRoot(), "O-O", castle);
        assertEquals("position fen " + fen + " moves e1h1", tree.positionCommand(chess960));

        tree = new GameTree(fen, 0, BoardConstants.WHITE, false);
        Node standard = add(tree.getRoot(), "O-O", castle);
        assertEquals("position fen " + fen + " moves e1g1", tree.positionCommand(standard));
    }

    @Test
    public void enginePositionUsesTheExplicitCastlingRookFile() throws Exception {
        String fen = "8/4k3/8/8/8/8/8/4KR1R w F - 0 1";
        tree = new GameTree(fen, 0, BoardConstants.WHITE);
        Node castle =
                add(
                        tree.getRoot(),
                        "O-O",
                        Move.makeMoveOO(Pos.fromString("e1"), Pos.fromString("g1")));
        assertEquals("position fen " + fen + " moves e1f1", tree.positionCommand(castle));
    }

    @Test
    public void enginePositionRetainsRepeatedMovesInCacheIdentity() throws Exception {
        Node node = tree.getRoot();
        String[] moves = {"g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8"};
        for (String move : moves) {
            node =
                    add(
                            node,
                            "",
                            Move.makeMove(
                                    Pos.fromString(move.substring(0, 2)),
                                    Pos.fromString(move.substring(2))));
        }
        assertEquals(
                "position fen " + START + " moves " + String.join(" ", moves),
                tree.positionCommand(node));
        assertFalse(tree.positionCommand(node).equals(tree.positionCommand(tree.getRoot())));
    }

    @Test
    public void enginePositionDistinguishesDuckPlacements() throws Exception {
        int e4 = Move.makeMove(Pos.fromString("e2"), Pos.fromString("e4"));
        Node d5 =
                tree.append(
                        tree.getRoot(),
                        new PGNEntry("e4", "", e4, Pos.fromString("d5")),
                        0,
                        0,
                        false);
        Node a5 =
                tree.append(
                        tree.getRoot(),
                        new PGNEntry("e4", "", e4, Pos.fromString("a5")),
                        0,
                        0,
                        false);
        assertEquals("position fen " + START + " moves e2e4@d5", tree.positionCommand(d5));
        assertEquals("position fen " + START + " moves e2e4@a5", tree.positionCommand(a5));
    }

    @Test
    public void enginePositionPreservesCustomStartAndUnderpromotion() throws Exception {
        String fen = "7k/P7/8/8/8/8/8/7K w - - 0 1";
        tree = new GameTree(fen, 0, BoardConstants.WHITE);
        Node promoted =
                add(
                        tree.getRoot(),
                        "a8=N",
                        Move.makeMovePromotion(
                                Pos.fromString("a7"),
                                Pos.fromString("a8"),
                                BoardConstants.KNIGHT,
                                false));
        assertEquals("position fen " + fen + " moves a7a8n", tree.positionCommand(promoted));
    }
}
