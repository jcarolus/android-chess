package jwtc.android.chess.helpers;

import android.util.Log;

import java.io.InputStream;

public class PGNHelper {

    public static String getPGNFromInputStream(InputStream is) throws Exception {
        StringBuilder sb = new StringBuilder();
        byte[] b = new byte[4096];
        int bytesRead;

        while ((bytesRead = is.read(b)) > 0) {
            sb.append(new String(b, 0, bytesRead));
        }
        is.close();

        return sb.toString().trim();
    }
}
