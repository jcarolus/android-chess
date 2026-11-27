package jwtc.chess;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The readable text of a PGN comment, kept apart from its embedded {@code [%...]} commands such as
 * evaluations, arrows and clock times.
 */
public final class PgnComment {
    private static final Pattern EVALUATION =
            Pattern.compile("\\[%eval\\s+(#?[+-]?\\d+(?:\\.\\d+)?)(?:,\\d+)?\\s*\\]");
    private static final Pattern COMMAND = Pattern.compile("\\[%[^\\]]*\\]");
    private static final Pattern CLOCK =
            Pattern.compile("\\[%clk\\s+([0-9]+):([0-5][0-9]):([0-5][0-9])(?:\\.([0-9]+))?\\s*\\]");
    private static final Pattern DIAGRAM =
            Pattern.compile("(?i)</?pgndiagram\\s*/?>|\\[pgndiagram\\]");
    private static final Pattern SPACES = Pattern.compile("[\\h\\x0B\\f]+");
    private static final Pattern LINE_BREAKS = Pattern.compile("( ?\\n ?)+");

    /** The comment without its embedded commands, cleaned as described in {@link #clean}. */
    public static String text(String comment) {
        return comment == null ? "" : clean(COMMAND.matcher(comment).replaceAll(""));
    }

    /**
     * Removes {@code <pgndiagram>} and {@code [pgndiagram]} markers and collapses runs of spaces
     * into one space and runs of line breaks into one line break.
     */
    static String clean(String text) {
        String cleaned = DIAGRAM.matcher(text).replaceAll("");
        cleaned = cleaned.replace("\r\n", "\n").replace('\r', '\n');
        cleaned = SPACES.matcher(cleaned).replaceAll(" ");
        return LINE_BREAKS.matcher(cleaned).replaceAll("\n").trim();
    }

    /** The {@code [%eval]} score, such as "0.25" or "#-3", or an empty string. */
    public static String evaluation(String comment) {
        if (comment == null) {
            return "";
        }
        Matcher matcher = EVALUATION.matcher(comment);
        return matcher.find() ? matcher.group(1) : "";
    }

    /**
     * Remaining time after the move, from the first valid {@code [%clk]} command, or null.
     * Fractions smaller than a millisecond are truncated; the original command is unchanged.
     */
    public static Long clockMillis(String comment) {
        if (comment == null) {
            return null;
        }
        Matcher matcher = CLOCK.matcher(comment);
        while (matcher.find()) {
            try {
                long hours = Math.multiplyExact(Long.parseLong(matcher.group(1)), 3600000L);
                long minutes = Long.parseLong(matcher.group(2)) * 60000L;
                long seconds = Long.parseLong(matcher.group(3)) * 1000L;
                String fraction = matcher.group(4);
                long millis =
                        fraction == null ? 0 : Long.parseLong((fraction + "000").substring(0, 3));
                return Math.addExact(hours, minutes + seconds + millis);
            } catch (NumberFormatException | ArithmeticException ignored) {
                // An unrepresentable clock does not hide a later valid command.
            }
        }
        return null;
    }

    /** Replaces the readable text, cleaned, and keeps the embedded commands in front of it. */
    public static String withText(String comment, String text) {
        StringBuilder out = new StringBuilder();
        if (comment != null) {
            Matcher matcher = COMMAND.matcher(comment);
            while (matcher.find()) {
                if (out.length() > 0) {
                    out.append(' ');
                }
                out.append(matcher.group());
            }
        }
        String trimmed = text == null ? "" : clean(text);
        if (!trimmed.isEmpty()) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(trimmed);
        }
        return out.toString();
    }

    private PgnComment() {}
}
