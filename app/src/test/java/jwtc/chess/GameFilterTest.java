package jwtc.chess;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Date;

import org.junit.Test;

public class GameFilterTest {
    @Test
    public void noConstraintsMeansNoWhereClause() {
        GameFilter filter = new GameFilter();
        assertTrue(filter.isEmpty());
        assertNull(filter.selection());
        assertNull(filter.selectionArgs());
    }

    @Test
    public void blankTextIsNoConstraint() {
        GameFilter filter = new GameFilter().white("  ").black(null).event("").result(" ");
        assertTrue(filter.isEmpty());
    }

    @Test
    public void namesAndEventMatchAnywhereInTheText() {
        GameFilter filter = new GameFilter().white(" Kasparov ").black("Deep").event("Wijk");
        assertEquals("white LIKE ? AND black LIKE ? AND event LIKE ?", filter.selection());
        assertArrayEquals(new String[]{"%Kasparov%", "%Deep%", "%Wijk%"}, filter.selectionArgs());
        assertFalse(filter.isEmpty());
    }

    @Test
    public void resultMatchesExactly() {
        GameFilter filter = new GameFilter().result("1/2-1/2");
        assertEquals("result = ?", filter.selection());
        assertArrayEquals(new String[]{"1/2-1/2"}, filter.selectionArgs());
    }

    @Test
    public void dateBoundsAreInclusiveAndInMilliseconds() {
        GameFilter filter = new GameFilter().after(new Date(1000)).before(new Date(2000));
        assertEquals("date >= ? AND date <= ?", filter.selection());
        assertArrayEquals(new String[]{"1000", "2000"}, filter.selectionArgs());
    }

    @Test
    public void argumentsFollowThePlaceholders() {
        GameFilter filter = new GameFilter().result("0-1").before(new Date(5)).white("A").after(new Date(3));
        assertEquals("white LIKE ? AND date >= ? AND date <= ? AND result = ?", filter.selection());
        assertArrayEquals(new String[]{"%A%", "3", "5", "0-1"}, filter.selectionArgs());
    }

    @Test
    public void aFilterCanBeCleared() {
        GameFilter filter = new GameFilter().white("A").after(new Date(3));
        filter.white(null).after(null);
        assertTrue(filter.isEmpty());
    }

    @Test
    public void defaultOrderIsNewestFirst() {
        assertEquals("date DESC", new GameFilter().orderBy());
    }

    @Test
    public void orderByAcceptsTheSortableColumns() {
        assertEquals("white ASC", new GameFilter().orderBy("white", "ASC").orderBy());
        assertEquals("event DESC", new GameFilter().orderBy(" event ", "DESC").orderBy());
        assertEquals("result ASC", new GameFilter().orderBy("result", "asc").orderBy());
    }

    @Test
    public void orderByRejectsAnythingElse() {
        assertEquals("date DESC", new GameFilter().orderBy("pgn; DROP TABLE games", "ASC; --").orderBy());
        assertEquals("date DESC", new GameFilter().orderBy("rating", "sideways").orderBy());
        assertEquals("date DESC", new GameFilter().orderBy(null, null).orderBy());
    }
}
