package jwtc.android.chess.analyze;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import jwtc.android.chess.engine.EngineEvaluation;

/** Main-line scores, accuracy and key moments; all evaluations use White's perspective. */
final class AnalysisReport {
    static final class Position {
        final EngineEvaluation evaluation;
        final boolean whiteToMove;
        final MoveClassifier.Feedback classification;
        final double whiteWinPercent;

        Position(
                EngineEvaluation evaluation,
                boolean whiteToMove,
                MoveClassifier.Feedback classification) {
            this.evaluation = evaluation;
            this.whiteToMove = whiteToMove;
            this.classification = classification;
            whiteWinPercent = winPercent(evaluation, whiteToMove);
        }
    }

    final List<Position> positions;
    final List<Integer> keyMoments;
    final double whiteAccuracy;
    final double blackAccuracy;
    final boolean complete;

    AnalysisReport(List<Position> positions) {
        this.positions = Collections.unmodifiableList(new ArrayList<>(positions));
        boolean allScored = true;
        for (Position position : positions) {
            allScored &= position.evaluation != null;
        }
        complete = allScored;
        whiteAccuracy = allScored ? accuracy(true) : Double.NaN;
        blackAccuracy = allScored ? accuracy(false) : Double.NaN;
        List<Integer> moments = new ArrayList<>();
        for (int i = 1; i < positions.size(); i++) {
            MoveClassifier.Feedback classification = positions.get(i).classification;
            if ((classification == MoveClassifier.Feedback.MISTAKE
                            || classification == MoveClassifier.Feedback.BLUNDER)
                    && Double.isFinite(loss(i))) {
                moments.add(i);
            }
        }
        moments.sort(
                Comparator.<Integer>comparingDouble(this::loss)
                        .reversed()
                        .thenComparingInt(i -> i));
        keyMoments =
                Collections.unmodifiableList(
                        new ArrayList<>(moments.subList(0, Math.min(8, moments.size()))));
    }

    static double winPercent(EngineEvaluation score, boolean whiteToMove) {
        if (score == null) {
            return Double.NaN;
        }
        if (score.mate) {
            return score.value == 0 ? (whiteToMove ? 0 : 100) : score.value > 0 ? 100 : 0;
        }
        double cp = Math.max(-1000, Math.min(1000, score.value));
        return 100 / (1 + Math.exp(-0.00368208 * cp));
    }

    double loss(int index) {
        Position before = positions.get(index - 1);
        Position after = positions.get(index);
        double change = before.whiteWinPercent - after.whiteWinPercent;
        return before.whiteToMove ? change : -change;
    }

    static double moveAccuracy(double loss) {
        return Math.max(0, Math.min(100, 103.1668 * Math.exp(-0.04354 * loss) - 3.1669 + 1));
    }

    /** ChessMate's accuracy model combines a volatility-weighted mean with a harmonic mean. */
    private double accuracy(boolean white) {
        int moves = positions.size() - 1;
        int window = Math.max(2, Math.min(8, moves / 10));
        double weighted = 0;
        double weights = 0;
        double reciprocals = 0;
        int count = 0;
        for (int i = 1; i < positions.size(); i++) {
            if (positions.get(i - 1).whiteToMove != white) {
                continue;
            }
            // The first window is repeated until a complete moving window is available.
            int start = Math.max(0, i - window + 1);
            int end = Math.min(positions.size(), start + window);
            double mean = 0;
            for (int j = start; j < end; j++) {
                mean += positions.get(j).whiteWinPercent;
            }
            mean /= end - start;
            double variance = 0;
            for (int j = start; j < end; j++) {
                double deviation = positions.get(j).whiteWinPercent - mean;
                variance += deviation * deviation;
            }
            double weight = Math.max(0.5, Math.min(12, Math.sqrt(variance / (end - start))));
            double accuracy = moveAccuracy(loss(i));
            weighted += accuracy * weight;
            weights += weight;
            reciprocals += 1 / Math.max(1, accuracy);
            count++;
        }
        return count == 0 ? Double.NaN : (weighted / weights + count / reciprocals) / 2;
    }
}
