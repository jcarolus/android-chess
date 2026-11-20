package jwtc.android.chess.analyze;

import android.os.Bundle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import jwtc.android.chess.engine.EngineEvaluation;
import jwtc.android.chess.services.GameApi.PositionMove;
import jwtc.android.chess.services.GameApi.PositionSnapshot;
import jwtc.chess.board.BoardConstants;

/** Completed Analysis results, including terminal Positions and unavailable results. */
final class AnalysisResults {
    static final class Result {
        final PositionMove preferredMove;
        final EngineEvaluation evaluation;
        final String bestMove;

        Result(PositionMove move, EngineEvaluation evaluation) {
            this.preferredMove = move;
            this.bestMove = move == null ? null : move.uci;
            this.evaluation = evaluation;
        }

        Result(String move, EngineEvaluation evaluation) {
            this.preferredMove = null;
            this.bestMove = move;
            this.evaluation = evaluation;
        }
    }

    static final class Saved {
        final String engine;
        final int millis;
        final Map<String, Result> results;

        Saved(String engine, int millis, Map<String, Result> results) {
            this.engine = engine;
            this.millis = millis;
            this.results = new LinkedHashMap<>(results);
        }

        void writeTo(Bundle session) {
            session.putString("engine", engine);
            session.putInt("millis", millis);
            ArrayList<Bundle> bundles = new ArrayList<>();
            for (Map.Entry<String, Result> entry : results.entrySet()) {
                Result analysis = entry.getValue();
                Bundle result = new Bundle();
                result.putString("position", entry.getKey());
                result.putString("move", analysis.bestMove);
                if (analysis.evaluation != null) {
                    result.putBoolean("mate", analysis.evaluation.mate);
                    result.putInt("value", analysis.evaluation.value);
                    result.putInt("depth", analysis.evaluation.depth);
                }
                bundles.add(result);
            }
            session.putParcelableArrayList("results", bundles);
        }

        static Saved readFrom(Bundle session) {
            Map<String, Result> results = new LinkedHashMap<>();
            ArrayList<Bundle> bundles = session.getParcelableArrayList("results");
            if (bundles != null) {
                for (Bundle result : bundles) {
                    if (result.containsKey("position")) {
                        results.put(
                                result.getString("position"),
                                new Result(
                                        result.getString("move"),
                                        result.containsKey("value")
                                                ? new EngineEvaluation(
                                                        result.getBoolean("mate"),
                                                        result.getInt("value"),
                                                        result.getInt("depth"))
                                                : null));
                    }
                }
            }
            return new Saved(session.getString("engine"), session.getInt("millis"), results);
        }
    }

    private final LinkedHashMap<String, Result> entries =
            new LinkedHashMap<String, Result>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Result> eldest) {
                    return size() > 128;
                }
            };
    private String engine;
    private int millis;

    void configure(String engine, int millis, Saved saved) {
        if (!Objects.equals(this.engine, engine) || this.millis != millis) {
            clear();
        }
        this.engine = engine;
        this.millis = millis;
        if (saved != null && Objects.equals(engine, saved.engine) && millis == saved.millis) {
            for (Map.Entry<String, Result> entry : saved.results.entrySet()) {
                put(entry.getKey(), entry.getValue());
            }
        }
    }

    Result result(PositionSnapshot position) {
        if (position == null) {
            return null;
        }
        // Terminal outcomes come from the board, including repetition history absent from FEN.
        if (isDraw(position.state)) {
            return new Result((PositionMove) null, new EngineEvaluation(false, 0, 0));
        }
        if (position.state == BoardConstants.MATE) {
            return new Result((PositionMove) null, new EngineEvaluation(true, 0, 0));
        }
        int winner = terminalWinner(position);
        if (winner != -1) {
            return new Result(
                    (PositionMove) null,
                    new EngineEvaluation(true, winner == BoardConstants.WHITE ? 1 : -1, 0));
        }
        Result result = get(position.positionCommand);
        if (result != null && result.preferredMove == null && result.bestMove != null) {
            result = new Result(position.findTurn(result.bestMove), result.evaluation);
            put(position.positionCommand, result);
        }
        return result;
    }

    void complete(PositionSnapshot position, String bestMove, EngineEvaluation evaluation) {
        PositionMove preferredMove = position.findTurn(bestMove);
        EngineEvaluation whiteEvaluation =
                evaluation == null
                        ? null
                        : evaluation.forWhite(position.turn == BoardConstants.WHITE);
        put(position.positionCommand, new Result(preferredMove, whiteEvaluation));
    }

    void unavailable(PositionSnapshot position) {
        put(position.positionCommand, new Result((PositionMove) null, null));
    }

    static int terminalWinner(PositionSnapshot position) {
        if (position == null) {
            return -1;
        }
        switch (position.state) {
            case BoardConstants.MATE:
            case BoardConstants.HILL_WIN:
                return position.turn == BoardConstants.WHITE
                        ? BoardConstants.BLACK
                        : BoardConstants.WHITE;
            case BoardConstants.DUCK_STALEMATE_WIN:
                return position.turn;
            default:
                return -1;
        }
    }

    static boolean isDraw(int state) {
        return state == BoardConstants.STALEMATE
                || state == BoardConstants.DRAW_MATERIAL
                || state == BoardConstants.DRAW_50
                || state == BoardConstants.DRAW_REPEAT;
    }

    Result get(String history) {
        return entries.get(history);
    }

    void put(String history, Result result) {
        entries.put(history, result);
    }

    void clear() {
        entries.clear();
    }

    Saved save() {
        return new Saved(engine, millis, entries);
    }
}
