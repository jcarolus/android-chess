package jwtc.android.chess.analyze;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import jwtc.android.chess.engine.EngineEvaluation;
import org.junit.Test;

public class AnalysisReportTest {
    private static AnalysisReport.Position position(
            int cp, boolean white, MoveClassifier.Feedback feedback) {
        return new AnalysisReport.Position(new EngineEvaluation(false, cp, 10), white, feedback);
    }

    @Test
    public void accuracyMatchesChessMateAcrossVolatilityWindowSizes() {
        int[] lengths = {4, 40, 80, 110};
        double[] white = {100, 59.09495064733146, 71.01185495965049, 68.21276823950642};
        double[] black = {
            76.18878832590522, 76.86485168469099, 78.59071997437061, 78.48658757372826
        };
        for (int sample = 0; sample < lengths.length; sample++) {
            List<AnalysisReport.Position> positions = new ArrayList<>();
            for (int i = 0; i <= lengths[sample]; i++) {
                positions.add(position((i * 73) % 601 - 300, i % 2 == 0, null));
            }
            AnalysisReport report = new AnalysisReport(positions);
            assertEquals(white[sample], report.whiteAccuracy, 0.000001);
            assertEquals(black[sample], report.blackAccuracy, 0.000001);
        }
    }

    @Test
    public void equalPositionsArePerfectForBothPlayers() {
        AnalysisReport report =
                new AnalysisReport(
                        List.of(
                                position(0, true, null),
                                position(0, false, MoveClassifier.Feedback.BEST),
                                position(0, true, MoveClassifier.Feedback.BEST)));
        assertTrue(report.complete);
        assertEquals(100, report.whiteAccuracy, 0.00001);
        assertEquals(100, report.blackAccuracy, 0.00001);
        assertTrue(report.keyMoments.isEmpty());
    }

    @Test
    public void startsWithBlackAndAttributesLossToTheMover() {
        AnalysisReport report =
                new AnalysisReport(
                        List.of(
                                position(0, false, null),
                                position(500, true, MoveClassifier.Feedback.BLUNDER),
                                position(500, false, MoveClassifier.Feedback.BEST)));
        assertEquals(100, report.whiteAccuracy, 0.00001);
        assertTrue(report.blackAccuracy < 30);
        assertTrue(report.loss(1) > 30);
        assertEquals(List.of(1), report.keyMoments);
    }

    @Test
    public void checkmateZeroUsesTheActualWinnerAndScoresAreClamped() {
        assertEquals(100, AnalysisReport.winPercent(new EngineEvaluation(true, 0, 0), false), 0);
        assertEquals(0, AnalysisReport.winPercent(new EngineEvaluation(true, 0, 0), true), 0);
        assertEquals(100, AnalysisReport.winPercent(new EngineEvaluation(true, 3, 0), true), 0);
        assertEquals(0, AnalysisReport.winPercent(new EngineEvaluation(true, -3, 0), false), 0);
        assertEquals(
                AnalysisReport.winPercent(new EngineEvaluation(false, 1000, 0), true),
                AnalysisReport.winPercent(new EngineEvaluation(false, 20000, 0), true),
                0);
        assertEquals(100, AnalysisReport.moveAccuracy(-50), 0);
        assertEquals(0, AnalysisReport.moveAccuracy(100), 0);
    }

    @Test
    public void missingEvaluationsDoNotInventAccuracyOrKeyMoments() {
        AnalysisReport report =
                new AnalysisReport(
                        List.of(
                                position(0, true, null),
                                new AnalysisReport.Position(
                                        null, false, MoveClassifier.Feedback.BLUNDER)));
        assertFalse(report.complete);
        assertTrue(Double.isNaN(report.whiteAccuracy));
        assertTrue(Double.isNaN(report.blackAccuracy));
        assertTrue(Double.isNaN(report.positions.get(1).whiteWinPercent));
        assertTrue(report.keyMoments.isEmpty());
    }

    @Test
    public void singleMoveDoesNotInventAnAccuracyForTheOtherPlayer() {
        AnalysisReport report =
                new AnalysisReport(
                        List.of(
                                position(0, true, null),
                                position(0, false, MoveClassifier.Feedback.BEST)));
        assertEquals(100, report.whiteAccuracy, 0.00001);
        assertTrue(Double.isNaN(report.blackAccuracy));
    }

    @Test
    public void selectsEightLargestLossesAndKeepsTiesInMoveOrder() {
        List<AnalysisReport.Position> positions = new ArrayList<>();
        positions.add(position(0, true, null));
        for (int i = 1; i <= 20; i++) {
            positions.add(
                    position(
                            i % 2 == 1 ? -200 : 0,
                            i % 2 == 0,
                            i == 1
                                    ? MoveClassifier.Feedback.INACCURACY
                                    : MoveClassifier.Feedback.MISTAKE));
        }
        AnalysisReport report = new AnalysisReport(positions);
        assertEquals(List.of(2, 3, 4, 5, 6, 7, 8, 9), report.keyMoments);
    }

    @Test
    public void harmonicMeanPenalizesOneBlunderInAnOtherwiseAccurateGame() {
        List<AnalysisReport.Position> positions = new ArrayList<>();
        positions.add(position(0, true, null));
        for (int i = 1; i <= 60; i++) {
            positions.add(
                    position(
                            i >= 31 ? -500 : 0,
                            i % 2 == 0,
                            i == 31
                                    ? MoveClassifier.Feedback.BLUNDER
                                    : MoveClassifier.Feedback.BEST));
        }
        AnalysisReport report = new AnalysisReport(positions);
        assertTrue(report.whiteAccuracy < 90);
        assertEquals(100, report.blackAccuracy, 0.00001);
        assertEquals(List.of(31), report.keyMoments);
    }
}
