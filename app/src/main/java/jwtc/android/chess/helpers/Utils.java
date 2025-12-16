package jwtc.android.chess.helpers;

public class Utils {
    public static int parseInt(String in, int defaultValue) {
        try {
            return Integer.parseInt(in);
        } catch(NumberFormatException ex) {
            return defaultValue;
        }
    }
}
