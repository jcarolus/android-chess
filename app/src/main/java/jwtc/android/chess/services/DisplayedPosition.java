package jwtc.android.chess.services;

import jwtc.android.chess.engine.RustBoard;
import jwtc.chess.Variant;

/** The last board Position rendered on screen, for Hotspot snapshot sharing. */
public final class DisplayedPosition {
    public static final class Snapshot {
        public final String fen;
        public final Variant variant;

        private Snapshot(String fen, Variant variant) {
            this.fen = fen;
            this.variant = variant;
        }
    }

    private static volatile Snapshot current = new Snapshot(RustBoard.START, Variant.STANDARD);

    private DisplayedPosition() {}

    public static void publish(GameApi game) {
        Variant variant =
                game.isKingOfTheHillVariant()
                        ? Variant.KING_OF_THE_HILL
                        : game.isDuckVariant()
                                ? Variant.DUCK
                                : game.isChess960Variant() ? Variant.CHESS_960 : Variant.STANDARD;
        current = new Snapshot(game.getFEN(), variant);
    }

    public static String currentFen() {
        return current.fen;
    }

    public static Snapshot current() {
        return current;
    }
}
