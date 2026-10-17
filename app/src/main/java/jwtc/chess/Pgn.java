package jwtc.chess;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads and writes the textual structure of PGN. Legality of moves is decided elsewhere, against a board. */
public final class Pgn {
    public static final Pattern MOVE = Pattern.compile("(K|Q|R|B|N)?(a|b|c|d|e|f|g|h)?(1|2|3|4|5|6|7|8)?(x)?(a|b|c|d|e|f|g|h)(1|2|3|4|5|6|7|8)(=Q|=R|=B|=N)?(@[a-h][1-8])?(\\+|#)?([\\?\\!]*)?[\\s]*");
    public static final Pattern CASTLING = Pattern.compile("(O\\-O\\-O|O\\-O)(@[a-h][1-8])?(\\+|#)?([\\?\\!]*)?");
    public static final Pattern RESULT = Pattern.compile("((\\*)|(1-0)|(0-1)|(1/2-1/2))");

    private static final Pattern TAG = Pattern.compile("\\[([A-Za-z0-9_]+)\\s+\"((?:\\\\.|[^\"\\\\])*)\"\\s*\\]");
    private static final String[] GLYPHS = {"", "!", "?", "!!", "??", "!?", "?!"};
    private static final String[] ROSTER = {"Event", "Site", "Date", "Round", "White", "Black", "Result"};
    private static final int MAX_VARIATION_DEPTH = 128;

    private Pgn() {}

    private static final class MoveBuilder {
        final String san;
        final int offset;
        final List<Integer> nags = new ArrayList<>();
        final List<List<MoveBuilder>> variations = new ArrayList<>();
        String comment = "";
        String leadingComment = "";

        MoveBuilder(String san, int offset) {
            this.san = san;
            this.offset = offset;
        }
    }

    private static final class Frame {
        final MoveBuilder resume;
        final List<MoveBuilder> line = new ArrayList<>();
        boolean hasMove;
        String leadingComment = "";

        Frame(MoveBuilder resume) { this.resume = resume; }
    }

    /** True when the text has the shape of a SAN move or castling, with or without annotation glyphs. */
    public static boolean isSan(String text) {
        String trimmed = text.trim();
        return MOVE.matcher(trimmed).matches() || CASTLING.matcher(trimmed.replace('0', 'O')).matches();
    }

    /**
     * Strict parse of one game. Throws {@link PgnSyntaxError} for anything not well formed: bad tags, unbalanced or
     * misplaced variations, annotations without a move, tokens after the result, disagreeing results.
     */
    public static PgnDocument parse(String text) {
        PGNTokenizer lexer = new PGNTokenizer(text);
        PGNTokenizer.Token token = lexer.next();
        Map<String, String> tags = new LinkedHashMap<>();
        while (token.kind == PGNTokenizer.Kind.TAG) {
            Matcher tag = TAG.matcher(token.text);
            if (!tag.matches()) throw new PgnSyntaxError("Invalid tag", token.offset);
            tags.put(tag.group(1), unescape(tag.group(2)));
            token = lexer.next();
        }

        List<MoveBuilder> mainLine = new ArrayList<>();
        Deque<Frame> stack = new ArrayDeque<>();
        MoveBuilder current = null;
        String rootComment = "";
        String result = null;
        boolean ended = false;

        for (; token.kind != PGNTokenizer.Kind.END; token = lexer.next()) {
            if (ended && token.kind != PGNTokenizer.Kind.COMMENT)
                throw new PgnSyntaxError("Unexpected token after result", token.offset);
            switch (token.kind) {
                case NUMBER:
                    break;
                case COMMENT:
                    if (!stack.isEmpty() && !stack.peek().hasMove) {
                        Frame frame = stack.peek();
                        frame.leadingComment = join(frame.leadingComment, token.text);
                    } else if (current == null) {
                        rootComment = join(rootComment, token.text);
                    } else {
                        current.comment = join(current.comment, token.text);
                    }
                    break;
                case OPEN:
                    if (stack.size() >= MAX_VARIATION_DEPTH || current == null
                        || !stack.isEmpty() && !stack.peek().hasMove)
                        throw new PgnSyntaxError("Variation must follow a move (maximum nesting "
                            + MAX_VARIATION_DEPTH + ")", token.offset);
                    stack.push(new Frame(current));
                    current = null;
                    break;
                case CLOSE:
                    if (stack.isEmpty() || !stack.peek().hasMove)
                        throw new PgnSyntaxError("Unmatched or empty variation", token.offset);
                    Frame closed = stack.pop();
                    closed.resume.variations.add(closed.line);
                    current = closed.resume;
                    break;
                case NAG:
                    if (!stack.isEmpty() && !stack.peek().hasMove)
                        throw new PgnSyntaxError("NAG must follow a move", token.offset);
                    current = requireMove(current, token);
                    current.nags.add(parseNag(token));
                    break;
                case SYMBOL:
                    if (RESULT.matcher(token.text).matches()) {
                        if (!stack.isEmpty()) throw new PgnSyntaxError("Result inside variation", token.offset);
                        result = token.text;
                        ended = true;
                        break;
                    }
                    String san = token.text.replaceAll("[!?]+$", "");
                    int glyph = Arrays.asList(GLYPHS).indexOf(token.text.substring(san.length()));
                    if (glyph < 0 || !isSan(san))
                        throw new PgnSyntaxError("Illegal or unsupported move: " + token.text, token.offset);
                    MoveBuilder move = new MoveBuilder(san, token.offset);
                    if (glyph > 0) move.nags.add(glyph);
                    if (stack.isEmpty()) {
                        mainLine.add(move);
                    } else {
                        Frame frame = stack.peek();
                        frame.line.add(move);
                        if (!frame.hasMove) {
                            frame.hasMove = true;
                            move.leadingComment = frame.leadingComment;
                        }
                    }
                    current = move;
                    break;
                default:
                    throw new PgnSyntaxError("Unexpected token: " + token.text, token.offset);
            }
        }
        if (!stack.isEmpty()) throw new PgnSyntaxError("Unclosed variation at end of PGN", text.length());

        String headerResult = tags.get("Result");
        if (headerResult != null && !RESULT.matcher(headerResult).matches())
            throw new PgnSyntaxError("Invalid Result tag");
        if (result != null && headerResult != null && !result.equals(headerResult))
            throw new PgnSyntaxError("Result tag and movetext disagree");

        return new PgnDocument(Collections.unmodifiableMap(tags), rootComment, freeze(mainLine),
            result != null ? result : headerResult);
    }

    /**
     * Lenient read of the tag block at the start of a game. Never throws; unrecognised text before the first tag is
     * skipped, and reading stops at the first thing after the tags that is not a tag.
     */
    public static Map<String, String> readTags(String text) {
        Map<String, String> tags = new LinkedHashMap<>();
        if (text == null) return tags;
        Matcher matcher = TAG.matcher(text);
        int pos = 0;
        while (pos < text.length()) {
            if (Character.isWhitespace(text.charAt(pos)) || text.charAt(pos) == '﻿') {
                pos++;
            } else if (matcher.region(pos, text.length()).lookingAt()) {
                tags.put(matcher.group(1), unescape(matcher.group(2)));
                pos = matcher.end();
            } else if (tags.isEmpty()) {
                pos++;
            } else {
                break;
            }
        }
        return tags;
    }

    /** The tag block: the seven roster tags first (missing ones as "?"), then the others by name. */
    public static String writeTags(Map<String, String> tags) {
        StringBuilder out = new StringBuilder();
        for (String name : ROSTER) appendTag(out, name, tags.getOrDefault(name, "?"));
        List<String> others = new ArrayList<>(tags.keySet());
        Collections.sort(others);
        for (String name : others) {
            if (!Arrays.asList(ROSTER).contains(name)) appendTag(out, name, tags.get(name));
        }
        return out.toString();
    }

    private static void appendTag(StringBuilder out, String name, String value) {
        if (value == null) return;
        out.append('[').append(name).append(" \"")
            .append(value.replace("\\", "\\\\").replace("\"", "\\\"").replace('\n', ' ').replace('\r', ' '))
            .append("\"]\n");
    }

    private static String unescape(String value) {
        if (value.indexOf('\\') < 0) return value;
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length() && (value.charAt(i + 1) == '"' || value.charAt(i + 1) == '\\')) {
                c = value.charAt(++i);
            }
            out.append(c);
        }
        return out.toString();
    }

    private static MoveBuilder requireMove(MoveBuilder current, PGNTokenizer.Token token) {
        if (current == null) throw new PgnSyntaxError("Invalid NAG", token.offset);
        return current;
    }

    private static int parseNag(PGNTokenizer.Token token) {
        try {
            int nag = Integer.parseInt(token.text);
            if (nag >= 0 && nag <= 255) return nag;
        } catch (NumberFormatException ignored) {
        }
        throw new PgnSyntaxError("Invalid NAG", token.offset);
    }

    private static String join(String first, String second) {
        return first.isEmpty() ? second : first + "\n" + second;
    }

    private static List<PgnDocument.Move> freeze(List<MoveBuilder> line) {
        List<PgnDocument.Move> moves = new ArrayList<>(line.size());
        for (MoveBuilder b : line) {
            List<List<PgnDocument.Move>> variations = new ArrayList<>(b.variations.size());
            for (List<MoveBuilder> variation : b.variations) variations.add(freeze(variation));
            moves.add(new PgnDocument.Move(b.san, b.offset, Collections.unmodifiableList(b.nags), b.comment,
                b.leadingComment, Collections.unmodifiableList(variations)));
        }
        return Collections.unmodifiableList(moves);
    }
}
