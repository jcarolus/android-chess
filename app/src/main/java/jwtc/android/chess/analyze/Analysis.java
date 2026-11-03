package jwtc.android.chess.analyze;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import jwtc.android.chess.analyze.AnalysisResults.Result;
import jwtc.android.chess.analyze.AnalysisResults.Saved;
import jwtc.android.chess.engine.EngineEvaluation;
import jwtc.android.chess.engine.EngineListener;
import jwtc.android.chess.engine.EngineSession;
import jwtc.android.chess.services.GameApi;
import jwtc.android.chess.services.GameApi.PositionMove;
import jwtc.android.chess.services.GameApi.PositionSnapshot;
import jwtc.chess.GameTree.Node;
import jwtc.chess.Move;
import jwtc.chess.PGNEntry;
import jwtc.chess.board.BoardConstants;

/** Owns screen-scoped Analysis work and completed results, keyed by full move history. */
final class Analysis implements EngineListener {
    interface Listener {
        void onChanged();

        void onEvaluation(Float value);
    }

    private final Context context;
    private final GameApi gameApi;
    private final Listener listener;
    private final AnalysisResults results = new AnalysisResults();
    private EngineSession engineSession;
    private boolean analysisActive;
    private String analysisFen;
    private int analysisTurn;
    private int analysisGeneration;
    private boolean analysisSwitchPending;
    private String configuredEngine;
    private int configuredMillis;
    private static final long ANALYSIS_DEBOUNCE_MILLIS = 200;
    private final Handler analysisHandler = new Handler(Looper.getMainLooper());
    private boolean analysisDebouncing;
    private final Runnable resumeAnalysis =
            () -> {
                analysisDebouncing = false;
                startNextAnalysis();
            };
    private final Map<Node, PositionSnapshot> positions = new WeakHashMap<>();
    private Node displayedNode;
    private PositionSnapshot requestedPosition;

    Analysis(Context context, GameApi gameApi, Listener listener) {
        this.context = context;
        this.gameApi = gameApi;
        this.listener = listener;
    }

    void resume(String engine, int millis, Saved saved) {
        results.configure(engine, millis, saved);
        configuredEngine = engine;
        configuredMillis = millis;
        engineSession = EngineSession.forAnalysis(context, gameApi, this, engine);
        analysisFen = null;
        requestedPosition = null;
        analysisSwitchPending = false;
        displayedNode = gameApi.getCurrentNode();
        analysisActive = true;
    }

    boolean configure(String engine, int millis) {
        if (!analysisActive
                || Objects.equals(engine, configuredEngine) && millis == configuredMillis) {
            return false;
        }
        pause();
        resume(engine, millis, null);
        return true;
    }

    void pause() {
        analysisActive = false;
        analysisGeneration++;
        analysisHandler.removeCallbacks(resumeAnalysis);
        analysisDebouncing = false;
        if (engineSession != null) {
            engineSession.close();
        }
        engineSession = null;
    }

    void reset() {
        pause();
        results.clear();
        positions.clear();
    }

    Saved save() {
        return results.save();
    }

    boolean isActive() {
        return analysisActive;
    }

    boolean isBuiltin() {
        return engineSession != null && engineSession.isBuiltin();
    }

    PositionSnapshot position(Node node) {
        return positions.get(node);
    }

    Result result(PositionSnapshot position) {
        return results.result(position);
    }

    static final class Summary {
        final boolean ready;
        final boolean unavailable;
        final MoveClassifier.Feedback feedback;
        final Node branchPoint;
        final PositionMove recommendation;

        Summary(
                boolean ready,
                MoveClassifier.Feedback feedback,
                Node branchPoint,
                PositionMove recommendation) {
            this.ready = ready;
            this.unavailable = ready && feedback == null;
            this.feedback = feedback;
            this.branchPoint = branchPoint;
            this.recommendation = recommendation;
        }
    }

    Summary summary(Node current) {
        PositionSnapshot parent = position(current.getParent());
        PositionSnapshot position = position(current);
        Result before = result(parent);
        Result after = result(position);
        boolean ready = after != null && (current.getParent() == null || before != null);
        boolean continuation =
                gameApi.getContinuations(current).isEmpty()
                        && after != null
                        && after.preferredMove != null;
        PGNEntry entry = current.getEntry();
        PositionMove preferred = before == null ? null : before.preferredMove;
        boolean matches =
                entry != null
                        && preferred != null
                        && Move.equalPositions(entry.move, preferred.move)
                        && Move.isPromotionMove(entry.move) == Move.isPromotionMove(preferred.move)
                        && (!Move.isPromotionMove(entry.move)
                                || Move.getPromotionPiece(entry.move)
                                        == Move.getPromotionPiece(preferred.move));
        MoveClassifier.Feedback feedback =
                entry == null || parent == null || position == null
                        ? null
                        : MoveClassifier.classify(
                                before == null ? null : before.evaluation,
                                after == null ? null : after.evaluation,
                                parent.turn == BoardConstants.WHITE,
                                matches,
                                parent.moves.size(),
                                position.state == BoardConstants.MATE,
                                AnalysisResults.isDraw(position.state));
        return new Summary(
                ready,
                feedback,
                continuation ? current : current.getParent(),
                continuation ? after.preferredMove : entry == null || matches ? null : preferred);
    }

    boolean selectPosition() {
        boolean changed = displayedNode != gameApi.getCurrentNode();
        displayedNode = gameApi.getCurrentNode();
        if (!gameApi.isDuckVariant()) {
            rememberPosition(displayedNode);
            if (displayedNode.getParent() != null) {
                rememberPosition(displayedNode.getParent());
            }
        }
        if (!analysisActive || engineSession == null || !engineSession.supportsAnalysis()) {
            return changed;
        }
        if (changed) {
            analysisHandler.removeCallbacks(resumeAnalysis);
            analysisDebouncing = true;
            analysisHandler.postDelayed(resumeAnalysis, ANALYSIS_DEBOUNCE_MILLIS);
            PositionSnapshot current = positions.get(displayedNode);
            PositionSnapshot parent = positions.get(displayedNode.getParent());
            // Local Engine results depend on the live Position; OEX can finish recorded work.
            if (analysisFen != null
                    && (engineSession.isBuiltin()
                            || (!matchesAnalysis(current) && !matchesAnalysis(parent)))) {
                analysisFen = null;
                requestedPosition = null;
                analysisSwitchPending = true;
                int generation = ++analysisGeneration;
                engineSession.abort(
                        () -> {
                            if (analysisActive && generation == analysisGeneration) {
                                analysisSwitchPending = false;
                                startNextAnalysis();
                            }
                        });
            }
        }
        return changed;
    }

    private boolean matchesAnalysis(PositionSnapshot position) {
        return position != null
                && requestedPosition != null
                && position.positionCommand.equals(requestedPosition.positionCommand);
    }

    private void rememberPosition(Node node) {
        if (positions.containsKey(node)) {
            return;
        }
        PositionSnapshot position = gameApi.getPositionSnapshot(node);
        if (position != null) {
            positions.put(node, position);
        }
    }

    void startNextAnalysis() {
        if (!analysisActive
                || analysisDebouncing
                || analysisSwitchPending
                || analysisFen != null
                || engineSession == null
                || !engineSession.supportsAnalysis()
                || displayedNode != gameApi.getCurrentNode()) {
            return;
        }
        PositionSnapshot current = positions.get(displayedNode);
        if (current == null) {
            return;
        }
        PositionSnapshot parent = positions.get(displayedNode.getParent());
        // Feedback needs both sides of the move, even when navigation skips uncached positions.
        PositionSnapshot next =
                parent != null && results.result(parent) == null
                        ? parent
                        : results.result(current) == null ? current : null;
        if (next == null) {
            listener.onChanged();
            return;
        }
        requestedPosition = next;
        analysisFen = next.fen;
        analysisTurn = next.turn;
        engineSession.analyze(next, configuredMillis);
    }

    public void OnEngineInfo(String message, float value) {
        // UCI scores are relative to the side to move in the searched position.
        if (analysisActive
                && analysisFen != null
                && matchesAnalysis(positions.get(displayedNode))
                && message != null
                && message.contains(" score ")) {
            listener.onEvaluation(analysisTurn == BoardConstants.WHITE ? value : -value);
        }
    }

    public void OnEngineError() {
        listener.onEvaluation(null);
        if (!analysisActive || analysisFen == null) {
            return;
        }
        results.unavailable(requestedPosition);
        analysisFen = null;
        requestedPosition = null;
        listener.onChanged();
        // Continue after the engine's error callback has unwound and released its search.
        analysisHandler.post(this::startNextAnalysis);
    }

    public void onAnalysisComplete(String fen, String bestMove, EngineEvaluation evaluation) {
        if (!analysisActive
                || !fen.equals(analysisFen)
                || requestedPosition == null
                || displayedNode != gameApi.getCurrentNode()) {
            return;
        }
        results.complete(requestedPosition, bestMove, evaluation);
        analysisFen = null;
        requestedPosition = null;
        listener.onChanged();
        // The engine releases its completed search after this callback returns.
        analysisHandler.post(this::startNextAnalysis);
    }

    public void OnEngineMove(int move, int duckMove, int value) {
        // Analysis must never play the engine's suggested move.
    }

    public void OnEngineStarted() {}

    public void OnEngineAborted() {}
}
