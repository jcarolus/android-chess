package jwtc.android.chess.engine;

import static org.junit.Assert.*;

import java.util.Arrays;
import org.junit.Test;

public class UciStrengthTest {
    @Test
    public void configuresStrengthOnlyWhenChangedAndDisablesItForUnrestrictedSearch() {
        UciStrength strength = new UciStrength();
        strength.accept("option name UCI_LimitStrength type check default false");
        strength.accept("option name UCI_Elo type spin default 1500 min 1320 max 3190");
        assertEquals(
                Arrays.asList(
                        "setoption name UCI_LimitStrength value true",
                        "setoption name UCI_Elo value 1500"),
                strength.configure(1500));
        assertTrue(strength.configure(1500).isEmpty());
        assertEquals(
                Arrays.asList(
                        "setoption name UCI_LimitStrength value true",
                        "setoption name UCI_Elo value 3190"),
                strength.configure(4000));
        assertEquals(
                Arrays.asList("setoption name UCI_LimitStrength value false"),
                strength.configure(null));
        assertTrue(strength.configure(null).isEmpty());
    }

    @Test
    public void unsupportedOptionsAreNeverSentAndDefaultLimitingIsDisabled() {
        UciStrength strength = new UciStrength();
        assertTrue(strength.configure(1500).isEmpty());
        strength.accept("option name UCI_LimitStrength type check default true");
        assertEquals(
                Arrays.asList("setoption name UCI_LimitStrength value false"),
                strength.configure(1500));
    }

    @Test
    public void acceptsExtraWhitespaceInOptionDeclarations() {
        UciStrength strength = new UciStrength();
        strength.accept("  option  name UCI_LimitStrength type check default false  ");
        strength.accept("option name UCI_Elo type spin default 1500   min 1320 max 3190");
        assertTrue(strength.supported());
    }

    @Test
    public void requiresBothOptionsWithTheirExpectedTypes() {
        UciStrength strength = new UciStrength();
        strength.accept("option name UCI_Elo type spin default 1500 min 1320 max 3190");
        assertFalse(strength.supported());
        strength.accept("option name UCI_LimitStrength type string default false");
        assertFalse(strength.supported());
        strength.accept("option name UCI_LimitStrength type check default false");
        assertTrue(strength.supported());
        assertEquals(1500, strength.defaultElo());
    }

    @Test
    public void ignoresMalformedRangesAndOverflow() {
        for (String range :
                Arrays.asList(
                        "default 1500 min 2000 max 1000",
                        "default 3000 min 1000 max 2000",
                        "default 1500 min 0 max 999999999999",
                        "default invalid min 0 max 2000")) {
            UciStrength strength = new UciStrength();
            strength.accept("option name UCI_LimitStrength type check default true");
            strength.accept("option name UCI_Elo type spin " + range);
            assertFalse(strength.supported());
        }
    }

    @Test
    public void choicesPreserveEndpointsAndSavedValue() {
        UciStrength strength = new UciStrength();
        strength.accept("option name UCI_Elo type spin default 1500 min 1320 max 1690");
        assertEquals(Arrays.asList(1320, 1400, 1450, 1500, 1600, 1690), strength.choices(1450));
        assertEquals(1320, strength.clamp(800));
        assertEquals(1690, strength.clamp(3000));
    }

    @Test
    public void supportsNegativeAndSingleValueRanges() {
        UciStrength strength = new UciStrength();
        strength.accept("option name UCI_Elo type spin default 0 min -125 max 125");
        assertEquals(Arrays.asList(-125, -100, 0, 100, 125), strength.choices(0));
        strength.accept("option name UCI_Elo type spin default 500 min 500 max 500");
        assertEquals(Arrays.asList(500), strength.choices(1500));
    }

    @Test
    public void extremeRangesProduceBoundedChoicesWithoutOverflow() {
        UciStrength strength = new UciStrength();
        strength.accept("option name UCI_Elo type spin default 0 min -2147483648 max 2147483647");
        assertTrue(strength.choices(0).size() <= 103);
        assertEquals(Integer.MIN_VALUE, (int) strength.choices(0).get(0));
    }
}
