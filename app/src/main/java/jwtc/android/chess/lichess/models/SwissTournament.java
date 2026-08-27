package jwtc.android.chess.lichess.models;

public class SwissTournament {
    public String id;
    public String name;
    public String status; // created | started | finished
    public int round;
    public int nbRounds;
    public int nbPlayers;
    public boolean rated;
    public String variant; // e.g. "standard"
    public String startsAt;
    public Clock clock;

    public static class Clock {
        public int limit;     // seconds
        public int increment; // seconds
    }

    // Lichess speed classification uses estimated duration = limit + 40*increment.
    // The Board API only plays rapid/classical/correspondence => estimated >= 480s (rapid floor).
    private static final int RAPID_MIN_ESTIMATED_SECONDS = 480;

    public boolean isBoardTimeControl() {
        if (clock == null) {
            return true; // unknown -> don't over-block
        }
        return clock.limit + 40 * clock.increment >= RAPID_MIN_ESTIMATED_SECONDS;
    }

    public boolean isSupportedVariant() {
        // The app's board only renders standard and chess960.
        return variant == null || variant.equals("standard") || variant.equals("chess960");
    }

    public boolean isBoardCompatible() {
        return isBoardTimeControl() && isSupportedVariant();
    }
}
