package jwtc.chess;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.regex.Pattern;

/** Reads games in order, retaining the tag block and movetext as supplied. */
public final class PgnSplitter {
    private static final String EVENT = "[Event \"";
    private static final Pattern MOVE_NUMBER = Pattern.compile("[0-9]+\\..*");

    private final Reader reader;
    private int lookahead = -2;
    private String pending;

    public PgnSplitter(Reader reader) {
        this.reader = new BufferedReader(reader);
    }

    /** A new tag block after movetext starts another game, even if it has no Event tag. */
    public String next() throws IOException {
        StringBuilder game = new StringBuilder();
        boolean started = false;
        boolean hasEventTag = false;
        boolean movetext = false;
        boolean ended = false;
        int variationDepth = 0;

        String firstToken = pending == null ? readToken() : pending;
        pending = null;
        for (String token = firstToken; token != null; token = readToken()) {
            char first = token.charAt(0);
            boolean space = isSpace(first);
            boolean comment = first == '{' || first == ';';
            boolean tag = first == '[' && Pgn.isTagPair(token);
            boolean event = token.startsWith(EVENT);
            if (started
                    && ((tag && movetext)
                            || (event && hasEventTag)
                            || (ended && !space && !comment && startsMovetext(token)))) {
                pending = token;
                break;
            }
            if (!started) {
                if (tag || event) {
                    game.setLength(0);
                    started = true;
                } else if (!space && !comment) {
                    started = startsMovetext(token);
                    if (!started) {
                        game.setLength(0);
                        continue;
                    }
                }
            }
            if (started && !tag && !space) {
                movetext = true;
                if (first == '(') {
                    variationDepth++;
                } else if (first == ')' && variationDepth > 0) {
                    variationDepth--;
                } else if (variationDepth == 0 && Pgn.RESULT.matcher(token).matches()) {
                    ended = true;
                }
            }
            hasEventTag |= event;
            game.append(token);
        }
        return started ? game.toString() : null;
    }

    private static boolean startsMovetext(String token) {
        return MOVE_NUMBER.matcher(token).matches()
                || Pgn.RESULT.matcher(token).matches()
                || Pgn.isSan(token);
    }

    private String readToken() throws IOException {
        int first = take();
        if (first < 0) {
            return null;
        }
        StringBuilder token = new StringBuilder().append((char) first);
        switch (first) {
            case '[':
                readTag(token);
                break;
            case '{':
                while (peek() >= 0 && peek() != '}') {
                    token.append((char) take());
                }
                if (peek() == '}') {
                    token.append((char) take());
                }
                break;
            case ';':
                while (peek() >= 0 && peek() != '\r' && peek() != '\n') {
                    token.append((char) take());
                }
                break;
            case '(':
            case ')':
            case '}':
            case ']':
                break;
            default:
                boolean space = isSpace(first);
                while (peek() >= 0
                        && (space
                                ? isSpace(peek())
                                : !isSpace(peek()) && "(){}[];".indexOf(peek()) < 0)) {
                    token.append((char) take());
                }
        }
        return token.toString();
    }

    private void readTag(StringBuilder token) throws IOException {
        boolean quoted = false;
        boolean escaped = false;
        while (peek() >= 0 && (quoted || peek() != '[')) {
            char c = (char) take();
            token.append(c);
            if (escaped) {
                escaped = false;
            } else if (c == '\\' && quoted) {
                escaped = true;
            } else if (c == '"') {
                quoted = !quoted;
            } else if (c == ']' && !quoted) {
                return;
            }
        }
    }

    private static boolean isSpace(int c) {
        return c == '\uFEFF' || Character.isWhitespace(c);
    }

    private int peek() throws IOException {
        if (lookahead == -2) {
            lookahead = reader.read();
        }
        return lookahead;
    }

    private int take() throws IOException {
        int c = peek();
        lookahead = -2;
        return c;
    }
}
