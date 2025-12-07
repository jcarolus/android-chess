package jwtc.android.chess.lichess.models;

import com.google.gson.annotations.SerializedName;

public class Variant {
    // we support "standard" and "chess960"
    public String key;
    public String name;

    @SerializedName("short")
    public String shortName;
}
