package jwtc.android.chess.play;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jwtc.chess.Pos;
import jwtc.chess.board.BoardConstants;

final class DictatedMove {
    private static final List<String> RANKS =
            Arrays.asList("one", "two", "three", "four", "five", "six", "seven", "eight");
    private static final List<String> PIECES = Arrays.asList("knight", "bishop", "rook", "queen");
    private static final int[] PROMOTIONS = {
        BoardConstants.KNIGHT, BoardConstants.BISHOP, BoardConstants.ROOK, BoardConstants.QUEEN
    };
    private static final String RANK = "([1-8]|" + String.join("|", RANKS) + ")";
    private static final Pattern MOVE =
            Pattern.compile(
                    "([a-h])\\s*"
                            + RANK
                            + "\\s+([a-h])\\s*"
                            + RANK
                            + "(?:\\s+("
                            + String.join("|", PIECES)
                            + "))?");

    final int from;
    final int to;
    final int promotion;

    private DictatedMove(int from, int to, int promotion) {
        this.from = from;
        this.to = to;
        this.promotion = promotion;
    }

    static DictatedMove parse(String text) {
        Matcher matcher = MOVE.matcher(text.trim().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            return null;
        }
        String piece = matcher.group(5);
        return new DictatedMove(
                square(matcher.group(1), matcher.group(2)),
                square(matcher.group(3), matcher.group(4)),
                piece == null ? -1 : PROMOTIONS[PIECES.indexOf(piece)]);
    }

    private static int square(String file, String rank) {
        int number = rank.length() == 1 ? rank.charAt(0) - '0' : RANKS.indexOf(rank) + 1;
        return Pos.fromColAndRow(file.charAt(0) - 'a', 8 - number);
    }
}
