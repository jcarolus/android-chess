package jwtc.chess.clock;
import jwtc.chess.board.BoardConstants;

public class Clock {
    protected static final String TAG = "Clock";

    protected long increment = 0;
    protected long whiteRemaining = 0;
    protected long blackRemaining = 0;
    protected long startTime = 0;

    public void setTimer(long given, long increment) {
        this.increment = increment;
        whiteRemaining = given;
        blackRemaining = given;
        startTime = 0;
    }

    public void switchTurn(int newTurn) {
        final long currentTime = System.currentTimeMillis();
        final long usedMillies = (currentTime - startTime);

        if (newTurn == BoardConstants.BLACK) {
            whiteRemaining -= usedMillies;
            whiteRemaining += increment;
        } else {
            blackRemaining -= usedMillies;
            blackRemaining += increment;
        }

        startTime = currentTime;
    }

    public long getRemaining(int turn) {
        return turn == BoardConstants.WHITE ? whiteRemaining : blackRemaining;
    }
}
