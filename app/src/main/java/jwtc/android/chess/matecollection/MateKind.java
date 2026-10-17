package jwtc.android.chess.matecollection;

import android.net.Uri;

import jwtc.android.chess.puzzle.MyPuzzleProvider;

/** The two local Mate Collections and how they differ. */
public enum MateKind {
    /** Mate-in-Two Puzzles: browsed by hand, no score. */
    MATE_IN_TWO_PUZZLES("puzzlePos", null, null),
    /** Practice Positions: worked through in order, with a score. */
    PRACTICE_POSITIONS("practicePos", "practiceNumPlayed", "practiceSolved");

    final String positionKey;
    final String playedKey;
    final String solvedKey;

    MateKind(String positionKey, String playedKey, String solvedKey) {
        this.positionKey = positionKey;
        this.playedKey = playedKey;
        this.solvedKey = solvedKey;
    }

    boolean hasScore() {
        return playedKey != null;
    }

    Uri uri() {
        return this == PRACTICE_POSITIONS ? MyPuzzleProvider.CONTENT_URI_PRACTICES : MyPuzzleProvider.CONTENT_URI_PUZZLES;
    }
}
