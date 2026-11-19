package jwtc.chess;

import java.io.Serializable;
import java.util.Date;

/** Editable criteria, including values retained while their filters are switched off. */
public final class GameSelection implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final String EXTRA = "gameSelection";

    public String white = "", black = "", event = "", topic = "", result = "1-0";
    public boolean useWhite, useBlack, useEvent, useTopic, useAfter, useBefore, useResult;
    public Date after = PgnDate.parse(PgnDate.format(new Date()));
    public Date before = after;
    public String sortColumn = PGNColumns.DATE, sortDirection = "DESC";

    public boolean hasValidDates() {
        return (!useAfter || after != null)
                && (!useBefore || before != null)
                && (!useAfter || !useBefore || !after.after(before));
    }

    public GameFilter filter() {
        return new GameFilter()
                .white(useWhite ? white : null)
                .black(useBlack ? black : null)
                .event(useEvent ? event : null)
                .topic(useTopic ? topic : null)
                .after(useAfter ? after : null)
                .before(useBefore ? before : null)
                .result(useResult ? result : null)
                .orderBy(sortColumn, sortDirection);
    }

    /** Only applied criteria and sorting identify a study's resume position. */
    public String resumeKey() {
        GameFilter filter = filter();
        StringBuilder key = new StringBuilder("studySelection/");
        append(key, filter.selection());
        String[] args = filter.selectionArgs();
        if (args != null) {
            for (String arg : args) {
                append(key, arg);
            }
        }
        append(key, filter.orderBy());
        return key.toString();
    }

    private static void append(StringBuilder key, String value) {
        if (value == null) {
            key.append("-1:");
        } else {
            key.append(value.length()).append(':').append(value);
        }
    }
}
