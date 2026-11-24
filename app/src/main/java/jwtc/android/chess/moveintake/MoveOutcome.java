package jwtc.android.chess.moveintake;

import java.util.EnumMap;
import java.util.Map;

/**
 * What a {@link MoveIntake} did with a {@link MoveCandidate}. The activity renders it: it asks the
 * question an outcome names and offers the candidate again with the answer, and it restores the
 * board after a rejection.
 */
public final class MoveOutcome {
    public enum Kind {
        PLAYED,
        REJECTED,
        NEEDS_PROMOTION,
        NEEDS_CASTLE_CHOICE,
        CONFIRM_OVERWRITE,
        CONFIRM_MOVE,
        QUEUED_AS_PREMOVE
    }

    /** Why a candidate was rejected. */
    public enum Reason {
        ENDED,
        NOT_YOUR_TURN,
        NOT_READY,
        /**
         * Not a legal move. When the board refuses a move it reports that to its listeners, which
         * restore the board, so the activity leaves the board alone.
         */
        ILLEGAL
    }

    public static final MoveOutcome PLAYED = new MoveOutcome(Kind.PLAYED, null);
    public static final MoveOutcome NEEDS_PROMOTION = new MoveOutcome(Kind.NEEDS_PROMOTION, null);
    public static final MoveOutcome NEEDS_CASTLE_CHOICE =
            new MoveOutcome(Kind.NEEDS_CASTLE_CHOICE, null);
    public static final MoveOutcome CONFIRM_OVERWRITE =
            new MoveOutcome(Kind.CONFIRM_OVERWRITE, null);
    public static final MoveOutcome CONFIRM_MOVE = new MoveOutcome(Kind.CONFIRM_MOVE, null);
    public static final MoveOutcome QUEUED_AS_PREMOVE =
            new MoveOutcome(Kind.QUEUED_AS_PREMOVE, null);

    private static final Map<Reason, MoveOutcome> REJECTIONS = new EnumMap<>(Reason.class);

    static {
        for (Reason reason : Reason.values()) {
            REJECTIONS.put(reason, new MoveOutcome(Kind.REJECTED, reason));
        }
    }

    private final Kind kind;
    private final Reason reason;

    private MoveOutcome(Kind kind, Reason reason) {
        this.kind = kind;
        this.reason = reason;
    }

    public static MoveOutcome rejected(Reason reason) {
        return REJECTIONS.get(reason);
    }

    public Kind kind() {
        return kind;
    }

    /** The reason for a rejection, or null for every other outcome. */
    public Reason reason() {
        return reason;
    }

    @Override
    public String toString() {
        return reason == null ? kind.name() : kind + "(" + reason + ")";
    }
}
