package jwtc.chess.board;

import jwtc.chess.Move;

/** Compatibility with the incremental hashes stored in the ECO opening database. */
public final class EcoHash {
    private EcoHash() {}

    public static long afterMove(long hash, String beforeFen, String afterFen, int move) {
        boolean castle = Move.isOO(move) || Move.isOOO(move);
        if (castle) {
            int colour = beforeFen.split(" ")[1].equals("w") ? 1 : 0;
            int from = Move.getFrom(move);
            int to = Move.getTo(move);
            // The historical hash moves only the king, even when castling moves the rook.
            // A stationary Chess 960 king is XORed once by the native implementation.
            hash ^= BoardHashKeys.HASH_KEY[colour * 384 + 5 * 64 + to];
            if (from != to) hash ^= BoardHashKeys.HASH_KEY[colour * 384 + 5 * 64 + from];
            hash ^= BoardHashKeys.HASH_KEY[(Move.isOO(move) ? 768 : 770) + colour];
        } else {
            hash ^= pieces(beforeFen) ^ pieces(afterFen);
        }
        return hash ^ BoardHashKeys.HASH_KEY[772];
    }

    private static long pieces(String fen) {
        long hash = 0;
        int square = 0;
        for (int i = 0; i < fen.length() && fen.charAt(i) != ' '; i++) {
            char symbol = fen.charAt(i);
            if (symbol == '/') continue;
            if (symbol >= '1' && symbol <= '8') {
                square += symbol - '0';
            } else {
                int piece = "pnbrqk".indexOf(Character.toLowerCase(symbol));
                if (piece >= 0) {
                    int colour = Character.isUpperCase(symbol) ? 1 : 0;
                    hash ^= BoardHashKeys.HASH_KEY[colour * 384 + piece * 64 + square];
                }
                square++;
            }
        }
        return hash;
    }
}
