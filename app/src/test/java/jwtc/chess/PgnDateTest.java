package jwtc.chess;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import org.junit.Test;

public class PgnDateTest {
    private static Date date(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month - 1, day);
        return calendar.getTime();
    }

    @Test
    public void parsesAFullDate() {
        assertEquals(date(2023, 5, 17), PgnDate.parse("2023.05.17"));
        assertEquals(date(2023, 5, 7), PgnDate.parse("2023.5.7"));
    }

    @Test
    public void anUnknownDayIsTheFirstOfTheMonth() {
        assertEquals(date(2023, 5, 1), PgnDate.parse("2023.05.??"));
    }

    @Test
    public void anUnknownMonthIsJanuaryFirst() {
        assertEquals(date(2023, 1, 1), PgnDate.parse("2023.??.??"));
    }

    @Test
    public void anUnknownYearIsNotADate() {
        assertNull(PgnDate.parse("????.??.??"));
        assertNull(PgnDate.parse("????.05.17"));
        assertNull(PgnDate.parse("????.??.01"));
    }

    @Test
    public void textThatIsNotADateIsNull() {
        assertNull(PgnDate.parse(null));
        assertNull(PgnDate.parse(""));
        assertNull(PgnDate.parse("?"));
        assertNull(PgnDate.parse("yesterday"));
        assertNull(PgnDate.parse("2023-05-17"));
        assertNull(PgnDate.parse(" 2023.05.17"));
        assertNull(PgnDate.parse("2023.05"));
    }

    @Test
    public void datesThatDoNotExistAreNull() {
        assertNull(PgnDate.parse("2023.13.01"));
        assertNull(PgnDate.parse("2023.02.30"));
        assertNull(PgnDate.parse("2023.00.10"));
        assertNull(PgnDate.parse("99999999999.01.01"));
    }

    @Test
    public void leapDaysFollowTheCalendar() {
        assertEquals(date(2024, 2, 29), PgnDate.parse("2024.02.29"));
        assertNull(PgnDate.parse("2023.02.29"));
    }

    @Test
    public void formatsWithPaddingAndAnEmptyStringForNull() {
        assertEquals("2023.05.07", PgnDate.format(date(2023, 5, 7)));
        assertEquals("0987.01.02", PgnDate.format(date(987, 1, 2)));
        assertEquals("", PgnDate.format(null));
    }

    @Test
    public void formatAndParseRoundTrip() {
        Date d = date(2023, 12, 31);
        assertEquals(d, PgnDate.parse(PgnDate.format(d)));
    }

    @Test
    public void formatIgnoresTheDefaultLocale() {
        Locale saved = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-SA-u-nu-arab"));
            assertEquals("2023.05.07", PgnDate.format(date(2023, 5, 7)));
        } finally {
            Locale.setDefault(saved);
        }
    }
}
