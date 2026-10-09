package jwtc.chess;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/** What the user narrows the Game Database list down to, and how it is ordered, as SQL for the Game Database table. */
public final class GameFilter {
    public static final List<String> SORT_COLUMNS = Arrays.asList(
        PGNColumns.DATE, PGNColumns.WHITE, PGNColumns.BLACK, PGNColumns.EVENT, PGNColumns.RESULT);

    private String white, black, event, result;
    private Date after, before;
    private String sortColumn = PGNColumns.DATE;
    private boolean descending = true;

    /** Text a name or event contains, in any case; blank means no constraint. */
    public GameFilter white(String text) {
        white = blankToNull(text);
        return this;
    }

    public GameFilter black(String text) {
        black = blankToNull(text);
        return this;
    }

    public GameFilter event(String text) {
        event = blankToNull(text);
        return this;
    }

    /** Exactly this result, such as 1-0; blank means no constraint. */
    public GameFilter result(String text) {
        result = blankToNull(text);
        return this;
    }

    /** Games on or after this moment; null means no constraint. */
    public GameFilter after(Date date) {
        after = date;
        return this;
    }

    /** Games on or before this moment; null means no constraint. */
    public GameFilter before(Date date) {
        before = date;
        return this;
    }

    /** Anything but a sortable column or ASC and DESC falls back to the default, newest first. */
    public GameFilter orderBy(String column, String direction) {
        sortColumn = column != null && SORT_COLUMNS.contains(column.trim()) ? column.trim() : PGNColumns.DATE;
        descending = direction == null || !direction.trim().equalsIgnoreCase("ASC");
        return this;
    }

    public boolean isEmpty() {
        return selection() == null;
    }

    /** The WHERE clause with ? placeholders, or null when nothing is constrained. */
    public String selection() {
        List<String> parts = new ArrayList<>();
        if (white != null) parts.add(PGNColumns.WHITE + " LIKE ?");
        if (black != null) parts.add(PGNColumns.BLACK + " LIKE ?");
        if (event != null) parts.add(PGNColumns.EVENT + " LIKE ?");
        if (after != null) parts.add(PGNColumns.DATE + " >= ?");
        if (before != null) parts.add(PGNColumns.DATE + " <= ?");
        if (result != null) parts.add(PGNColumns.RESULT + " = ?");
        if (parts.isEmpty()) return null;
        StringBuilder where = new StringBuilder(parts.get(0));
        for (int i = 1; i < parts.size(); i++) where.append(" AND ").append(parts.get(i));
        return where.toString();
    }

    /** The values for the placeholders of {@link #selection()}, in order, or null when there are none. */
    public String[] selectionArgs() {
        List<String> args = new ArrayList<>();
        if (white != null) args.add("%" + white + "%");
        if (black != null) args.add("%" + black + "%");
        if (event != null) args.add("%" + event + "%");
        if (after != null) args.add(String.valueOf(after.getTime()));
        if (before != null) args.add(String.valueOf(before.getTime()));
        if (result != null) args.add(result);
        return args.isEmpty() ? null : args.toArray(new String[0]);
    }

    public String orderBy() {
        return sortColumn + (descending ? " DESC" : " ASC");
    }

    private static String blankToNull(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
