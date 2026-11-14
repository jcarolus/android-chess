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

    /** The comment without its embedded commands. */
    public static String text(String comment) {
        return comment == null ? "" : COMMAND.matcher(comment).replaceAll("").trim();
    }

    /** The {@code [%eval]} score, such as "0.25" or "#-3", or an empty string. */
    public static String evaluation(String comment) {
        if (comment == null) {
            return "";
        }
        Matcher matcher = EVALUATION.matcher(comment);
        return matcher.find() ? matcher.group(1) : "";
    }

    /** Replaces the readable text and keeps the embedded commands in front of it. */
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
        String trimmed = text == null ? "" : text.trim();
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
