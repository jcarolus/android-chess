package jwtc.chess;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class PgnExportTest {
    private static final String GAME = "[Event \"Zürich\"]\n\n1. e4 e5 {één} (1... c5) *";

    @Test
    public void appendPreservesExistingGameAndSeparatesUnterminatedComment() throws Exception {
        String existing = "[Event \"Existing\"]\n\n1. d4 d5 * ; final comment";
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.write(existing.getBytes(StandardCharsets.UTF_8));
        PgnExport.copy(input(GAME), file, true);
        String contents = file.toString("UTF-8");
        assertTrue(contents.startsWith(existing));
        PgnSplitter splitter = new PgnSplitter(new StringReader(contents));
        assertEquals(existing, splitter.next().trim());
        assertEquals(GAME, splitter.next().trim());
        assertNull(splitter.next());
    }

    @Test
    public void repeatedAppendKeepsDuplicatesAndAnnotations() throws Exception {
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        PgnExport.copy(input(GAME), file, false);
        PgnExport.copy(input(GAME), file, true);
        PgnSplitter splitter = new PgnSplitter(new StringReader(file.toString("UTF-8")));
        assertEquals(GAME, splitter.next().trim());
        assertEquals(GAME, splitter.next().trim());
        assertNull(splitter.next());
    }

    @Test
    public void manyGamesRetainTheirOrderAndUnicodeAcrossCopyBuffers() throws Exception {
        ByteArrayOutputStream snapshot = new ByteArrayOutputStream();
        for (int i = 0; i < 500; i++) {
            PgnExport.writeGame(snapshot, GAME.replace("Zürich", "Zürich " + i));
        }
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        PgnExport.copy(new ByteArrayInputStream(snapshot.toByteArray()), file, false);
        PgnSplitter splitter = new PgnSplitter(new StringReader(file.toString("UTF-8")));
        for (int i = 0; i < 500; i++) {
            assertEquals(GAME.replace("Zürich", "Zürich " + i), splitter.next().trim());
        }
        assertNull(splitter.next());
    }

    @Test
    public void emptySnapshotFailsWithoutWritingAnAppendSeparator() {
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        assertThrows(IOException.class, () -> PgnExport.copy(input(""), file, true));
        assertEquals(0, file.size());
    }

    @Test
    public void writeFailureIsPropagatedWithoutRetrying() {
        IOException failure = new IOException("Full destination");
        OutputStream file =
                new OutputStream() {
                    @Override
                    public void write(int value) throws IOException {
                        throw failure;
                    }
                };
        assertSame(
                failure,
                assertThrows(IOException.class, () -> PgnExport.copy(input(GAME), file, false)));
    }

    private static ByteArrayInputStream input(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
