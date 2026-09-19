package jwtc.android.chess.lichess;

final class ChallengeRequest {
    private ChallengeRequest() {}

    static String normalizeUsername(Object username) {
        if (!(username instanceof String)) {
            return null;
        }
        String normalized = ((String) username).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    static String pathFor(Object username) {
        String normalized = normalizeUsername(username);
        return normalized == null ? null : "/api/challenge/" + normalized;
    }
}
