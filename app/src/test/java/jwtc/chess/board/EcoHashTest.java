package jwtc.chess.board;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class EcoHashTest {
    // Expected hashes and packed moves verify compatibility with the opening-book format.
    @Test public void doublePawnPushMatchesNativeOpeningHash() {
        assertEquals(-2196248314007145541L, EcoHash.afterMove(-8567268772865283918L,
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1", 133428));
    }

    @Test public void whiteKingsideCastleMatchesNativeOpeningHash() {
        assertEquals(-716235359548464417L, EcoHash.afterMove(-1885793626085919453L,
            "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1",
            "r3k2r/8/8/8/8/8/8/R4RK1 b kq - 1 1", 20412));
    }

    @Test public void whiteQueensideCastleMatchesNativeOpeningHash() {
        assertEquals(-7619451941413590788L, EcoHash.afterMove(-1885793626085919453L,
            "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1",
            "r3k2r/8/8/8/8/8/8/2KR3R b kq - 1 1", 36540));
    }

    @Test public void stationaryChess960KingMatchesNativeOpeningHash() {
        assertEquals(391224981820584491L, EcoHash.afterMove(4708815201563109436L,
            "4k3/8/8/8/8/8/8/R5KR w KQ - 0 1",
            "4k3/8/8/8/8/8/8/R4RK1 b - - 1 1", 20414));
    }

    @Test public void chess960QueensideCastleMatchesNativeOpeningHash() {
        assertEquals(-2894428181510342122L, EcoHash.afterMove(4708815201563109436L,
            "4k3/8/8/8/8/8/8/R5KR w KQ - 0 1",
            "4k3/8/8/8/8/8/8/2KR3R b - - 1 1", 36542));
    }

    @Test public void enPassantMatchesNativeOpeningHash() {
        assertEquals(-8326939384501386550L, EcoHash.afterMove(-8761735090292878988L,
            "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1",
            "4k3/8/3P4/8/8/8/8/4K3 b - - 0 1", 74972));
    }

    @Test public void queenPromotionMatchesNativeOpeningHash() {
        assertEquals(-1187054932290895442L, EcoHash.afterMove(1294301692422072319L,
            "1r2k3/P7/8/8/8/8/8/4K3 w - - 0 1",
            "Qr2k3/8/8/8/8/8/8/4K3 b - - 0 1", 4718600));
    }

    @Test public void rookPromotionMatchesNativeOpeningHash() {
        assertEquals(-658403226427102469L, EcoHash.afterMove(1294301692422072319L,
            "1r2k3/P7/8/8/8/8/8/4K3 w - - 0 1",
            "Rr2k3/8/8/8/8/8/8/4K3 b - - 0 1", 3670024));
    }

    @Test public void bishopPromotionMatchesNativeOpeningHash() {
        assertEquals(-2802877897225255557L, EcoHash.afterMove(1294301692422072319L,
            "1r2k3/P7/8/8/8/8/8/4K3 w - - 0 1",
            "Br2k3/8/8/8/8/8/8/4K3 b - - 0 1", 2621448));
    }

    @Test public void knightPromotionMatchesNativeOpeningHash() {
        assertEquals(-4066872573155810796L, EcoHash.afterMove(1294301692422072319L,
            "1r2k3/P7/8/8/8/8/8/4K3 w - - 0 1",
            "Nr2k3/8/8/8/8/8/8/4K3 b - - 0 1", 1572872));
    }

    @Test public void queenPromotionCaptureMatchesNativeOpeningHash() {
        assertEquals(-5652593717535165522L, EcoHash.afterMove(1294301692422072319L,
            "1r2k3/P7/8/8/8/8/8/4K3 w - - 0 1",
            "1Q2k3/8/8/8/8/8/8/4K3 b - - 0 1", 5046344));
    }

    @Test public void rookPromotionCaptureMatchesNativeOpeningHash() {
        assertEquals(-7507283012364456261L, EcoHash.afterMove(1294301692422072319L,
            "1r2k3/P7/8/8/8/8/8/4K3 w - - 0 1",
            "1R2k3/8/8/8/8/8/8/4K3 b - - 0 1", 3997768));
    }

    @Test public void bishopPromotionCaptureMatchesNativeOpeningHash() {
        assertEquals(4883004979437021598L, EcoHash.afterMove(1294301692422072319L,
            "1r2k3/P7/8/8/8/8/8/4K3 w - - 0 1",
            "1B2k3/8/8/8/8/8/8/4K3 b - - 0 1", 2687048));
    }

    @Test public void knightPromotionCaptureMatchesNativeOpeningHash() {
        assertEquals(-4368939821959506994L, EcoHash.afterMove(1294301692422072319L,
            "1r2k3/P7/8/8/8/8/8/4K3 w - - 0 1",
            "1N2k3/8/8/8/8/8/8/4K3 b - - 0 1", 1638472));
    }

    @Test public void blackKingsideCastleMatchesNativeOpeningHash() {
        assertEquals(8343423737688129390L, EcoHash.afterMove(-1885793626085919453L,
            "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1",
            "r4rk1/8/8/8/8/8/8/R3K2R w KQ - 1 1", 16772));
    }

    @Test public void blackQueensideCastleMatchesNativeOpeningHash() {
        assertEquals(6223524014755432365L, EcoHash.afterMove(-1885793626085919453L,
            "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1",
            "2kr3r/8/8/8/8/8/8/R3K2R w KQ - 1 1", 32900));
    }

    @Test public void duckPlacementDoesNotChangeOpeningHash() {
        assertEquals(-2196248314007145541L, EcoHash.afterMove(-8567268772865283918L,
            "rnbqkbnr/pppppppp/8/3$4/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            "rnbqkbnr/pppppppp/8/8/3$P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1", 133428));
    }
}
