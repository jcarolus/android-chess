package jwtc.chess;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class PgnTimeControlTest {
    @Test
    public void readsBaseAndIncrementIncludingZero() {
        PgnTimeControl control = PgnTimeControl.parse("30+0");
        assertEquals(PgnTimeControl.Type.TIMED, control.type);
        assertEquals(1, control.periods.size());
        PgnTimeControl.Period period = control.periods.get(0);
        assertEquals(PgnTimeControl.Period.Kind.INCREMENTAL, period.kind);
        assertEquals(30000, period.baseMillis);
        assertEquals(0, period.incrementMillis);
        assertEquals(0, period.moves);
        assertEquals(3000, PgnTimeControl.parse("300+3").periods.get(0).incrementMillis);
        assertEquals(0, PgnTimeControl.parse("0+1").periods.get(0).baseMillis);
    }

    @Test
    public void readsUnknownAndUntimedWithoutConfusingThemWithMissing() {
        assertEquals(PgnTimeControl.Type.UNKNOWN, PgnTimeControl.parse("?").type);
        assertEquals(PgnTimeControl.Type.UNTIMED, PgnTimeControl.parse("-").type);
        assertEquals(0, PgnTimeControl.parse("-").periods.size());
        assertNull(PgnTimeControl.parse(null));
        assertNull(PgnTimeControl.parse(""));
    }

    @Test
    public void readsStagedControlsInOrder() {
        PgnTimeControl control = PgnTimeControl.parse("40/7200:20/3600:900+30");
        assertEquals(3, control.periods.size());
        assertEquals(PgnTimeControl.Period.Kind.MOVES, control.periods.get(0).kind);
        assertEquals(40, control.periods.get(0).moves);
        assertEquals(7200000, control.periods.get(0).baseMillis);
        assertEquals(20, control.periods.get(1).moves);
        assertEquals(3600000, control.periods.get(1).baseMillis);
        assertEquals(PgnTimeControl.Period.Kind.INCREMENTAL, control.periods.get(2).kind);
        assertEquals(900000, control.periods.get(2).baseMillis);
        assertEquals(30000, control.periods.get(2).incrementMillis);
        assertEquals(3600000, PgnTimeControl.parse("40/7200:3600").periods.get(1).baseMillis);
        assertEquals(40, PgnTimeControl.parse("40/7200").periods.get(0).moves);
    }

    @Test
    public void readsSuddenDeathAndHourglass() {
        assertEquals(
                PgnTimeControl.Period.Kind.SUDDEN_DEATH,
                PgnTimeControl.parse("300").periods.get(0).kind);
        assertEquals(300000, PgnTimeControl.parse("300").periods.get(0).baseMillis);
        assertEquals(
                PgnTimeControl.Period.Kind.HOURGLASS,
                PgnTimeControl.parse("*180").periods.get(0).kind);
        assertEquals(180000, PgnTimeControl.parse("*180").periods.get(0).baseMillis);
    }

    @Test
    public void malformedAndUnrepresentableControlsStayUnavailable() {
        String[] invalid = {
            " ",
            "?+1",
            "-1",
            "30+",
            "30+-1",
            "30.5+1",
            "0/7200",
            "40/0",
            "*0",
            "*",
            "40/7200:",
            ":300",
            "300:300",
            "300+1:300",
            "*180:300",
            "40/7200::300",
            "999999999999999999999",
            "9223372036854776+1",
            "30+9223372036854776"
        };
        for (String value : invalid) {
            assertNull(value, PgnTimeControl.parse(value));
        }
    }
}
