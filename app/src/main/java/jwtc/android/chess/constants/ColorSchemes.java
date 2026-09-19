package jwtc.android.chess.constants;

import androidx.core.graphics.ColorUtils;

import jwtc.android.chess.R;

public class ColorSchemes {

    // The CUSTOM_COLOR_SCHEME row only supplies [3] to [5]; its square colours
    // come from customDarkColor and customLightColor.
    public static final int CUSTOM_COLOR_SCHEME = 9;
    public static final int EINK_COLOR_SCHEME = 10; // Greyscale scheme for e-ink screens

    public static final int DARK_SQUARE = 0;
    public static final int LIGHT_SQUARE = 1;
    public static final int SELECTED_SQUARE = 2;
    public static final int HIGHLIGHT = 3;
    public static final int COORD_BACKGROUND = 4;
    public static final int COORD_TEXT = 5;


    // [color scheme index] x [color index]
    private static final int[][] colorScheme = new int[11][6];

    private static final int DEFAULT_HIGHLIGHT = 0x66ffff00;
    private static final int DEFAULT_COORD_BACKGROUND = 0x99ffffff;
    private static final int DEFAULT_COORD_TEXT = 0x99000000;

    public static int selectedColorScheme = 0;
    public static boolean showCoords = false;
    public static boolean isRotated = false; // not ideal
    public static int selectedPattern = 0;
    public static float saturationFactor = 1.0f;
    private static int customDarkColor = 0xffFAAE2F;
    private static int customLightColor = 0xffFFCC78;

    static {
        colorScheme[0][DARK_SQUARE] = 0xeeFAAE2F;
        colorScheme[0][LIGHT_SQUARE] = 0xeeFFCC78;
        colorScheme[0][SELECTED_SQUARE] = 0xffFFE1B0;
        colorScheme[0][HIGHLIGHT] = 0xffFFE1B0;
        colorScheme[0][COORD_BACKGROUND] = DEFAULT_COORD_BACKGROUND;
        colorScheme[0][COORD_TEXT] = DEFAULT_COORD_TEXT;


        colorScheme[1][DARK_SQUARE] = 0xee629EFC;
        colorScheme[1][LIGHT_SQUARE] = 0xee93BBFA;
        colorScheme[1][SELECTED_SQUARE] = 0xffCDEDF7;
        colorScheme[1][HIGHLIGHT] = 0xffCDEDF7;
        colorScheme[1][COORD_BACKGROUND] = DEFAULT_COORD_BACKGROUND;
        colorScheme[1][COORD_TEXT] = DEFAULT_COORD_TEXT;

        colorScheme[2][DARK_SQUARE] = 0xee488C1D;
        colorScheme[2][LIGHT_SQUARE] = 0xee71B04A;
        colorScheme[2][SELECTED_SQUARE] = 0xffB0E092;
        colorScheme[2][HIGHLIGHT] = 0xffB0E092;
        colorScheme[2][COORD_BACKGROUND] = DEFAULT_COORD_BACKGROUND;
        colorScheme[2][COORD_TEXT] = DEFAULT_COORD_TEXT;

        colorScheme[3][DARK_SQUARE] = 0xee444444;
        colorScheme[3][LIGHT_SQUARE] = 0xee777777;
        colorScheme[3][SELECTED_SQUARE] = 0xffCCCCCC;
        colorScheme[3][HIGHLIGHT] = 0xffCCCCCC;
        colorScheme[3][COORD_BACKGROUND] = DEFAULT_COORD_BACKGROUND;
        colorScheme[3][COORD_TEXT] = DEFAULT_COORD_TEXT;

        colorScheme[4][DARK_SQUARE] = 0xeeAD5F2F;
        colorScheme[4][LIGHT_SQUARE] = 0xeeBD8562;
        colorScheme[4][SELECTED_SQUARE] = 0xffFFC5A1;
        colorScheme[4][HIGHLIGHT] = 0xffFFC5A1;
        colorScheme[4][COORD_BACKGROUND] = DEFAULT_COORD_BACKGROUND;
        colorScheme[4][COORD_TEXT] = DEFAULT_COORD_TEXT;

        colorScheme[5][DARK_SQUARE] = 0xeeEB573D;
        colorScheme[5][LIGHT_SQUARE] = 0xeeF77159;
        colorScheme[5][SELECTED_SQUARE] = 0xffFFAB9C;
        colorScheme[5][HIGHLIGHT] = 0xffFFAB9C;
        colorScheme[5][COORD_BACKGROUND] = DEFAULT_COORD_BACKGROUND;
        colorScheme[5][COORD_TEXT] = DEFAULT_COORD_TEXT;

        colorScheme[6][DARK_SQUARE] = 0xeeFC9432;
        colorScheme[6][LIGHT_SQUARE] = 0xeeFCB26D;
        colorScheme[6][SELECTED_SQUARE] = 0xffFFC894;
        colorScheme[6][HIGHLIGHT] = 0xffFFC894;
        colorScheme[6][COORD_BACKGROUND] = DEFAULT_COORD_BACKGROUND;
        colorScheme[6][COORD_TEXT] = DEFAULT_COORD_TEXT;

        colorScheme[7][DARK_SQUARE] = 0xeeF55FE3;
        colorScheme[7][LIGHT_SQUARE] = 0xeeFA7FEB;
        colorScheme[7][SELECTED_SQUARE] = 0xffFFB3F6;
        colorScheme[7][HIGHLIGHT] = 0xffFFB3F6;
        colorScheme[7][COORD_BACKGROUND] = DEFAULT_COORD_BACKGROUND;
        colorScheme[7][COORD_TEXT] = DEFAULT_COORD_TEXT;

        colorScheme[8][DARK_SQUARE] = 0xee805ad5;
        colorScheme[8][LIGHT_SQUARE] = 0xeeac8eed;
        colorScheme[8][SELECTED_SQUARE] = 0xffFCD2F7;
        colorScheme[8][HIGHLIGHT] = 0xffFCD2F7;
        colorScheme[8][COORD_BACKGROUND] = DEFAULT_COORD_BACKGROUND;
        colorScheme[8][COORD_TEXT] = DEFAULT_COORD_TEXT;

        // initialized, but not all are used
        colorScheme[CUSTOM_COLOR_SCHEME][DARK_SQUARE] = 0xee805ad5;
        colorScheme[CUSTOM_COLOR_SCHEME][LIGHT_SQUARE] = 0xeeac8eed;
        colorScheme[CUSTOM_COLOR_SCHEME][SELECTED_SQUARE] = 0xffFCD2F7;
        colorScheme[CUSTOM_COLOR_SCHEME][HIGHLIGHT] = DEFAULT_HIGHLIGHT;
        colorScheme[CUSTOM_COLOR_SCHEME][COORD_BACKGROUND] = DEFAULT_COORD_BACKGROUND;
        colorScheme[CUSTOM_COLOR_SCHEME][COORD_TEXT] = DEFAULT_COORD_TEXT;

        // Greyscale scheme for e-ink displays.
        //
        // Squares are opaque (the colour schemes above are 0xee, which washes
        // out on a reflective panel) and sit at levels an e-ink controller can
        // hold cleanly. The dark square is deliberately a light grey (#bbbbbb,
        // one of the 16 panel levels) rather than anything darker: Alpha black
        // pieces are solid #101010 with no light outline, so they lose contrast
        // against a dark square. Kept in step with @color/einkBoardDark.
        colorScheme[EINK_COLOR_SCHEME][DARK_SQUARE] = 0xffbbbbbb; // dark square
        colorScheme[EINK_COLOR_SCHEME][LIGHT_SQUARE] = 0xffffffff; // light square
        colorScheme[EINK_COLOR_SCHEME][SELECTED_SQUARE] = 0xff4d4d4d; // selected square
        colorScheme[EINK_COLOR_SCHEME][HIGHLIGHT] = 0x40000000; // last-move wash, darkens either square
        colorScheme[EINK_COLOR_SCHEME][COORD_BACKGROUND] = 0xffffffff; // coordinate background, opaque
        colorScheme[EINK_COLOR_SCHEME][COORD_TEXT] = 0xff000000; // coordinate text, opaque

    }

    public static int getLight() {
        int color = selectedColorScheme == CUSTOM_COLOR_SCHEME
            ? customLightColor
            : colorScheme[selectedColorScheme][LIGHT_SQUARE];
        return desaturateColor(color, ColorSchemes.saturationFactor);
    }

    public static int getDark() {
        int color = selectedColorScheme == CUSTOM_COLOR_SCHEME
            ? customDarkColor
            : colorScheme[selectedColorScheme][DARK_SQUARE];
        return desaturateColor(color, ColorSchemes.saturationFactor);
    }

    public static int getHightlightColor() {
        return colorScheme[selectedColorScheme][HIGHLIGHT];
    }

    public static int getCoordBackgroundColor() {
        return colorScheme[selectedColorScheme][COORD_BACKGROUND];
    }

    public static int getCoordTextColor() {
        return colorScheme[selectedColorScheme][COORD_TEXT];
    }

    public static int getSelectedColor() {
        if (selectedColorScheme == CUSTOM_COLOR_SCHEME) {
            return ColorUtils.blendARGB(customLightColor, customDarkColor, 0.35f);
        }
        return colorScheme[selectedColorScheme][2];
    }

    public static void setCustomColors(int darkColor, int lightColor) {
        customDarkColor = darkColor;
        customLightColor = lightColor;
    }

    public static int getCustomDarkColor() {
        return customDarkColor;
    }

    public static int getCustomLightColor() {
        return customLightColor;
    }

    public static int getSelectedPatternDrawable() {
        switch (selectedPattern) {
            case 1:
                return R.drawable.square_single_shade;
            case 2:
                return R.drawable.square_double_shade;
            case 3:
                return R.drawable.diagonal_stripes;
        }

        return 0;
    }

    public static int desaturateColor(int color, float factor) {
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(color, hsl);

        // Reduce saturation
        hsl[1] = hsl[1] * factor;

        return ColorUtils.HSLToColor(hsl);
    }
}
