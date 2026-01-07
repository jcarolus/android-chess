package jwtc.android.chess.helpers;

public class StartItem {
    public int iconResourceId;
    public int textResourceId;
    public  Class<?> cls;

    public StartItem(int iconResourceId, int textResourceId, Class<?> cls) {
        this.iconResourceId = iconResourceId;
        this.textResourceId = textResourceId;
        this.cls = cls;
    }
}
