package jwtc.android.chess.play;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import jwtc.chess.Pos;
import jwtc.chess.board.BoardConstants;

final class DictatedMove {
    private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{M}\\p{N}\\s-]+");

    static final class Vocabulary {
        private final Map<String, Integer> files = new LinkedHashMap<>();
        private final Map<String, Integer> ranks = new LinkedHashMap<>();
        private final Map<String, Integer> promotions = new LinkedHashMap<>();
        private final Pattern move;

        Vocabulary(String eAliases, String[] rankWords, String[] promotionWords) {
            if (rankWords.length != 8 || promotionWords.length != 4) {
                throw new IllegalArgumentException(
                        "Expected eight ranks and four promotion pieces");
            }
            for (int index = 0; index < 8; index++) {
                addAliases(files, Character.toString((char) ('a' + index)), index);
                addAliases(ranks, Integer.toString(index + 1), index + 1);
                addAliases(ranks, rankWords[index], index + 1);
            }
            addAliases(files, eAliases, 4);
            int[] pieces = {
                BoardConstants.KNIGHT,
                BoardConstants.BISHOP,
                BoardConstants.ROOK,
                BoardConstants.QUEEN
            };
            for (int index = 0; index < pieces.length; index++) {
                addAliases(promotions, promotionWords[index], pieces[index]);
            }
            String square = alternatives(files) + "\\s*" + alternatives(ranks);
            move =
                    Pattern.compile(
                            square
                                    + "(?:\\s+|\\s*-\\s*|(?<=\\p{sc=Han}))"
                                    + square
                                    + "(?:(?:\\s+|\\s*-\\s*|(?=\\p{sc=Han}))"
                                    + alternatives(promotions)
                                    + ")?");
        }

        private static void addAliases(Map<String, Integer> words, String aliases, int value) {
            for (String alias : aliases.split("\\|", -1)) {
                String word = normalize(alias);
                Integer previous = words.putIfAbsent(word, value);
                if (word.isEmpty() || (previous != null && previous != value)) {
                    throw new IllegalArgumentException("Invalid or ambiguous move alias: " + alias);
                }
            }
        }

        private static String alternatives(Map<String, Integer> words) {
            return words.keySet().stream()
                    .map(Pattern::quote)
                    .collect(Collectors.joining("|", "(", ")"));
        }

        int square(String file, String rank) {
            return Pos.fromColAndRow(files.get(file), 8 - ranks.get(rank));
        }
    }

    final int from;
    final int to;
    final int promotion;

    private DictatedMove(int from, int to, int promotion) {
        this.from = from;
        this.to = to;
        this.promotion = promotion;
    }

    String describe() {
        return Pos.toString(from) + " " + Pos.toString(to);
    }

    static DictatedMove parse(String text, Vocabulary vocabulary) {
        Matcher matcher = vocabulary.move.matcher(normalize(text));
        if (!matcher.matches()) {
            return null;
        }
        String piece = matcher.group(5);
        return new DictatedMove(
                vocabulary.square(matcher.group(1), matcher.group(2)),
                vocabulary.square(matcher.group(3), matcher.group(4)),
                piece == null ? -1 : vocabulary.promotions.get(piece));
    }

    static DictatedMove parseUnambiguous(String text, Iterator<Vocabulary> vocabularies) {
        DictatedMove result = null;
        while (vocabularies.hasNext()) {
            DictatedMove candidate = parse(text, vocabularies.next());
            if (candidate == null) {
                continue;
            }
            if (result != null
                    && (result.from != candidate.from
                            || result.to != candidate.to
                            || result.promotion != candidate.promotion)) {
                return null;
            }
            result = candidate;
        }
        return result;
    }

    private static String normalize(String text) {
        StringBuilder coordinates = new StringBuilder(text.length());
        text.codePoints()
                .forEach(
                        character -> {
                            if (Character.isDigit(character)) {
                                coordinates.append(Character.digit(character, 10));
                            } else if ((character >= 'Ａ' && character <= 'Ｚ')
                                    || (character >= 'ａ' && character <= 'ｚ')) {
                                coordinates.append((char) (character - 'Ａ' + 'A'));
                            } else {
                                coordinates.appendCodePoint(character);
                            }
                        });
        return SEPARATORS.matcher(coordinates).replaceAll(" ").trim().toLowerCase(Locale.ROOT);
    }
}
