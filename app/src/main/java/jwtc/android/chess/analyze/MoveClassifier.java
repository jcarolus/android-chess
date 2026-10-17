package jwtc.android.chess.analyze;

import jwtc.android.chess.engine.EngineEvaluation;

/** Move quality using completed scores normalized to White's perspective. */
public final class MoveClassifier {
    public enum Feedback { BEST, EXCELLENT, OKAY, INACCURACY, MISTAKE, BLUNDER, FORCED }

    private MoveClassifier() {}

    public static Feedback classify(EngineEvaluation before, EngineEvaluation after,
            boolean whiteMoved, boolean preferredMove, int legalMoveCount,
            boolean checkmate, boolean draw) {
        if (legalMoveCount == 1) return Feedback.FORCED;
        if (checkmate) return Feedback.BEST;
        if (before == null || after == null) return null;
        // An actual draw takes precedence over an engine prediction of a winning best move.
        if (preferredMove && !draw) return Feedback.BEST;
        int sign = whiteMoved ? 1 : -1;
        double previous = (double) before.value * sign;
        double current = (double) after.value * sign;
        if (draw) return fromLoss(expectedScore(before.mate, previous) - 0.5);
        if (before.mate && after.mate) {
            if (previous > 0 && current < 0) return current < -3 ? Feedback.MISTAKE : Feedback.BLUNDER;
            if (current < 0 || previous < 0) return Feedback.BEST;
            double delay = current - previous;
            if (delay < 0) return Feedback.BEST;
            if (delay < 2) return Feedback.EXCELLENT;
            if (delay < 7) return Feedback.OKAY;
            return Feedback.INACCURACY;
        }
        if (before.mate) {
            // Escaping a forced loss is not a mistake, even if still behind in material.
            if (previous < 0) return Feedback.BEST;
            if (current >= 800) return Feedback.EXCELLENT;
            if (current >= 400) return Feedback.OKAY;
            if (current >= 200) return Feedback.INACCURACY;
            if (current >= 0) return Feedback.MISTAKE;
            return Feedback.BLUNDER;
        }
        if (after.mate) {
            if (current > 0) return Feedback.BEST;
            if (current >= -2) return Feedback.BLUNDER;
            if (current >= -5) return Feedback.MISTAKE;
            return Feedback.INACCURACY;
        }
        return fromLoss(expectedScore(false, previous) - expectedScore(false, current));
    }

    private static double expectedScore(boolean mate, double value) {
        return mate ? (value > 0 ? 1 : 0) : 1 / (1 + Math.exp(-0.0035 * value));
    }

    static Feedback fromLoss(double loss) {
        if (loss < 0.01) return Feedback.BEST;
        if (loss < 0.045) return Feedback.EXCELLENT;
        if (loss < 0.08) return Feedback.OKAY;
        if (loss < 0.12) return Feedback.INACCURACY;
        if (loss < 0.22) return Feedback.MISTAKE;
        return Feedback.BLUNDER;
    }
}
