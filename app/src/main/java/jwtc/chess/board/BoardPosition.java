package jwtc.chess.board;

import jwtc.chess.Move;
import jwtc.chess.Pos;

/**
 * Editable FEN data, including incomplete positions in the setup screen. No move rules live here.
 */
public final class BoardPosition {
    public final char[] squares = new char[64];
    public boolean white;
    public String castling;
    public int ep;
    public int halfmove;
    public int fullmove;

    public BoardPosition(String fen) {
        String[] fields = fen.trim().split("\\s+");
        // The move counters are optional, as in EPD and many shared FENs.
        if (fields.length < 4 || fields.length > 6) {
            throw new IllegalArgumentException("Expected four to six FEN fields");
        }
        String[] ranks = fields[0].split("/");
        if (ranks.length != 8) {
            throw new IllegalArgumentException("Expected eight ranks");
        }
        for (int row = 0; row < 8; row++) {
            int col = 0;
            for (char c : ranks[row].toCharArray()) {
                if (c >= '1' && c <= '8') {
                    col += c - '0';
                } else {
                    if (col >= 8 || "pnbrqkPNBRQK$".indexOf(c) < 0) {
                        throw new IllegalArgumentException("Invalid piece");
                    }
                    squares[row * 8 + col++] = c;
                }
            }
            if (col != 8) {
                throw new IllegalArgumentException("Invalid rank width");
            }
        }
        if (!fields[1].equals("w") && !fields[1].equals("b")) {
            throw new IllegalArgumentException("Invalid turn");
        }
        white = fields[1].equals("w");
        castling = fields[2].equals("-") ? "" : normalizeCastling(fields[2]);
        ep = fields[3].equals("-") ? -1 : square(fields[3]);
        halfmove = fields.length > 4 ? Integer.parseInt(fields[4]) : 0;
        fullmove = fields.length > 5 ? Integer.parseInt(fields[5]) : 1;
        if (halfmove < 0 || fullmove < 1) {
            throw new IllegalArgumentException("Invalid move counters");
        }
    }

    /**
     * Castling rights in {@code KQkq} order. Shredder/X-FEN rook files ({@code HAha}) count as
     * kingside when the file lies beyond that side's king, else queenside.
     */
    private String normalizeCastling(String rights) {
        boolean[] present = new boolean[4];
        for (char c : rights.toCharArray()) {
            boolean white = Character.isUpperCase(c);
            char lower = Character.toLowerCase(c);
            boolean kingside;
            if (lower == 'k' || lower == 'q') {
                kingside = lower == 'k';
            } else if (lower >= 'a' && lower <= 'h') {
                kingside = lower - 'a' > kingFile(white);
            } else {
                throw new IllegalArgumentException("Invalid castling rights");
            }
            present[(white ? 0 : 2) + (kingside ? 0 : 1)] = true;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (present[i]) {
                out.append("KQkq".charAt(i));
            }
        }
        return out.toString();
    }

    private int kingFile(boolean white) {
        int base = white ? 56 : 0;
        char king = white ? 'K' : 'k';
        for (int col = 0; col < 8; col++) {
            if (squares[base + col] == king) {
                return col;
            }
        }
        return 4;
    }

    public static int square(String s) {
        if (!s.matches("[a-h][1-8]")) {
            throw new IllegalArgumentException("Invalid square: " + s);
        }
        return (8 - (s.charAt(1) - '0')) * 8 + s.charAt(0) - 'a';
    }

    /**
     * The {@link BoardConstants} piece at a square owned by {@code turn}, or {@link
     * BoardConstants#FIELD}.
     */
    public static int pieceType(char rustPieceChar, int turn) {
        if (rustPieceChar == 0
                || Character.isUpperCase(rustPieceChar) != (turn == BoardConstants.WHITE)) {
            return BoardConstants.FIELD;
        }
        switch (Character.toLowerCase(rustPieceChar)) {
            case 'p':
                return BoardConstants.PAWN;
            case 'n':
                return BoardConstants.KNIGHT;
            case 'b':
                return BoardConstants.BISHOP;
            case 'r':
                return BoardConstants.ROOK;
            case 'q':
                return BoardConstants.QUEEN;
            case 'k':
                return BoardConstants.KING;
            default:
                return BoardConstants.FIELD; // the duck marker '$'
        }
    }

    public int duck() {
        for (int i = 0; i < 64; i++) {
            if (squares[i] == '$') {
                return i;
            }
        }
        return -1;
    }

    public String fen() {
        StringBuilder out = new StringBuilder();
        for (int row = 0; row < 8; row++) {
            if (row > 0) {
                out.append('/');
            }
            int empty = 0;
            for (int col = 0; col < 8; col++) {
                char c = squares[row * 8 + col];
                if (c == 0) {
                    empty++;
                } else {
                    if (empty > 0) {
                        out.append(empty);
                    }
                    empty = 0;
                    out.append(c);
                }
            }
            if (empty > 0) {
                out.append(empty);
            }
        }
        return out
                + (white ? " w " : " b ")
                + (castling.isEmpty() ? "-" : castling)
                + " "
                + (ep < 0 ? "-" : Pos.toString(ep))
                + " "
                + halfmove
                + " "
                + fullmove;
    }

    /** Encode a Rust legal coordinate and SAN for the application's packed-move consumers. */
    public int packedMove(String coordinate, String san) {
        String pieceMove = coordinate.split("@")[0];
        int from = square(pieceMove.substring(0, 2));
        int to = square(pieceMove.substring(2, 4));
        int move;
        if (san.startsWith("O-O-O")) {
            move = Move.makeMoveOOO(from, from / 8 * 8 + 2);
        } else if (san.startsWith("O-O")) {
            move = Move.makeMoveOO(from, from / 8 * 8 + 6);
        } else if (pieceMove.length() == 5) {
            move =
                    Move.makeMovePromotion(
                            from, to, "pnbrqk".indexOf(pieceMove.charAt(4)), san.contains("x"));
        } else if (Character.toLowerCase(squares[from]) == 'p' && to == ep && squares[to] == 0) {
            move = Move.makeMoveEP(from, to);
        } else if (Character.toLowerCase(squares[from]) == 'p' && Math.abs(from - to) == 16) {
            move = Move.makeMoveFirstPawn(from, to);
        } else {
            move = san.contains("x") ? Move.makeMoveHit(from, to) : Move.makeMove(from, to);
        }
        return san.endsWith("+") || san.endsWith("#") ? Move.setCheck(move) : move;
    }

    public boolean chess960() {
        for (boolean side : new boolean[] {false, true}) {
            int base = side ? 56 : 0;
            char king = side ? 'K' : 'k', rook = side ? 'R' : 'r';
            if (castling.indexOf(side ? 'K' : 'k') >= 0
                    && (squares[base + 4] != king || squares[base + 7] != rook)) {
                return true;
            }
            if (castling.indexOf(side ? 'Q' : 'q') >= 0
                    && (squares[base + 4] != king || squares[base] != rook)) {
                return true;
            }
        }
        return false;
    }

    public static String chess960Fen(int seed) {
        if (seed < 0 || seed >= 960) {
            throw new IllegalArgumentException("Chess 960 seed must be 0..959");
        }
        char[] rank = new char[8];
        rank[1 + 2 * (seed % 4)] = 'B';
        seed /= 4;
        rank[2 * (seed % 4)] = 'B';
        seed /= 4;
        rank[emptyFile(rank, seed % 6)] = 'Q';
        seed /= 6;
        int[][] knights = {
            {0, 1}, {0, 2}, {0, 3}, {0, 4}, {1, 2}, {1, 3}, {1, 4}, {2, 3}, {2, 4}, {3, 4}
        };
        int a = emptyFile(rank, knights[seed][0]), b = emptyFile(rank, knights[seed][1]);
        rank[a] = rank[b] = 'N';
        rank[emptyFile(rank, 0)] = 'R';
        rank[emptyFile(rank, 0)] = 'K';
        rank[emptyFile(rank, 0)] = 'R';
        String back = new String(rank);
        return back.toLowerCase(java.util.Locale.ROOT)
                + "/pppppppp/8/8/8/8/PPPPPPPP/"
                + back
                + " w KQkq - 0 1";
    }

    private static int emptyFile(char[] rank, int index) {
        for (int i = 0; i < 8; i++) {
            if (rank[i] == 0 && index-- == 0) {
                return i;
            }
        }
        throw new IllegalArgumentException("No empty file");
    }
}
