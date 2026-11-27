package jwtc.android.chess.lichess.models;

import com.google.gson.annotations.SerializedName;
import jwtc.chess.Variant;

/** A Lichess variant as the API sends it; {@link #key} names the rule set. */
public class LichessVariant {
    public String key;
    public String name;

    @SerializedName("short")
    public String shortName;

    /** The app's Variant for a Lichess variant key, or null for rule sets the app lacks. */
    public static Variant toVariant(String key) {
        if (key == null) {
            return null;
        }
        switch (key) {
            case "standard":
                return Variant.STANDARD;
            case "chess960":
                return Variant.CHESS_960;
            case "kingOfTheHill":
                return Variant.KING_OF_THE_HILL;
            default:
                return null;
        }
    }

    /** Whether the app can play games of this Lichess variant key on Lichess. */
    public static boolean isSupported(String key) {
        Variant variant = toVariant(key);
        return variant != null && variant.supportsLichess();
    }
}
