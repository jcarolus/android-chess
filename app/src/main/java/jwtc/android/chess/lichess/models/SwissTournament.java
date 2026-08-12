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
}
