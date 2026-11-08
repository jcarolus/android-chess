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
        private static final String[] NATO_FILES = {
            "alfa", "bravo", "charlie", "delta", "echo", "foxtrot", "golf", "hotel"
        };

        private final Map<String, Integer> files = new LinkedHashMap<>();
        private final Map<String, Integer> ranks = new LinkedHashMap<>();
        private final Map<String, Integer> promotions = new LinkedHashMap<>();
        private final Pattern move;
        private final Map<String, String> sanWords = new LinkedHashMap<>();

        Vocabulary(
                String[] rankWords,
                String[] promotionWords,
                String[] pieceWords,
                String captures) {
            String[] symbols = {"", "n", "b", "r", "q", "k"};
            for (int i = 0; i < symbols.length; i++) {
                for (String word : pieceWords[i].split("\\|")) {
                    sanWords.put(normalize(word), symbols[i]);
                }
            }
            for (String word : captures.split("\\|")) {
                sanWords.put(normalize(word), "x");
            }
            if (rankWords.length != 8 || promotionWords.length != 4) {
                throw new IllegalArgumentException(
                        "Expected eight ranks and four promotion pieces");
            }
            for (int index = 0; index < 8; index++) {
                addAliases(files, Character.toString((char) ('a' + index)), index);
                addAliases(files, NATO_FILES[index], index);
                addAliases(ranks, Integer.toString(index + 1), index + 1);
                addAliases(ranks, rankWords[index], index + 1);
            }
            int[] pieces = {
                BoardConstants.KNIGHT,
                BoardConstants.BISHOP,
                BoardConstants.ROOK,
                BoardConstants.QUEEN
            };
            for (int index = 0; index < pieces.length; index++) {
                addAliases(promotions, promotionWords[index], pieces[index]);
                addAliases(promotions, "nbrq".substring(index, index + 1), pieces[index]);
                for (String word : promotionWords[index].split("\\|")) {
                    sanWords.put(normalize(word), "nbrq".substring(index, index + 1));
                }
            }
            String square = alternatives(files) + "\\s*" + alternatives(ranks);
            move =
                    Pattern.compile(
                            square
                                    + "(?:\\s*|\\s*-\\s*)"
                                    + square
                                    + "(?:(?:\\s*|\\s*-\\s*)"
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

    final String san;
    final int from;
    final int to;
    final int promotion;

    private DictatedMove(int from, int to, int promotion) {
        this.san = null;
        this.from = from;
        this.to = to;
        this.promotion = promotion;
    }

    private DictatedMove(String san) {
        this.san = san;
        this.from = -1;
        this.to = -1;
        this.promotion = -1;
    }

    boolean matchesSan(String legalSan) {
        String expected = legalSan.toLowerCase(Locale.ROOT).replace("=", "");
        return san.equals(expected) || san.equals(expected.replaceAll("[+#]$", ""));
    }

    boolean matchesSanWithoutPromotion(String legalSan) {
        return legalSan.matches(".*=[QRBN][+#]?") && matchesSan(legalSan.replaceAll("=[QRBN]", ""));
    }

    String describe() {
        if (san != null) {
            return san;
        }
        return Pos.toString(from) + " " + Pos.toString(to);
    }

    static DictatedMove parse(String text, Vocabulary vocabulary) {
        Matcher matcher = vocabulary.move.matcher(normalize(text));
        if (!matcher.matches()) {
            return parseSan(text, vocabulary);
        }
        String piece = matcher.group(5);
        return new DictatedMove(
                vocabulary.square(matcher.group(1), matcher.group(2)),
                vocabulary.square(matcher.group(3), matcher.group(4)),
                piece == null ? -1 : vocabulary.promotions.get(piece));
    }

    private static DictatedMove parseSan(String text, Vocabulary vocabulary) {
        String value = normalize(text, Pattern.compile("[^\\p{L}\\p{M}\\p{N}\\s+#-]+"));
        for (Map.Entry<String, String> word : vocabulary.sanWords.entrySet()) {
            value =
                    value.replaceAll(
                            "(?<![\\p{L}\\p{M}])"
                                    + Pattern.quote(word.getKey())
                                    + "(?![\\p{L}\\p{M}])",
                            word.getValue());
        }
        for (Map.Entry<String, Integer> rank : vocabulary.ranks.entrySet()) {
            value =
                    value.replaceAll(
                            "(?<![\\p{L}\\p{M}])"
                                    + Pattern.quote(rank.getKey())
                                    + "(?![\\p{L}\\p{M}])",
                            rank.getValue().toString());
        }
        value = value.replaceAll("\\s+", "");
        if (!value.matches(
                "(?:[kqrbn]?[a-h]?[1-8]?x?[a-h][1-8][qrbn]?|[o0]-[o0](?:-[o0])?)[+#]?")) {
            return null;
        }
        return new DictatedMove(value.replace('0', 'o'));
    }

    static DictatedMove parseUnambiguous(String text, Iterator<Vocabulary> vocabularies) {
        DictatedMove result = null;
        while (vocabularies.hasNext()) {
            DictatedMove candidate = parse(text, vocabularies.next());
            if (candidate == null) {
                continue;
            }
            if (result != null
                    && (!java.util.Objects.equals(result.san, candidate.san)
                            || result.from != candidate.from
                            || result.to != candidate.to
                            || result.promotion != candidate.promotion)) {
                return null;
            }
            result = candidate;
        }
        return result;
    }

    private static String normalize(String text) {
        return normalize(text, SEPARATORS);
    }

    private static String normalize(String text, Pattern separators) {
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
        return separators.matcher(coordinates).replaceAll(" ").trim().toLowerCase(Locale.ROOT);
    }
}
