package jwtc.chess;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Date;
import org.junit.Test;

public class GameSelectionTest {
    @Test
    public void disabledFiltersKeepValuesWithoutRestrictingGames() {
        GameSelection selection = new GameSelection();
        selection.white = "White";
        selection.black = "Black";
        selection.event = "Event";
        selection.topic = "Topic";
        selection.result = "0-1";
        assertTrue(selection.filter().isEmpty());
        assertEquals("date DESC", selection.filter().orderBy());
    }

    @Test
    public void enabledFiltersCombineWithTheChosenOrder() {
        GameSelection selection = new GameSelection();
        selection.white = " A ";
        selection.black = "B";
        selection.event = "Open";
        selection.topic = "Mate";
        selection.result = "*";
        selection.after = new Date(1000);
        selection.before = new Date(2000);
        selection.useWhite = selection.useBlack = selection.useEvent = selection.useTopic = true;
        selection.useAfter = selection.useBefore = selection.useResult = true;
        selection.sortColumn = "white";
        selection.sortDirection = "ASC";
        GameFilter filter = selection.filter();
        assertEquals("white LIKE ? AND black LIKE ? AND event LIKE ? AND topic LIKE ? AND date >= ? AND date <= ? AND result = ?", filter.selection());
        assertArrayEquals(new String[] {"%A%", "%B%", "%Open%", "%Mate%", "1000", "2000", "*"}, filter.selectionArgs());
        assertEquals("white ASC", filter.orderBy());
    }

    @Test
    public void dateValidationIgnoresDisabledBoundsAndAcceptsEqualDates() {
        GameSelection selection = new GameSelection();
        selection.after = new Date(2000);
        selection.before = new Date(1000);
        assertTrue(selection.hasValidDates());
        selection.useAfter = true;
        assertTrue(selection.hasValidDates());
        selection.useBefore = true;
        assertFalse(selection.hasValidDates());
        selection.before = new Date(2000);
        assertTrue(selection.hasValidDates());
        selection.after = null;
        assertFalse(selection.hasValidDates());
        selection.useAfter = false;
        assertTrue(selection.hasValidDates());
        selection.before = null;
        assertFalse(selection.hasValidDates());
    }

    @Test
    public void resumeIdentityUsesAppliedCriteriaAndSorting() {
        GameSelection selection = new GameSelection();
        String all = selection.resumeKey();
        selection.topic = "Mate";
        selection.after = new Date(1000);
        assertEquals(all, selection.resumeKey());
        selection.useTopic = true;
        String mate = selection.resumeKey();
        assertNotEquals(all, mate);
        selection.topic = " Mate ";
        assertEquals(mate, selection.resumeKey());
        selection.topic = "Endgame";
        assertNotEquals(mate, selection.resumeKey());
        selection.topic = "Mate";
        selection.sortDirection = "ASC";
        assertNotEquals(mate, selection.resumeKey());
        selection.sortDirection = "DESC";
        selection.useTopic = false;
        selection.event = "Mate";
        selection.useEvent = true;
        assertNotEquals(mate, selection.resumeKey());
    }

    @Test
    public void selectionSnapshotRetainsBothAppliedAndDisabledValues() throws Exception {
        GameSelection selection = new GameSelection();
        selection.white = "White";
        selection.topic = "Hidden topic";
        selection.useWhite = true;
        selection.useBefore = true;
        selection.before = new Date(2000);
        selection.sortColumn = "event";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(selection);
        }
        GameSelection restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (GameSelection) input.readObject();
        }
        assertEquals(selection.resumeKey(), restored.resumeKey());
        assertEquals("Hidden topic", restored.topic);
        assertFalse(restored.useTopic);
        assertEquals(new Date(2000), restored.before);
        assertTrue(restored.useWhite);
    }
}
