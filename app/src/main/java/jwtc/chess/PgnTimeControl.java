package jwtc.chess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A read-only interpretation of a PGN TimeControl tag; the tag remains the export source. */
public final class PgnTimeControl {
    public enum Type {
        UNKNOWN,
        UNTIMED,
        TIMED
    }

    public static final class Period {
        public enum Kind {
            MOVES,
            SUDDEN_DEATH,
            INCREMENTAL,
            HOURGLASS
        }

        public final Kind kind;

        /** Number of moves in this period, or zero for a terminal period. */
        public final long moves;

        /** Allocated time, or total sandclock time for HOURGLASS. */
        public final long baseMillis;

        public final long incrementMillis;

        private Period(Kind kind, long moves, long baseMillis, long incrementMillis) {
            this.kind = kind;
            this.moves = moves;
            this.baseMillis = baseMillis;
            this.incrementMillis = incrementMillis;
        }
    }

    private static final Pattern PERIOD = Pattern.compile("([0-9]+)(?:([/+])([0-9]+))?");

    public final Type type;

    /** Ordered control periods; empty for unknown and untimed games. */
    public final List<Period> periods;

    private PgnTimeControl(Type type, List<Period> periods) {
        this.type = type;
        this.periods = Collections.unmodifiableList(periods);
    }

    /**
     * Reads PGN's unknown, untimed, move-count, sudden-death, increment and hourglass forms.
     * Missing, unsupported, malformed or overflowing values return null, leaving the raw tag
     * intact.
     */
    public static PgnTimeControl parse(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.equals("?") || text.equals("-")) {
            return new PgnTimeControl(
                    text.equals("?") ? Type.UNKNOWN : Type.UNTIMED, Collections.emptyList());
        }
        String[] fields = text.split(":", -1);
        List<Period> periods = new ArrayList<>();
        try {
            for (int i = 0; i < fields.length; i++) {
                String field = fields[i];
                boolean last = i == fields.length - 1;
                if (field.startsWith("*") && field.substring(1).matches("[0-9]+")) {
                    long millis = millis(field.substring(1));
                    if (!last || millis == 0) {
                        return null;
                    }
                    periods.add(new Period(Period.Kind.HOURGLASS, 0, millis, 0));
                    continue;
                }
                Matcher matcher = PERIOD.matcher(field);
                if (!matcher.matches()) {
                    return null;
                }
                String separator = matcher.group(2);
                if ("/".equals(separator)) {
                    long moves = Long.parseLong(matcher.group(1));
                    long base = millis(matcher.group(3));
                    if (moves == 0 || base == 0) {
                        return null;
                    }
                    periods.add(new Period(Period.Kind.MOVES, moves, base, 0));
                } else {
                    if (!last) {
                        return null;
                    }
                    long base = millis(matcher.group(1));
                    periods.add(
                            new Period(
                                    separator == null
                                            ? Period.Kind.SUDDEN_DEATH
                                            : Period.Kind.INCREMENTAL,
                                    0,
                                    base,
                                    separator == null ? 0 : millis(matcher.group(3))));
                }
            }
        } catch (NumberFormatException | ArithmeticException ignored) {
            return null;
        }
        return new PgnTimeControl(Type.TIMED, periods);
    }

    private static long millis(String seconds) {
        return Math.multiplyExact(Long.parseLong(seconds), 1000L);
    }
}
