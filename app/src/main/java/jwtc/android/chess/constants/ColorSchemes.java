package jwtc.android.chess.constants;

public class ColorSchemes {
    private static int[][] colorScheme = new int[6][3];
    public static int selectedColorScheme = 0;
    
    static {
        // yellow
        colorScheme[0][0] = 0xffdeac5d;
        colorScheme[0][1] = 0xfff9e3c0;
        colorScheme[0][2] = 0xccf3ed4b;

        // blue
        colorScheme[1][0] = 0xff28628b;
        colorScheme[1][1] = 0xff7dbdea;
        colorScheme[1][2] = 0xcc9fdef3;

        // green
        colorScheme[2][0] = 0xff8eb59b;
        colorScheme[2][1] = 0xffcae787;
        colorScheme[2][2] = 0xcc9ff3b4;

        // grey
        colorScheme[3][0] = 0xffc0c0c0;
        colorScheme[3][1] = 0xffffffff;
        colorScheme[3][2] = 0xccf3ed4b;

        // brown
        colorScheme[4][0] = 0xff65390d; //4c2b0a
        colorScheme[4][1] = 0xffb98b4f;
        colorScheme[4][2] = 0xccf3ed4b;
        // 347733
        // red
        colorScheme[5][0] = 0xffff2828;
        colorScheme[5][1] = 0xffffd1d1;
        colorScheme[5][2] = 0xccf3ed4b;
    }

    public static int getLight() {
        return colorScheme[selectedColorScheme][1];
    }

    public static int getDark() {
        return colorScheme[selectedColorScheme][0];
    }

    public static int getHightlight() {
        return colorScheme[selectedColorScheme][2];
    }
}
