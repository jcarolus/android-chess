package jwtc.android.chess.services;

import java.util.HashSet;
import java.util.Set;

/** Tracks last-move utterances while TextToSpeech owns the actual audio queue. */
final class LastMoveSpeechQueue {
    private final Set<String> pendingIds = new HashSet<>();
    private final Set<String> protectedIds = new HashSet<>();

    /**
     * Adds an utterance and returns whether it should be appended to an existing
     * last-move sequence. A false result means it starts a new sequence.
     */
    boolean start(String utteranceId, boolean protectSpeech) {
        boolean append = !pendingIds.isEmpty();
        pendingIds.add(utteranceId);
        if (protectSpeech) {
            protectedIds.add(utteranceId);
        }
        return append;
    }

    boolean hasProtectedSpeech() {
        return !protectedIds.isEmpty();
    }

    void finish(String utteranceId) {
        if (utteranceId == null) {
            return;
        }
        pendingIds.remove(utteranceId);
        protectedIds.remove(utteranceId);
    }

    void clear() {
        pendingIds.clear();
        protectedIds.clear();
    }
}
