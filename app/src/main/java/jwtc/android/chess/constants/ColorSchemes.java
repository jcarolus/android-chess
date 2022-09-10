package jwtc.android.chess.constants;

import jwtc.android.chess.R;

public class ColorSchemes {
    private static int[][] colorScheme = new int[9][4];
    public static int selectedColorScheme = 0;
    public static boolean showCoords = false;
    public static boolean isRotated = false; // not ideal
    public static int selectedPattern = 0;
    
    static {
        // yellow
        colorScheme[0][0] = 0xffdeac5d;
        colorScheme[0][1] = 0xfff9e3c0;
        colorScheme[0][2] = 0xfff3ed4b;
        colorScheme[0][3] = 0xcc000000;

        // blue
        colorScheme[1][0] = 0xff6e98da;
        colorScheme[1][1] = 0xffa8e8ff;
        colorScheme[1][2] = 0xffcdedf7;
        colorScheme[1][3] = 0xccffffff;

        // green - ,
        colorScheme[2][0] = 0xff769655;
        colorScheme[2][1] = 0xffeeeed2;
        colorScheme[2][2] = 0xff98e5ab;
        colorScheme[2][3] = 0xccffffff;

        // grey
        colorScheme[3][0] = 0xffc0c0c0;
        colorScheme[3][1] = 0xffffffff;
        colorScheme[3][2] = 0xfff3ed4b;
        colorScheme[3][3] = 0xcc000000;

        // brown
        colorScheme[4][0] = 0xffab6e41;
        colorScheme[4][1] = 0xffd6a47e;
        colorScheme[4][2] = 0xfff3ed4b;
        colorScheme[4][3] = 0xccffffff;

        // red
        colorScheme[5][0] = 0xfffc4e4e;
        colorScheme[5][1] = 0xfffca9a9;
        colorScheme[5][2] = 0xfff3ed4b;
        colorScheme[5][3] = 0xccffffff;

        // orange
        colorScheme[6][0] = 0xfffc9432;
        colorScheme[6][1] = 0xfffab778;
        colorScheme[6][2] = 0xfff3ed4b;
        colorScheme[6][3] = 0xccffffff;

        // pink
        colorScheme[7][0] = 0xfff55fe3;
        colorScheme[7][1] = 0xfffc9ff1;
        colorScheme[7][2] = 0xfff3ed4b;
        colorScheme[7][3] = 0xccffffff;

        // purple
        colorScheme[8][0] = 0xff805ad5;
        colorScheme[8][1] = 0xffac8eed;
        colorScheme[8][2] = 0xfffcd2f7;
        colorScheme[8][3] = 0xccffffff;
    }

    public static int getLight() {
        return colorScheme[selectedColorScheme][1];
    }

    public static int getDark() {
        return colorScheme[selectedColorScheme][0];
    }

    public static int getHightlightColor() {
        return colorScheme[selectedColorScheme][3];
    }

    public static int getSelectedColor() {
        return colorScheme[selectedColorScheme][2];
    }

    public static int getSelectedPatternDrawable() {
        switch (selectedPattern) {
            case 1: return R.drawable.square_single_shade;
            case 2: return R.drawable.square_double_shade;
        }

        return 0;
    }
}
