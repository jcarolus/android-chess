package jwtc.chess;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.StringJoiner;
import jwtc.chess.board.BoardPosition;

/** Identity of a PGN's main line, independent of presentation and descriptive tags. */
public final class PgnFingerprint {
    private static final String VERSION = "1";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private PgnFingerprint() {}

    /** Returns null when an existing row has no parseable PGN. */
    public static String of(String pgn) {
        if (pgn == null || pgn.trim().isEmpty() || pgn.length() > Pgn.MAX_GAME_SIZE) {
            return null;
        }
        try {
            return of(Pgn.parse(pgn));
        } catch (PgnSyntaxError error) {
            return null;
        }
    }

    public static String of(PgnDocument document) {
        String variant = document.tags.getOrDefault("Variant", "").trim().toLowerCase(Locale.ROOT);
        String fen = document.tags.getOrDefault("FEN", "").trim().replaceAll("\\s+", " ");
        BoardPosition position = null;
        if (!fen.isEmpty()) {
            try {
                position = new BoardPosition(fen);
                fen = position.fen();
            } catch (IllegalArgumentException error) {
                // Syntax-only imports can still carry an invalid Starting Position.
            }
        }
        if (variant.isEmpty() || variant.equals("standard") || variant.equals("from position")) {
            if (position != null && position.duck() >= 0) {
                variant = "duck";
            } else if (position != null && position.chess960()) {
                variant = "chess960";
            } else {
                variant = "standard";
            }
        } else if (variant.equals("fischerandom")) {
            variant = "chess960";
        }

        if (position != null && variant.equals("chess960")) {
            StringBuilder rights = new StringBuilder();
            for (boolean white : new boolean[] {true, false}) {
                int[] files = position.chess960Files(white);
                for (boolean kingside : new boolean[] {true, false}) {
                    int file = files[kingside ? 2 : 1];
                    if (position.canCastle(white, kingside) && file >= 0) {
                        rights.append((char) ((white ? 'A' : 'a') + file));
                    }
                }
            }
            position.castling = rights.toString();
            fen = position.fen();
        }

        if (variant.equals("duck") && fen.equals(BoardPosition.DUCK_START_FEN)
                || (variant.equals("standard") || variant.equals("king of the hill"))
                        && fen.equals(BoardPosition.START_FEN)) {
            fen = "";
        }

        StringJoiner moves = new StringJoiner(" ");
        for (PgnDocument.Move move : document.mainLine) {
            moves.add(move.san.replace('0', 'O'));
        }
        // Length-prefix fields because free-form PGN tags can contain separators.
        String key =
                VERSION + ":" + variant.length() + ":" + variant + fen.length() + ":" + fen + moves;
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(key.getBytes(StandardCharsets.UTF_8));
            char[] hex = new char[digest.length * 2];
            for (int i = 0; i < digest.length; i++) {
                hex[i * 2] = HEX[(digest[i] & 0xff) >>> 4];
                hex[i * 2 + 1] = HEX[digest[i] & 0xf];
            }
            return new String(hex);
        } catch (NoSuchAlgorithmException error) {
            throw new AssertionError(error);
        }
    }
}
