package jwtc.chess;

/** A sequential lexer: punctuation inside comments and quoted tags is never treated as movetext. */
final class PGNTokenizer {
    public enum Kind { TAG, COMMENT, OPEN, CLOSE, NUMBER, NAG, SYMBOL, END }

    public static final class Token {
        public final Kind kind;
        public final String text;
        public final int offset;
        Token(Kind kind, String text, int offset) {
            this.kind = kind;
            this.text = text;
            this.offset = offset;
        }
    }

    private final String input;
    private int cursor;

    public PGNTokenizer(String input) { this.input = input; }

    public Token next() {
        while (cursor < input.length() && (Character.isWhitespace(input.charAt(cursor))
            || input.charAt(cursor) == '\uFEFF')) cursor++;
        int start = cursor;
        if (cursor == input.length()) return new Token(Kind.END, "", cursor);
        char ch = input.charAt(cursor++);
        if (ch == '(') return new Token(Kind.OPEN, "(", start);
        if (ch == ')') return new Token(Kind.CLOSE, ")", start);
        if (ch == '{' || ch == ';') {
            int body = cursor;
            while (cursor < input.length() && (ch == '{' ? input.charAt(cursor) != '}'
                : input.charAt(cursor) != '\n' && input.charAt(cursor) != '\r')) cursor++;
            String text = input.substring(body, cursor);
            if (ch == '{') {
                if (cursor == input.length()) throw error("Unclosed comment", start);
                cursor++;
            }
            return new Token(Kind.COMMENT, text, start);
        }
        if (ch == '[') {
            boolean quoted = false, escaped = false;
            while (cursor < input.length()) {
                char c = input.charAt(cursor++);
                if (escaped) escaped = false;
                else if (c == '\\' && quoted) escaped = true;
                else if (c == '"') quoted = !quoted;
                else if (c == ']' && !quoted) return new Token(Kind.TAG, input.substring(start, cursor), start);
            }
            throw error("Unclosed tag", start);
        }
        if (Character.isDigit(ch)) {
            while (cursor < input.length() && Character.isDigit(input.charAt(cursor))) cursor++;
            if (cursor < input.length() && input.charAt(cursor) == '.') {
                while (cursor < input.length() && input.charAt(cursor) == '.') cursor++;
                return new Token(Kind.NUMBER, input.substring(start, cursor), start);
            }
        }
        if (ch == '.') {
            while (cursor < input.length() && input.charAt(cursor) == '.') cursor++;
            return new Token(Kind.NUMBER, input.substring(start, cursor), start);
        }
        if (ch == '$') {
            while (cursor < input.length() && Character.isDigit(input.charAt(cursor))) cursor++;
            return new Token(Kind.NAG, input.substring(start + 1, cursor), start);
        }
        while (cursor < input.length() && !Character.isWhitespace(input.charAt(cursor))
            && "(){}[];$".indexOf(input.charAt(cursor)) < 0) cursor++;
        return new Token(Kind.SYMBOL, input.substring(start, cursor), start);
    }

    static PgnSyntaxError error(String message, int offset) {
        return new PgnSyntaxError(message, offset);
    }
}
