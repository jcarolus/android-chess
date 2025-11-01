package jwtc.android.chess.constants;

import jwtc.android.chess.R;

public class ColorSchemes {
    private static int[][] colorScheme = new int[9][4];
    public static int selectedColorScheme = 0;
    public static boolean showCoords = false;
    public static boolean isRotated = false; // not ideal
    public static int selectedPattern = 0;
    
    static {
        colorScheme[0][0] = 0xeeFAAE2F;
        colorScheme[0][1] = 0xeeFFCC78;
        colorScheme[0][2] = 0xffFFE1B0;
        colorScheme[0][3] = 0xffFFE1B0;

        colorScheme[1][0] = 0xee629EFC;
        colorScheme[1][1] = 0xee93BBFA;
        colorScheme[1][2] = 0xffCDEDF7;
        colorScheme[1][3] = 0xffCDEDF7;

        colorScheme[2][0] = 0xee488C1D;
        colorScheme[2][1] = 0xee71B04A;
        colorScheme[2][2] = 0xffB0E092;
        colorScheme[2][3] = 0xffB0E092;

        colorScheme[3][0] = 0xee444444;
        colorScheme[3][1] = 0xee777777;
        colorScheme[3][2] = 0xffCCCCCC;
        colorScheme[3][3] = 0xffCCCCCC;

        colorScheme[4][0] = 0xeeAD5F2F;
        colorScheme[4][1] = 0xeeBD8562;
        colorScheme[4][2] = 0xffFFC5A1;
        colorScheme[4][3] = 0xffFFC5A1;

        colorScheme[5][0] = 0xeeE3543B;
        colorScheme[5][1] = 0xeeF77159;
        colorScheme[5][2] = 0xffFFAB9C;
        colorScheme[5][3] = 0xffFFAB9C;

        colorScheme[6][0] = 0xeeFC9432;
        colorScheme[6][1] = 0xeeFCB26D;
        colorScheme[6][2] = 0xffFFC894;
        colorScheme[6][3] = 0xffFFC894;

        colorScheme[7][0] = 0xeeF55FE3;
        colorScheme[7][1] = 0xeeFA7FEB;
        colorScheme[7][2] = 0xffFFB3F6;
        colorScheme[7][3] = 0xffFFB3F6;

        colorScheme[8][0] = 0xee805ad5;
        colorScheme[8][1] = 0xeeac8eed;
        colorScheme[8][2] = 0xffFCD2F7;
        colorScheme[8][3] = 0xffFCD2F7;
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
            case 3: return R.drawable.diagonal_stripes;
        }

        return 0;
    }
}
