package jwtc.android.chess.analyze;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import jwtc.android.chess.R;
import jwtc.android.chess.engine.EngineEvaluation;
import jwtc.android.chess.helpers.ChessAlertDialogBuilder;
import jwtc.android.chess.services.GameApi;
import jwtc.chess.GameTree.Node;
import jwtc.chess.Pos;

/** Displays report generation and its finished scores without moving the board during analysis. */
final class AnalysisReportDialog {
    private final Context context;
    private final Analysis analysis;
    private final GameApi game;
    private final Consumer<Node> navigate;
    private final Function<Node, String> describeMove;
    private final AlertDialog dialog;
    private final View content;
    private boolean finished;

    AnalysisReportDialog(
            Context context,
            Analysis analysis,
            GameApi game,
            Consumer<Node> navigate,
            Function<Node, String> describeMove) {
        this.context = context;
        this.analysis = analysis;
        this.game = game;
        this.navigate = navigate;
        this.describeMove = describeMove;
        content = LayoutInflater.from(context).inflate(R.layout.analysis_report, null);
        dialog =
                new ChessAlertDialogBuilder(context)
                        .setTitle(R.string.analysis_report_title)
                        .setView(content)
                        .setNegativeButton(R.string.button_cancel, null)
                        .create();
        dialog.setOnDismissListener(ignored -> analysis.cancelReport());
    }

    boolean show() {
        dialog.show();
        if (!analysis.startReport()) {
            dialog.dismiss();
            return false;
        }
        return true;
    }

    void dismiss() {
        dialog.dismiss();
    }

    void update() {
        if (!dialog.isShowing() || finished) {
            return;
        }
        TextView status = content.findViewById(R.id.ReportProgressText);
        LinearProgressIndicator progress = content.findViewById(R.id.ReportProgress);
        if (analysis.isReporting()) {
            progress.setMax(analysis.reportNodes().size());
            progress.setProgress(analysis.reportCompleted());
            status.setText(
                    context.getString(
                            R.string.analysis_report_progress,
                            analysis.reportCompleted(),
                            analysis.reportNodes().size()));
            return;
        }
        finished = true;
        AnalysisReport report = analysis.report();
        List<Node> nodes = new ArrayList<>(analysis.reportNodes());
        progress.setVisibility(View.GONE);
        status.setVisibility(report.complete ? View.GONE : View.VISIBLE);
        status.setText(R.string.analysis_report_partial);
        content.findViewById(R.id.ReportContent).setVisibility(View.VISIBLE);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setText(R.string.button_close);
        TextView accuracy = content.findViewById(R.id.ReportAccuracy);
        accuracy.setText(
                context.getString(
                        R.string.analysis_report_accuracy,
                        accuracy(report.whiteAccuracy),
                        accuracy(report.blackAccuracy)));
        ReportGraphView graph = content.findViewById(R.id.ReportGraph);
        TextView selected = content.findViewById(R.id.ReportSelectedMove);
        MaterialButton goTo = content.findViewById(R.id.ReportGoToMove);
        Runnable updateSelected =
                () -> {
                    int index = graph.getProgress();
                    String description =
                            context.getString(
                                    R.string.analysis_report_position,
                                    moveLabel(nodes.get(index)),
                                    evaluation(
                                            report.positions.get(index).evaluation,
                                            report.positions.get(index).whiteToMove));
                    selected.setText(description);
                    String spoken =
                            context.getString(
                                    R.string.analysis_report_position,
                                    describeMove.apply(nodes.get(index)),
                                    evaluation(
                                            report.positions.get(index).evaluation,
                                            report.positions.get(index).whiteToMove));
                    ViewCompat.setStateDescription(graph, spoken);
                    goTo.setContentDescription(
                            context.getString(R.string.analysis_report_go_to_move) + ": " + spoken);
                };
        graph.setReport(report, Math.max(0, nodes.indexOf(game.getCurrentNode())));
        updateSelected.run();
        graph.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                        updateSelected.run();
                        graph.invalidate();
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        jump(nodes.get(seekBar.getProgress()));
                    }
                });
        goTo.setOnClickListener(view -> jump(nodes.get(graph.getProgress())));
        LinearLayout moments = content.findViewById(R.id.ReportMoments);
        for (int index : report.keyMoments) {
            Node node = nodes.get(index);
            AnalysisReport.Position before = report.positions.get(index - 1);
            AnalysisReport.Position after = report.positions.get(index);
            MaterialButton button = new MaterialButton(context);
            button.setAllCaps(false);
            button.setText(
                    context.getString(
                            R.string.analysis_report_moment,
                            moveLabel(node),
                            context.getString(
                                    before.whiteToMove
                                            ? R.string.piece_white
                                            : R.string.piece_black),
                            context.getString(AnalyzeActivity.feedbackText(after.classification)),
                            evaluation(before.evaluation, before.whiteToMove),
                            evaluation(after.evaluation, after.whiteToMove)));
            button.setContentDescription(
                    context.getString(
                            R.string.analysis_report_moment,
                            describeMove.apply(node),
                            context.getString(
                                    before.whiteToMove
                                            ? R.string.piece_white
                                            : R.string.piece_black),
                            context.getString(AnalyzeActivity.feedbackText(after.classification)),
                            evaluation(before.evaluation, before.whiteToMove),
                            evaluation(after.evaluation, after.whiteToMove)));
            button.setOnClickListener(view -> jump(node));
            moments.addView(
                    button,
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        content.findViewById(R.id.ReportEmpty)
                .setVisibility(
                        report.keyMoments.isEmpty() && report.complete ? View.VISIBLE : View.GONE);
    }

    private void jump(Node node) {
        dialog.dismiss();
        navigate.accept(node);
    }

    private String moveLabel(Node node) {
        if (node.getParent() == null) {
            return context.getString(R.string.analysis_report_start);
        }
        String move = node.getEntry().sMove;
        if (node.getEntry().duckMove != -1) {
            move += "@" + Pos.toString(node.getEntry().duckMove);
        }
        return game.getMoveNumber(node) + " " + move;
    }

    private String accuracy(double value) {
        if (!Double.isFinite(value)) {
            return context.getString(R.string.analysis_report_unknown);
        }
        NumberFormat format = NumberFormat.getPercentInstance();
        format.setMaximumFractionDigits(1);
        return format.format(value / 100);
    }

    private String evaluation(EngineEvaluation evaluation, boolean whiteToMove) {
        if (evaluation == null) {
            return context.getString(R.string.analysis_report_unknown);
        }
        if (evaluation.mate) {
            if (evaluation.value == 0) {
                return (whiteToMove ? "−" : "+") + "#0";
            }
            return (evaluation.value > 0 ? "+" : "−") + "#" + Math.abs((long) evaluation.value);
        }
        NumberFormat format = NumberFormat.getNumberInstance();
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return (evaluation.value > 0 ? "+" : "") + format.format(evaluation.value / 100.0);
    }
}
