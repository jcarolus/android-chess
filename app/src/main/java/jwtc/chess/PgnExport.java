package jwtc.chess;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Writes UTF-8 Games with blank lines separating adjacent PGNs. */
public final class PgnExport {
    private PgnExport() {}

    public static void writeGame(OutputStream output, String pgn) throws IOException {
        output.write(pgn.getBytes(StandardCharsets.UTF_8));
        output.write('\n');
        output.write('\n');
    }

    public static void copy(InputStream input, OutputStream output, boolean append)
            throws IOException {
        byte[] buffer = new byte[8192];
        int read = input.read(buffer);
        if (read == -1) {
            throw new IOException("No games to export");
        }
        if (append) {
            output.write('\n');
            output.write('\n');
        }
        do {
            output.write(buffer, 0, read);
        } while ((read = input.read(buffer)) != -1);
    }
}
