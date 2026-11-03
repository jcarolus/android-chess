package jwtc.android.chess.tools;

/** Exercises Practice Position selection through the real Game and Rust search path. */
public final class PracticePositionSelectionInstrumentation {
    private static final String MATE_GAME =
            "[Event \"Moscow\"]\n"
                    + "[Result \"1-0\"]\n\n"
                    + "1.e4 e6 2.d4 d5 3.Nc3 dxe4 4.Nxe4 Ne7 5.Bd3 g6 6.Nf6# 1-0";

    public static void verify() {
        PracticePositionSelector selector = new PracticePositionSelector(new ImportApi());
        check(selector.select("1. e4 e5 *") == null, "Non-mate Game was selected");

        String selected = selector.select(MATE_GAME);
        check(selected != null && selected.startsWith("[FEN \""), "Mate Game was not selected");
        check(new ImportApi().loadPGN(selected), "Selected Practice Position is invalid PGN");
        check(selector.select(MATE_GAME) == null, "Final Position was selected twice");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
