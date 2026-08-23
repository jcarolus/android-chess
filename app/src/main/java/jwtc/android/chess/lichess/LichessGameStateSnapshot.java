package jwtc.android.chess.lichess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import jwtc.android.chess.lichess.models.GameFull;
import jwtc.chess.board.BoardConstants;

/**
 * Immutable part of a Lichess game update used to distinguish stream snapshots from events.
 */
final class LichessGameStateSnapshot {
    final String gameId;
    final List<String> moves;
    final String status;
    final String winner;

    private LichessGameStateSnapshot(String gameId, List<String> moves, String status, String winner) {
        this.gameId = gameId;
        this.moves = Collections.unmodifiableList(moves);
        this.status = status;
        this.winner = winner;
    }

    static LichessGameStateSnapshot from(GameFull gameFull) {
        String moves = gameFull.state.moves;
        List<String> moveList = new ArrayList<>();
        if (moves != null && !moves.trim().isEmpty()) {
            Collections.addAll(moveList, moves.trim().split("\\s+"));
        }
        return new LichessGameStateSnapshot(
            gameFull.id,
            moveList,
            gameFull.state.status,
            gameFull.state.winner
        );
    }

    Transition transitionFrom(LichessGameStateSnapshot previous) {
        boolean newGame = previous == null || !Objects.equals(gameId, previous.gameId);
        if (newGame) {
            return new Transition(true, false, false, false, -1);
        }

        boolean moveApplied = moves.size() > previous.moves.size()
            && isPrefix(previous.moves, moves);
        boolean historyPositionChanged = moves.size() < previous.moves.size()
            && isPrefix(moves, previous.moves);
        boolean statusChanged = !Objects.equals(status, previous.status);

        int resignedColor = -1;
        if (statusChanged && "resign".equals(status)) {
            if ("white".equals(winner)) {
                resignedColor = BoardConstants.BLACK;
            } else if ("black".equals(winner)) {
                resignedColor = BoardConstants.WHITE;
            }
        }

        return new Transition(
            false,
            moveApplied,
            historyPositionChanged,
            statusChanged && "draw".equals(status),
            resignedColor
        );
    }

    boolean isStarted() {
        return "created".equals(status) || "started".equals(status);
    }

    @Override
    public String toString() {
        return "Snapshot {status=" + status + ", moves " + moves.size() + "}";
    }

    private static boolean isPrefix(List<String> prefix, List<String> moves) {
        if (prefix.size() > moves.size()) {
            return false;
        }
        for (int i = 0; i < prefix.size(); i++) {
            if (!prefix.get(i).equals(moves.get(i))) {
                return false;
            }
        }
        return true;
    }

    static final class Transition {
        final boolean newGame;
        final boolean moveApplied;
        final boolean historyPositionChanged;
        final boolean drawEnded;
        final int resignedColor;

        private Transition(
            boolean newGame,
            boolean moveApplied,
            boolean historyPositionChanged,
            boolean drawEnded,
            int resignedColor
        ) {
            this.newGame = newGame;
            this.moveApplied = moveApplied;
            this.historyPositionChanged = historyPositionChanged;
            this.drawEnded = drawEnded;
            this.resignedColor = resignedColor;
        }

        @Override
        public String toString() {
            return "Transition{" +
                "newGame=" + newGame +
                ", moveApplied=" + moveApplied +
                ", historyPositionChanged=" + historyPositionChanged +
                ", drawEnded=" + drawEnded +
                ", resignedColor=" + resignedColor +
                '}';
        }
    }
}
