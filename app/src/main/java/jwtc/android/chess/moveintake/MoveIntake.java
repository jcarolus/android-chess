package jwtc.android.chess.moveintake;

/**
 * The rules a Mode applies to a move the user offers. Every input (board, castle choice, text,
 * dictation, premove) offers its {@link MoveCandidate} here, and answers to the questions an
 * outcome asks come back as a new offer.
 */
public interface MoveIntake {
    /**
     * Decides what happens to the candidate and plays it when the Mode allows. {@link
     * MoveOutcome#PLAYED} means the move was applied; a move the board refuses comes back rejected
     * as {@link MoveOutcome.Reason#ILLEGAL}.
     */
    MoveOutcome offer(MoveCandidate candidate);
}
