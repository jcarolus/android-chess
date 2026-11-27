package jwtc.chess;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jwtc.chess.board.BoardConstants;

/**
 * Turns a move written as text into one of a Position's legal moves. Every text input resolves
 * here: PGN SAN tokens from import, replay and Lichess, SAN the user typed or dictated, and typed
 * coordinates. A text that matches no legal move, or more than one, resolves to nothing.
 */
public final class MoveResolver {
    private static final Pattern SAN =
            Pattern.compile("([KQRBN])?([a-h])?([1-8])?(x)?([a-h][1-8])(?:=?([QRBN]))?");
    private static final Pattern CASTLING = Pattern.compile("[O0]-[O0](-[O0])?");
    private static final Pattern DUCK = Pattern.compile("@([a-h][1-8])$");

    private MoveResolver() {}

    /** A resolved PGN SAN token: the legal move and, in Duck chess, the Duck's square or -1. */
    public static final class SanMove {
        public final LegalMove move;
        public final int duckSquare;

        SanMove(LegalMove move, int duckSquare) {
            this.move = move;
            this.duckSquare = duckSquare;
        }
    }

    /** What a typed or dictated move resolves to. */
    public static final class Resolution {
        public enum Kind {
            MOVE,
            NEEDS_PROMOTION,
            NONE
        }

        private static final Resolution NONE = new Resolution(Kind.NONE, null, -1, -1);

        public final Kind kind;

        /** The move, for {@link Kind#MOVE}. */
        public final LegalMove move;

        /** The pawn's squares, for {@link Kind#NEEDS_PROMOTION}. */
        public final int from, to;

        private Resolution(Kind kind, LegalMove move, int from, int to) {
            this.kind = kind;
            this.move = move;
            this.from = from;
            this.to = to;
        }

        static Resolution of(LegalMove move) {
            return move == null ? NONE : new Resolution(Kind.MOVE, move, -1, -1);
        }

        static Resolution promotion(LegalMove pawnMove) {
            return pawnMove == null
                    ? NONE
                    : new Resolution(
                            Kind.NEEDS_PROMOTION,
                            null,
                            Move.getFrom(pawnMove.packed),
                            Move.getTo(pawnMove.packed));
        }
    }

    /**
     * Resolves a PGN SAN token. It accepts check marks and move annotations, a Duck placement
     * ({@code @d4}), castling written with zeros, a promotion without {@code =} and more
     * disambiguation than the position needs. Piece letters are case-sensitive and a capture must
     * be written as one.
     */
    public static SanMove san(List<LegalMove> moves, String token) {
        if (token == null) {
            return null;
        }
        String text = stripSuffixes(token.trim());
        int duckSquare = -1;
        Matcher duck = DUCK.matcher(text);
        if (duck.find()) {
            duckSquare = square(duck.group(1));
            text = stripSuffixes(text.substring(0, duck.start()));
        }
        LegalMove move;
        if (CASTLING.matcher(text).matches()) {
            move = castle(moves, text.length() > 3);
        } else {
            Matcher san = SAN.matcher(text);
            if (!san.matches()) {
                return null;
            }
            move = sanMatch(moves, san);
        }
        return move == null ? null : new SanMove(move, duckSquare);
    }

    /**
     * Resolves typed coordinates. Castling is written king to rook square: in Chess 960 exactly as
     * the board writes it, otherwise from the king to the rook's corner. A promotion of -1 matches
     * only moves that do not promote; when it leaves out the piece of a legal promotion the user is
     * asked for it.
     */
    public static Resolution coordinates(
            List<LegalMove> moves, Variant variant, int from, int to, int promotion) {
        LegalMove move = coordinateMatch(moves, variant, from, to, promotion);
        if (move == null && promotion < 0) {
            return Resolution.promotion(
                    coordinateMatch(moves, variant, from, to, BoardConstants.QUEEN));
        }
        return Resolution.of(move);
    }

    /**
     * Resolves typed or dictated SAN, which ignores case and {@code =}, and need not write the
     * capture or check. {@code "castle"} means either castling when only one is legal. When the
     * text leaves out the promotion piece, every choice must move the same pawn.
     */
    public static Resolution spokenSan(List<LegalMove> moves, String san) {
        String spoken = san.toLowerCase(Locale.ROOT);
        LegalMove result = null;
        for (LegalMove move : moves) {
            if (spokenMatches(spoken, move.san)) {
                if (result != null) {
                    return Resolution.NONE;
                }
                result = move;
            }
        }
        if (result != null) {
            return Resolution.of(result);
        }
        for (LegalMove move : moves) {
            if (Move.isPromotionMove(move.packed)
                    && spokenMatches(spoken, move.san.replaceAll("=[QRBN]", ""))) {
                if (result != null && !Move.equalPositions(result.packed, move.packed)) {
                    return Resolution.NONE;
                }
                result = move;
            }
        }
        return Resolution.promotion(result);
    }

    private static String stripSuffixes(String text) {
        return text.replaceAll("[+#?!\\s]+$", "");
    }

    private static LegalMove castle(List<LegalMove> moves, boolean queenside) {
        String expected = queenside ? "O-O-O" : "O-O";
        for (LegalMove move : moves) {
            if (stripSuffixes(move.san).equals(expected)) {
                return move;
            }
        }
        return null;
    }

    private static LegalMove sanMatch(List<LegalMove> moves, Matcher san) {
        char piece = san.group(1) == null ? 'P' : san.group(1).charAt(0);
        String fromFile = san.group(2), fromRank = san.group(3);
        boolean capture = san.group(4) != null;
        int destination = square(san.group(5));
        int promotion = san.group(6) == null ? -1 : promotionPiece(san.group(6).charAt(0));
        LegalMove result = null;
        for (LegalMove move : moves) {
            if (move.san.startsWith("O-O")) {
                continue;
            }
            int from = Move.getFrom(move.packed);
            int movePromotion =
                    Move.isPromotionMove(move.packed) ? Move.getPromotionPiece(move.packed) : -1;
            if (Move.getTo(move.packed) != destination
                    || movePromotion != promotion
                    || sanPiece(move.san) != piece
                    || move.san.indexOf('x') >= 0 != capture
                    || fromFile != null && !fromFile.equals(Pos.colToString(from))
                    || fromRank != null && !fromRank.equals(Pos.rowToString(from))) {
                continue;
            }
            if (result != null) {
                return null;
            }
            result = move;
        }
        return result;
    }

    private static LegalMove coordinateMatch(
            List<LegalMove> moves, Variant variant, int from, int to, int promotion) {
        for (LegalMove move : moves) {
            if (Move.getFrom(move.packed) != from
                    || Move.isPromotionMove(move.packed) != (promotion >= 0)
                    || (promotion >= 0 && Move.getPromotionPiece(move.packed) != promotion)) {
                continue;
            }
            int destination = Move.getTo(move.packed);
            if (Move.isOO(move.packed) || Move.isOOO(move.packed)) {
                if (variant == Variant.CHESS_960) {
                    if (move.coordinate.equals(Pos.toString(from) + Pos.toString(to))) {
                        return move;
                    }
                    continue;
                }
                destination = Pos.fromColAndRow(Move.isOO(move.packed) ? 7 : 0, Pos.row(from));
            }
            if (destination == to) {
                return move;
            }
        }
        return null;
    }

    private static boolean spokenMatches(String spoken, String legalSan) {
        String expected = legalSan.toLowerCase(Locale.ROOT).replace("=", "");
        if (spoken.equals("castle")) {
            return expected.matches("o-o(?:-o)?[+#]?");
        }
        if (!spoken.contains("x")) {
            expected = expected.replace("x", "");
        }
        return spoken.equals(expected) || spoken.equals(expected.replaceAll("[+#]$", ""));
    }

    /** The moving piece's letter in a legal move's SAN: pawn moves have none. */
    private static char sanPiece(String san) {
        char first = san.charAt(0);
        return Character.isUpperCase(first) ? first : 'P';
    }

    /** A square the patterns above have already checked. */
    private static int square(String name) {
        return Pos.fromColAndRow(name.charAt(0) - 'a', '8' - name.charAt(1));
    }

    private static int promotionPiece(char letter) {
        switch (letter) {
            case 'Q':
                return BoardConstants.QUEEN;
            case 'R':
                return BoardConstants.ROOK;
            case 'B':
                return BoardConstants.BISHOP;
            default:
                return BoardConstants.KNIGHT;
        }
    }
}
