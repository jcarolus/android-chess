package jwtc.chess;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class PgnSplitterTest {
    private static final String GAME_1 = "[Event \"One\"]\n[White \"A\"]\n\n1. e4 e5 1-0\n\n";
    private static final String GAME_2 = "[Event \"Two\"]\n[White \"B\"]\n\n1. d4 d5 0-1\n\n";
    private static final String GAME_3 = "[Event \"Three\"]\n\n1. c4 *";

    /** A reader that hands out a single character per read, to put every boundary on a chunk edge. */
    private static final class OneCharReader extends Reader {
        private final String text;
        private int pos;

        OneCharReader(String text) { this.text = text; }

        @Override
        public int read(char[] buffer, int offset, int length) {
            if (pos >= text.length()) return -1;
            buffer[offset] = text.charAt(pos++);
            return 1;
        }

        @Override
        public void close() {}
    }

    private static final class OneByteStream extends InputStream {
        private final ByteArrayInputStream source;

        OneByteStream(byte[] bytes) { source = new ByteArrayInputStream(bytes); }

        @Override
        public int read() { return source.read(); }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            return length == 0 ? 0 : source.read(buffer, offset, 1);
        }
    }

    private static List<String> split(Reader reader) throws IOException {
        PgnSplitter splitter = new PgnSplitter(reader);
        List<String> games = new ArrayList<>();
        for (String game = splitter.next(); game != null; game = splitter.next()) games.add(game);
        return games;
    }

    private static List<String> split(String text) throws IOException {
        return split(new StringReader(text));
    }

    @Test
    public void aSingleGameIsReturned() throws IOException {
        List<String> games = split(GAME_1);
        assertEquals(1, games.size());
        assertEquals(GAME_1, games.get(0));
    }

    @Test
    public void severalGamesAreReturnedInOrder() throws IOException {
        List<String> games = split(GAME_1 + GAME_2 + GAME_3);
        assertEquals(3, games.size());
        assertEquals(GAME_1, games.get(0));
        assertEquals(GAME_2, games.get(1));
        assertEquals(GAME_3, games.get(2));
    }

    @Test
    public void theLastGameIsReturnedWithoutATrailingNewline() throws IOException {
        List<String> games = split(GAME_1 + GAME_3);
        assertEquals(GAME_3, games.get(1));
    }

    @Test
    public void textBeforeTheFirstGameIsSkipped() throws IOException {
        List<String> games = split("﻿; exported by x\n\n" + GAME_1);
        assertEquals(1, games.size());
        assertEquals(GAME_1, games.get(0));
    }

    @Test
    public void textWithoutAnEventTagHasNoGames() throws IOException {
        assertEquals(0, split("[White \"A\"]\n\n1. e4 e5 1-0\n").size());
        assertEquals(0, split("").size());
        assertEquals(0, split("[Event").size());
    }

    @Test
    public void aGameWithoutAnEventTagStaysWithThePreviousGame() throws IOException {
        String noEvent = "[White \"B\"]\n\n1. d4 d5 0-1\n";
        List<String> games = split(GAME_1 + noEvent + GAME_3);
        assertEquals(2, games.size());
        assertEquals(GAME_1 + noEvent, games.get(0));
    }

    @Test
    public void lineEndingsAreKept() throws IOException {
        String crlf = "[Event \"One\"]\r\n\r\n1. e4 *\r\n\r\n";
        assertEquals(crlf, split(crlf + GAME_2).get(0));
    }

    @Test
    public void nextKeepsReturningNullAtTheEnd() throws IOException {
        PgnSplitter splitter = new PgnSplitter(new StringReader(GAME_1));
        splitter.next();
        assertNull(splitter.next());
        assertNull(splitter.next());
    }

    @Test
    public void aStartMarkerSplitAcrossReadsIsStillFound() throws IOException {
        List<String> games = split(new OneCharReader("junk [Even" + GAME_1 + GAME_2 + GAME_3));
        assertEquals(3, games.size());
        assertEquals(GAME_1, games.get(0));
        assertEquals(GAME_2, games.get(1));
        assertEquals(GAME_3, games.get(2));
    }

    @Test
    public void gamesLargerThanOneReadAreKeptWhole() throws IOException {
        StringBuilder moves = new StringBuilder();
        for (int i = 0; i < 5000; i++) moves.append("{comment ").append(i).append("} ");
        String big = "[Event \"Big\"]\n\n1. e4 " + moves + "*\n\n";
        List<String> games = split(big + GAME_2);
        assertEquals(2, games.size());
        assertEquals(big, games.get(0));
        assertEquals(GAME_2, games.get(1));
    }

    @Test
    public void multibyteCharactersSurviveAnyChunking() throws IOException {
        String text = "[Event \"Ünïcödé\"]\n[White \"Jörg Müller\"]\n[Black \"李小龙\"]\n\n1. e4 *\n\n"
            + "[Event \"Zwei\"]\n[White \"Søren\"]\n\n1. d4 *\n";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        List<String> games = split(new InputStreamReader(new OneByteStream(bytes), StandardCharsets.UTF_8));
        assertEquals(2, games.size());
        assertEquals(text, games.get(0) + games.get(1));
        assertEquals("[Event \"Ünïcödé\"]\n[White \"Jörg Müller\"]\n[Black \"李小龙\"]\n\n1. e4 *\n\n", games.get(0));
    }
}
