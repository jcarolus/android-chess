package jwtc.android.chess.engine;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** An exact UCI score. Values are from the side to move until converted to White's perspective. */
public final class EngineEvaluation {
    private static final Pattern SCORE = Pattern.compile("\\bscore\\s+(cp|mate)\\s+(-?\\d+)(?=\\s|$)");
    private static final Pattern DEPTH = Pattern.compile("\\bdepth\\s+(\\d+)(?=\\s|$)");
    private static final Pattern MULTIPV = Pattern.compile("\\bmultipv\\s+(\\d+)(?=\\s|$)");
    private static final Pattern BOUND = Pattern.compile("\\b(lowerbound|upperbound)\\b");

    public final boolean mate;
    public final int value;
    public final int depth;

    public EngineEvaluation(boolean mate, int value, int depth) {
        this.mate = mate;
        this.value = value;
        this.depth = depth;
    }

    /** Null means no usable exact principal-line score, never an equal position. */
    public static EngineEvaluation fromInfo(String line) {
        if (!line.startsWith("info ") || line.startsWith("info string ") || BOUND.matcher(line).find()) return null;
        try {
            Matcher pv = MULTIPV.matcher(line);
            if (pv.find() && Integer.parseInt(pv.group(1)) != 1) return null;
            Matcher score = SCORE.matcher(line);
            if (!score.find()) return null;
            Matcher depth = DEPTH.matcher(line);
            return new EngineEvaluation("mate".equals(score.group(1)), Integer.parseInt(score.group(2)),
                depth.find() ? Integer.parseInt(depth.group(1)) : 0);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public EngineEvaluation forWhite(boolean whiteToMove) {
        return new EngineEvaluation(mate, whiteToMove ? value : -value, depth);
    }
}
