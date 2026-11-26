package jwtc.android.chess.tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import jwtc.chess.PgnSplitter;

/** The games of a PGN file, or of every .pgn entry in a zip file. */
public final class PgnSource implements ImportSource {
    private final InputStream in;
    private final boolean zip;

    private PgnSource(InputStream in, boolean zip) {
        this.in = in;
        this.zip = zip;
    }

    public static PgnSource file(InputStream in) {
        return new PgnSource(in, false);
    }

    public static PgnSource zip(InputStream in) {
        return new PgnSource(in, true);
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    @Override
    public void forEach(Sink sink) throws IOException {
        try (InputStream stream = in) {
            if (!zip) {
                readGames(new InputStreamReader(stream, StandardCharsets.UTF_8), null, sink);
                return;
            }
            ZipInputStream zis = new ZipInputStream(stream);
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()
                        || !entry.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".pgn")) {
                    continue;
                }
                // the reader is not closed, that would close the zip stream with the entries still
                // to read
                if (!readGames(
                        new InputStreamReader(zis, StandardCharsets.UTF_8),
                        entry.getName(),
                        sink)) {
                    return;
                }
            }
        }
    }

    interface FileSink {
        boolean accept(File file, String sourceName) throws Exception;
    }

    /**
     * Spools one entry at a time so it can be scanned and reread without retaining it in memory.
     */
    void forEachFile(File directory, BooleanSupplier cancelled, FileSink sink) throws Exception {
        try (InputStream stream = in) {
            if (!zip) {
                visitFile(stream, directory, null, cancelled, sink);
                return;
            }
            ZipInputStream zis = new ZipInputStream(stream);
            ZipEntry entry;
            while (!cancelled.getAsBoolean() && (entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()
                        && entry.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".pgn")
                        && !visitFile(zis, directory, entry.getName(), cancelled, sink)) {
                    return;
                }
            }
        }
    }

    private static boolean visitFile(
            InputStream input,
            File directory,
            String sourceName,
            BooleanSupplier cancelled,
            FileSink sink)
            throws Exception {
        File file = File.createTempFile("pgn-import-", ".pgn", directory);
        try {
            try (FileOutputStream output = new FileOutputStream(file)) {
                byte[] buffer = new byte[8192];
                int length;
                while (!cancelled.getAsBoolean() && (length = input.read(buffer)) != -1) {
                    output.write(buffer, 0, length);
                }
            }
            return !cancelled.getAsBoolean() && sink.accept(file, sourceName);
        } finally {
            file.delete();
        }
    }

    /**
     * @return false when the sink asked to stop
     */
    private static boolean readGames(Reader reader, String sourceName, Sink sink)
            throws IOException {
        PgnSplitter splitter = new PgnSplitter(reader);
        int position = 0;
        String game;
        while ((game = splitter.next()) != null) {
            if (!sink.accept(new ImportItem(game, null, sourceName, ++position))) {
                return false;
            }
        }
        return true;
    }
}
