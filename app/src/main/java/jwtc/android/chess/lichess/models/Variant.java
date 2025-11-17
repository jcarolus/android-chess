package jwtc.android.chess.lichess.models;

import com.google.gson.annotations.SerializedName;

public class Variant {
    public String key;
    public String name;

    @SerializedName("short")
    public String shortName;
}
