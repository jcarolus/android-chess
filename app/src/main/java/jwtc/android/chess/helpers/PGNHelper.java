package jwtc.android.chess.helpers;

import android.util.Log;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PGNHelper {


    public static String getPGNFromInputStream(InputStream is) throws Exception {
        String sPGN = "";

        byte[] b = new byte[4096];

        while (is.read(b) > 0) {
            sPGN += new String(b);
        }
        is.close();

        return sPGN.trim();
    }

    public static Date getDate(String s) {
        if (s != null) {
            Pattern patTag = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");
            Matcher match = patTag.matcher(s);
            if (match.matches()) {
                try {
                    SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd");
                    Date utilDate = formatter.parse(s);
                    return utilDate;
                } catch (Exception ex) {
                }
            } else {

                // in case it's a YYYY.mm.?? format, we make it fst of the month
                patTag = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\?\\?)");
                match = patTag.matcher(s);
                if (match.matches()) {
                    try {
                        s = match.group(1) + "." + match.group(2) + ".01";
                        SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd");
                        Date utilDate = formatter.parse(s);
                        return utilDate;
                    } catch (Exception ex) {
                    }
                } else {
                    // in case it's a YYYY.??.?? format, we make it January 1
                    patTag = Pattern.compile("(\\d+)\\.(\\?\\?)\\.(\\?\\?)");
                    match = patTag.matcher(s);
                    if (match.matches()) {
                        try {
                            s = match.group(1) + ".01.01";
                            SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd");
                            Date utilDate = formatter.parse(s);
                            return utilDate;
                        } catch (Exception ex) {
                        }
                    }
                }
            }
        }
        return null;
    }
}
