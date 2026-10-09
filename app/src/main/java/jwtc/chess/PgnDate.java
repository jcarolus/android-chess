package jwtc.chess;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The PGN date format, yyyy.MM.dd, where the PGN standard allows "??" for an unknown month or day. */
public final class PgnDate {
    private static final Pattern FULL = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");
    private static final Pattern DAY_UNKNOWN = Pattern.compile("(\\d+)\\.(\\d+)\\.\\?\\?");
    private static final Pattern MONTH_UNKNOWN = Pattern.compile("(\\d+)\\.\\?\\?\\.\\?\\?");

    private PgnDate() {}

    /**
     * An unknown day counts as the first of the month, and an unknown month as January. Returns null when the text is
     * not a date, when the year is unknown, or when the date does not exist (such as 2023.13.45).
     */
    public static Date parse(String text) {
        if (text == null) return null;
        Matcher m = FULL.matcher(text);
        if (m.matches()) return build(m.group(1), m.group(2), m.group(3));
        m = DAY_UNKNOWN.matcher(text);
        if (m.matches()) return build(m.group(1), m.group(2), "1");
        m = MONTH_UNKNOWN.matcher(text);
        if (m.matches()) return build(m.group(1), "1", "1");
        return null;
    }

    /** yyyy.MM.dd with ASCII digits whatever the device locale; an empty string for null. */
    public static String format(Date date) {
        if (date == null) return "";
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return String.format(Locale.ROOT, "%04d.%02d.%02d", calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH));
    }

    private static Date build(String year, String month, String day) {
        try {
            Calendar calendar = Calendar.getInstance();
            calendar.clear();
            calendar.setLenient(false);
            calendar.set(Integer.parseInt(year), Integer.parseInt(month) - 1, Integer.parseInt(day));
            return calendar.getTime();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
