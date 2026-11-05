package jwtc.android.chess.matecollection;

/** How many Practice Positions were played and how many of those were solved. */
public final class MateScore {
    public static final MateScore NONE = new MateScore(0, 0);

    public final int played;
    public final int solved;

    public MateScore(int played, int solved) {
        this.played = played;
        this.solved = solved;
    }

    /** A wrong reply counts as played; solving the position afterwards counts again. */
    MateScore afterWrong() {
        return new MateScore(played + 1, solved);
    }

    MateScore afterSolved() {
        return new MateScore(played + 1, solved + 1);
    }

    public float percentage() {
        return played > 0 ? (float) solved / played * 100 : 0f;
    }
}
