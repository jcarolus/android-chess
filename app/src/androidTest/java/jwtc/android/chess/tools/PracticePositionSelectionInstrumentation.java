package jwtc.android.chess.tools;

/** Exercises Practice Position selection through the real Game and Rust search path. */
public final class PracticePositionSelectionInstrumentation {
    private static final String MATE_GAME =
            "[Event \"Moscow\"]\n"
                    + "[Result \"1-0\"]\n\n"
                    + "1.e4 e6 2.d4 d5 3.Nc3 dxe4 4.Nxe4 Ne7 5.Bd3 g6 6.Nf6# 1-0";
    private static final String MATE_IN_ONE_FEN = "4k3/R7/1R6/8/8/8/8/4K3 w - - 0 1";
    private static final String MATE_IN_ONE = "[FEN \"" + MATE_IN_ONE_FEN + "\"]\n\n1. Rb8# 1-0";

    public static void verify() {
        PracticePositionSelector selector = new PracticePositionSelector(new ImportApi());
        check(selector.select("1. e4 e5 *") == null, "Non-mate Game was selected");

        String selected = selector.select(MATE_GAME);
        check(selected != null && selected.startsWith("[FEN \""), "Mate Game was not selected");
        check(new ImportApi().loadPGN(selected), "Selected Practice Position is invalid PGN");
        check(selector.select(MATE_GAME) == null, "Final Position was selected twice");

        check(
                selector.select("[FEN \"1R2k3/R7/8/8/8/8/8/4K3 b - - 1 1\"]\n\n1-0") == null,
                "An already checkmated starting Position was selected");
        String mateInOne = selector.select(MATE_IN_ONE);
        check(mateInOne != null, "Mate-in-one Game was not selected");
        ImportApi practice = new ImportApi();
        check(practice.loadPGN(mateInOne), "Selected mate-in-one is invalid PGN");
        check(practice.getPGNSize() == 1, "Selected mate-in-one lost its solution move");
        practice.jumpToBoardNum(0);
        check(MATE_IN_ONE_FEN.equals(practice.getFEN()), "Mate-in-one lost its starting Position");
        check(selector.select(MATE_IN_ONE) == null, "Mate-in-one was selected twice");

        String twoPly =
                new PracticePositionSelector(new ImportApi())
                        .select("[FEN \"3k4/R7/1R6/8/8/8/8/4K3 b - - 0 1\"]\n1... Ke8 2. Rb8# 1-0");
        check(twoPly != null && practice.loadPGN(twoPly), "Two-ply Game was not selected");
        check(practice.getPGNSize() == 1, "Two-ply Game did not yield a mate-in-one Position");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
