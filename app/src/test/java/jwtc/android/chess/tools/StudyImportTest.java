package jwtc.android.chess.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class StudyImportTest {
    private static final String PGN =
            "[Event \"First\"]\n[StudyTitle \"Existing title\"]\n\n1. e4 *\n\n"
                    + "[Event \"Second\"]\n[Topic \"Existing topic\"]\n\n1. d4 *";

    @Test
    public void asksOnceForBothMissingFieldsBeforeDeliveringChapters() throws Exception {
        List<String> events = new ArrayList<>();
        StudyImport study =
                new StudyImport(
                        null,
                        "lesson.pgn",
                        () -> source(PGN),
                        (filename, defaults) -> {
                            assertTrue(defaults.needsTitle);
                            assertTrue(defaults.needsTopic);
                            events.add(filename);
                            return true;
                        });
        study.forEach(item -> events.add(Integer.toString(item.sourcePosition)));
        assertEquals(List.of("lesson.pgn", "1", "2"), events);
    }

    @Test
    public void cancellingMetadataLeavesTheImportUnwritten() throws Exception {
        StudyImport study =
                new StudyImport(null, "lesson.pgn", () -> source(PGN), (filename, defaults) -> false);
        List<ImportItem> chapters = new ArrayList<>();
        study.forEach(chapters::add);
        assertTrue(chapters.isEmpty());
    }

    private static PgnSource source(String pgn) {
        return PgnSource.file(new ByteArrayInputStream(pgn.getBytes(StandardCharsets.UTF_8)));
    }
}
