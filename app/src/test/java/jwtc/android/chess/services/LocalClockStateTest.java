package jwtc.android.chess.services;

import static jwtc.chess.board.BoardConstants.BLACK;
import static jwtc.chess.board.BoardConstants.WHITE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LocalClockStateTest {
    @Test
    public void completedTurnsChargeMoverAndAwardIncrement() {
        LocalClockState clock = new LocalClockState();
        clock.start(2000, 60000, 60000, WHITE, 1000, true);

        assertEquals(57000, clock.remaining(WHITE, 4000));
        assertEquals(60000, clock.remaining(BLACK, 4000));
        clock.completeTurn(4000);
        assertEquals(BLACK, clock.activePlayer());
        assertEquals(59000, clock.remaining(WHITE, 5000));
        assertEquals(59000, clock.remaining(BLACK, 5000));

        clock.completeTurn(5000);
        assertEquals(WHITE, clock.activePlayer());
        assertEquals(58000, clock.remaining(WHITE, 6000));
        assertEquals(61000, clock.remaining(BLACK, 6000));
    }

    @Test
    public void readingEitherPlayerDoesNotChangeTheActiveClock() {
        LocalClockState clock = new LocalClockState();
        clock.start(0, 60000, 60000, BLACK, 1000, true);

        assertEquals(60000, clock.remaining(WHITE, 2000));
        assertEquals(58000, clock.remaining(BLACK, 3000));
        assertEquals(60000, clock.remaining(WHITE, 4000));
        assertEquals(BLACK, clock.activePlayer());
        assertEquals(56000, clock.remaining(BLACK, 5000));
    }

    @Test
    public void expiredMoveCannotReceiveIncrementOrSwitchPlayer() {
        for (long moveTime : new long[] {1100, 1200}) {
            LocalClockState clock = new LocalClockState();
            clock.start(2000, 100, 60000, WHITE, 1000, true);
            clock.completeTurn(moveTime);

            assertEquals(0, clock.remaining(WHITE, 2000));
            assertEquals(60000, clock.remaining(BLACK, 2000));
            assertEquals(WHITE, clock.activePlayer());
            assertFalse(clock.isRunning());
            assertTrue(clock.isConfigured());
        }
    }

    @Test
    public void stoppedClockFreezesBalancesAndIgnoresMoves() {
        LocalClockState clock = new LocalClockState();
        clock.start(2000, 60000, 60000, BLACK, 1000, true);
        clock.stop(4000);
        clock.completeTurn(5000);
        clock.stop(6000);

        assertEquals(57000, clock.remaining(BLACK, 10000));
        assertEquals(60000, clock.remaining(WHITE, 10000));
        assertEquals(BLACK, clock.activePlayer());
        assertFalse(clock.isRunning());
    }

    @Test
    public void configuredButNotStartedClockDoesNotConsumeTime() {
        LocalClockState clock = new LocalClockState();
        clock.start(2000, 60000, 60000, WHITE, 0, false);
        clock.completeTurn(100000);

        assertEquals(60000, clock.remaining(WHITE, 100000));
        assertEquals(WHITE, clock.activePlayer());
        assertFalse(clock.isRunning());
    }

    @Test
    public void synchronizationReplacesBalancesAndActivePlayerWithoutIncrement() {
        LocalClockState clock = new LocalClockState();
        clock.start(2000, 60000, 60000, WHITE, 1000, true);
        clock.completeTurn(3000);
        clock.start(2000, 45000, 50000, WHITE, 4000, true);

        assertEquals(45000, clock.remaining(WHITE, 4000));
        assertEquals(50000, clock.remaining(BLACK, 4000));
        assertEquals(44000, clock.remaining(WHITE, 5000));
    }

    @Test
    public void restoredClockChargesBackgroundTimeToSavedPlayer() {
        LocalClockState clock = new LocalClockState();
        clock.start(0, 60000, 40000, BLACK, -10000, true);

        assertEquals(60000, clock.remaining(WHITE, 5000));
        assertEquals(25000, clock.remaining(BLACK, 5000));
    }

    @Test
    public void handingOverChargesElapsedTimeWithoutIncrement() {
        LocalClockState clock = new LocalClockState();
        clock.start(2000, 60000, 60000, BLACK, 1000, true);

        clock.setActivePlayer(WHITE, 3000);
        assertEquals(WHITE, clock.activePlayer());
        assertEquals(58000, clock.remaining(BLACK, 5000));
        assertEquals(58000, clock.remaining(WHITE, 5000));
    }
}
