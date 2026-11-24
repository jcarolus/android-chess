package jwtc.android.chess.engine;

import static org.junit.Assert.assertEquals;

import jwtc.chess.Pgn;
import jwtc.chess.Variant;
import org.junit.Test;

public class PgnReplayTest {
    @Test
    public void rookFileCastlingRightsSelectChess960WithoutAVariantTag() {
        for (String rights : new String[] {"F", "H", "A", "f", "h", "a"}) {
            String fen = "r3kr1r/8/8/8/8/8/8/R3KR1R w " + rights + " - 0 1";
            PgnReplay.StartingPosition start =
                    PgnReplay.startingPosition(Pgn.parse("[FEN \"" + fen + "\"]\n*"));
            assertEquals(Variant.CHESS_960, start.variant);
            assertEquals(fen, start.fen);
            assertEquals(
                    Variant.CHESS_960,
                    PgnReplay.startingPosition(
                                    Pgn.parse("[Variant \"Standard\"]\n[FEN \"" + fen + "\"]\n*"))
                            .variant);
        }
    }
}
