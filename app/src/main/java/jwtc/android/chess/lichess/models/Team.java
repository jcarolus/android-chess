package jwtc.android.chess.lichess.models;

public class Team {
    public String id;
    public String name;
    public String description;
    public int nbMembers;
    public boolean open;
    public Boolean joined; // may be absent; null means unknown
}
