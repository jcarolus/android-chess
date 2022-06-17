package jwtc.android.chess.constants;

public class ColorSchemes {
    private static int[][] colorScheme = new int[6][3];
    public static int selectedColorScheme = 0;
    
    static {
        // yellow
        colorScheme[0][0] = 0xffdeac5d;
        colorScheme[0][1] = 0xfff9e3c0;
        colorScheme[0][2] = 0xfff3ed4b;

        // blue
        colorScheme[1][0] = 0xff28628b;
        colorScheme[1][1] = 0xff7dbdea;
        colorScheme[1][2] = 0xff9fdef3;

        // green
        colorScheme[2][0] = 0xff6a8974;
        colorScheme[2][1] = 0xff8eb59b;
        colorScheme[2][2] = 0xff98e5ab;

        // grey
        colorScheme[3][0] = 0xffc0c0c0;
        colorScheme[3][1] = 0xffffffff;
        colorScheme[3][2] = 0xfff3ed4b;

        // brown
        colorScheme[4][0] = 0xff704d33;
        colorScheme[4][1] = 0xffaf701d;
        colorScheme[4][2] = 0xfff3ed4b;
        // 347733
        // red
        colorScheme[5][0] = 0xffc85252;
        colorScheme[5][1] = 0xffd09a9a;
        colorScheme[5][2] = 0x00f3ed4b;
    }

    public static int getLight() {
        return colorScheme[selectedColorScheme][1];
    }

    public static int getDark() {
        return colorScheme[selectedColorScheme][0];
    }

    public static int getHightlightColor() {
        return 0xffff0000;
    }

    public static int getSelectedColor() {
        return colorScheme[selectedColorScheme][2];
    }
}
