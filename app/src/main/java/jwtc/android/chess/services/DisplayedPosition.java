package jwtc.android.chess.services;

import jwtc.android.chess.engine.RustBoard;
import jwtc.android.chess.engine.RustSearchRequest;

/** The last board Position rendered on screen, for Hotspot snapshot sharing. */
public final class DisplayedPosition {
    public static final class Snapshot {
        public final String fen;
        public final RustSearchRequest.Variant variant;

        private Snapshot(String fen, RustSearchRequest.Variant variant) {
            this.fen = fen;
            this.variant = variant;
        }
    }

    private static volatile Snapshot current =
            new Snapshot(RustBoard.START, RustSearchRequest.Variant.STANDARD);

    private DisplayedPosition() {}

    public static void publish(GameApi game) {
        RustSearchRequest.Variant variant =
                game.isKingOfTheHillVariant()
                        ? RustSearchRequest.Variant.KING_OF_THE_HILL
                        : game.isDuckVariant()
                                ? RustSearchRequest.Variant.DUCK
                                : game.isChess960Variant()
                                        ? RustSearchRequest.Variant.CHESS_960
                                        : RustSearchRequest.Variant.STANDARD;
        current = new Snapshot(game.getFEN(), variant);
    }

    public static String currentFen() {
        return current.fen;
    }

    public static Snapshot current() {
        return current;
    }
}
