package jwtc.chess;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import jwtc.chess.GameTree.Node;

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
    public void exportsMainLineWithMoveNumbers() {
        Node e4 = add(tree.getRoot(), "e4", 1);
        add(add(e4, "e5", 2), "Nf3", 3);
        assertEquals("1. e4 1... e5 2. Nf3", tree.exportMoves());
    }

    @Test
    public void exportsVariationsInParentheses() {
        Node e4 = add(tree.getRoot(), "e4", 1);
        add(tree.getRoot(), "d4", 4);
        Node e5 = add(e4, "e5", 2);
        add(e4, "c5", 5);
        add(e5, "Nf3", 3);
        assertEquals("1. e4 ( 1. d4 ) 1... e5 ( 1... c5 ) 2. Nf3", tree.exportMoves());
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
        assertEquals("{start} 1. e4 $1 $14 {the [best] move} ( {other} 1. d4 )", tree.exportMoves());
    }

    @Test
    public void exportsDuckPlacement() {
        tree.append(tree.getRoot(), new PGNEntry("e4", "", 1, 20), 0, 0, false);
        assertEquals("1. e4@" + Pos.toString(20), tree.exportMoves());
    }

    @Test
    public void moveNumbersFollowTheStartingPosition() {
        GameTree black = new GameTree("8/8/8/8/8/8/8/K6k b - - 0 7", 0, 1);
        Node first = black.append(black.getRoot(), new PGNEntry("Kg1", "", 1, -1), 0, 0, false);
        Node second = black.append(first, new PGNEntry("Ka2", "", 2, -1), 0, 0, false);
        assertEquals("7...", black.moveNumber(first));
        assertEquals("8.", black.moveNumber(second));
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
}
