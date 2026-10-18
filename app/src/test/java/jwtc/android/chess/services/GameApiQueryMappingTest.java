package jwtc.android.chess.services;

import static org.junit.Assert.assertEquals;

import jwtc.chess.board.BoardConstants;
import org.junit.Test;

public class GameApiQueryMappingTest {

    @Test
    public void mapsEachRustGameStateCodeToItsLegacyConstant() {
        assertEquals(BoardConstants.PLAY, GameApi.legacyGameState(0));
        assertEquals(BoardConstants.CHECK, GameApi.legacyGameState(1));
        assertEquals(BoardConstants.MATE, GameApi.legacyGameState(2));
        assertEquals(BoardConstants.STALEMATE, GameApi.legacyGameState(3));
        assertEquals(BoardConstants.DRAW_MATERIAL, GameApi.legacyGameState(4));
        assertEquals(BoardConstants.DRAW_50, GameApi.legacyGameState(5));
        assertEquals(BoardConstants.DRAW_REPEAT, GameApi.legacyGameState(6));
    }

    @Test
    public void emptySquareIsFieldForEitherTurn() {
        assertEquals(BoardConstants.FIELD, GameApi.legacyPieceAt((char) 0, BoardConstants.WHITE));
        assertEquals(BoardConstants.FIELD, GameApi.legacyPieceAt((char) 0, BoardConstants.BLACK));
    }

    @Test
    public void duckMarkerIsFieldForEitherTurn() {
        assertEquals(BoardConstants.FIELD, GameApi.legacyPieceAt('$', BoardConstants.WHITE));
        assertEquals(BoardConstants.FIELD, GameApi.legacyPieceAt('$', BoardConstants.BLACK));
    }

    @Test
    public void uppercaseLetterIsItsPieceOnlyWhenAskedForWhite() {
        assertEquals(BoardConstants.PAWN, GameApi.legacyPieceAt('P', BoardConstants.WHITE));
        assertEquals(BoardConstants.KNIGHT, GameApi.legacyPieceAt('N', BoardConstants.WHITE));
        assertEquals(BoardConstants.BISHOP, GameApi.legacyPieceAt('B', BoardConstants.WHITE));
        assertEquals(BoardConstants.ROOK, GameApi.legacyPieceAt('R', BoardConstants.WHITE));
        assertEquals(BoardConstants.QUEEN, GameApi.legacyPieceAt('Q', BoardConstants.WHITE));
        assertEquals(BoardConstants.KING, GameApi.legacyPieceAt('K', BoardConstants.WHITE));

        assertEquals(BoardConstants.FIELD, GameApi.legacyPieceAt('P', BoardConstants.BLACK));
        assertEquals(BoardConstants.FIELD, GameApi.legacyPieceAt('N', BoardConstants.BLACK));
        assertEquals(BoardConstants.FIELD, GameApi.legacyPieceAt('B', BoardConstants.BLACK));
        assertEquals(BoardConstants.FIELD, GameApi.legacyPieceAt('R', BoardConstants.BLACK));
        assertEquals(BoardConstants.FIELD, GameApi.legacyPieceAt('Q', BoardConstants.BLACK));
        assertEquals(BoardConstants.FIELD, GameApi.legacyPieceAt('K', BoardConstants.BLACK));
    }

    @Test
    public void lowercaseLetterIsItsPieceOnlyWhenAskedForBlack() {
        assertEquals(BoardConstants.PAWN, GameApi.legacyPieceAt('p', BoardConstants.BLACK));
        assertEquals(BoardConstants.KNIGHT, GameApi.legacyPieceAt('n', BoardConstants.BLACK));
        assertEquals(BoardConstants.BISHOP, GameApi.legacyPieceAt('b', BoardConstants.BLACK));
        assertEquals(BoardConstants.ROOK, GameApi.legacyPieceAt('r', BoardConstants.BLACK));
        assertEquals(BoardConstants.QUEEN, GameApi.legacyPieceAt('q', BoardConstants.BLACK));
        assertEquals(BoardConstants.KING, GameApi.legacyPieceAt('k', BoardConstants.BLACK));

        assertEquals(BoardConstants.FIELD, GameApi.legacyPieceAt('p', BoardConstants.WHITE));
        assertEquals(BoardConstants.FIELD, GameApi.legacyPieceAt('n', BoardConstants.WHITE));
        assertEquals(BoardConstants.FIELD, GameApi.legacyPieceAt('b', BoardConstants.WHITE));
        assertEquals(BoardConstants.FIELD, GameApi.legacyPieceAt('r', BoardConstants.WHITE));
        assertEquals(BoardConstants.FIELD, GameApi.legacyPieceAt('q', BoardConstants.WHITE));
        assertEquals(BoardConstants.FIELD, GameApi.legacyPieceAt('k', BoardConstants.WHITE));
    }
}
