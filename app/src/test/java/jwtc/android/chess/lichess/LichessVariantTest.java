package jwtc.android.chess.lichess;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import jwtc.android.chess.lichess.models.LichessVariant;
import jwtc.chess.Variant;
import org.junit.Test;

public class LichessVariantTest {
    @Test
    public void mapsLichessKeys() {
        assertEquals(Variant.STANDARD, LichessVariant.toVariant("standard"));
        assertEquals(Variant.CHESS_960, LichessVariant.toVariant("chess960"));
        assertEquals(Variant.KING_OF_THE_HILL, LichessVariant.toVariant("kingOfTheHill"));
        assertNull(LichessVariant.toVariant("atomic"));
        assertNull(LichessVariant.toVariant(null));
    }

    @Test
    public void supportsOnlyWhatTheAppPlaysOnLichess() {
        assertTrue(LichessVariant.isSupported("standard"));
        assertTrue(LichessVariant.isSupported("chess960"));
        assertFalse(LichessVariant.isSupported("kingOfTheHill"));
        assertFalse(LichessVariant.isSupported("crazyhouse"));
        assertFalse(LichessVariant.isSupported(null));
    }
}
