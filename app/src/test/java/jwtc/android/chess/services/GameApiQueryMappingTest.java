package jwtc.android.chess.services;

import static org.junit.Assert.assertEquals;

import jwtc.android.chess.engine.RustBoardResult;
import jwtc.chess.board.BoardConstants;
import jwtc.chess.board.BoardPosition;
import org.junit.Test;

public class GameApiQueryMappingTest {

    @Test
    public void mapsEachRustGameStateCodeToItsLegacyConstant() {
        assertEquals(BoardConstants.PLAY, RustBoardResult.boardState(0));
        assertEquals(BoardConstants.CHECK, RustBoardResult.boardState(1));
        assertEquals(BoardConstants.MATE, RustBoardResult.boardState(2));
        assertEquals(BoardConstants.STALEMATE, RustBoardResult.boardState(3));
        assertEquals(BoardConstants.DRAW_MATERIAL, RustBoardResult.boardState(4));
        assertEquals(BoardConstants.DRAW_50, RustBoardResult.boardState(5));
        assertEquals(BoardConstants.DRAW_REPEAT, RustBoardResult.boardState(6));
    }

    @Test
    public void emptySquareIsFieldForEitherTurn() {
        assertEquals(BoardConstants.FIELD, BoardPosition.pieceType((char) 0, BoardConstants.WHITE));
        assertEquals(BoardConstants.FIELD, BoardPosition.pieceType((char) 0, BoardConstants.BLACK));
    }

    @Test
    public void duckMarkerIsFieldForEitherTurn() {
        assertEquals(BoardConstants.FIELD, BoardPosition.pieceType('$', BoardConstants.WHITE));
        assertEquals(BoardConstants.FIELD, BoardPosition.pieceType('$', BoardConstants.BLACK));
    }

    @Test
    public void uppercaseLetterIsItsPieceOnlyWhenAskedForWhite() {
        assertEquals(BoardConstants.PAWN, BoardPosition.pieceType('P', BoardConstants.WHITE));
        assertEquals(BoardConstants.KNIGHT, BoardPosition.pieceType('N', BoardConstants.WHITE));
        assertEquals(BoardConstants.BISHOP, BoardPosition.pieceType('B', BoardConstants.WHITE));
        assertEquals(BoardConstants.ROOK, BoardPosition.pieceType('R', BoardConstants.WHITE));
        assertEquals(BoardConstants.QUEEN, BoardPosition.pieceType('Q', BoardConstants.WHITE));
        assertEquals(BoardConstants.KING, BoardPosition.pieceType('K', BoardConstants.WHITE));

        assertEquals(BoardConstants.FIELD, BoardPosition.pieceType('P', BoardConstants.BLACK));
        assertEquals(BoardConstants.FIELD, BoardPosition.pieceType('N', BoardConstants.BLACK));
        assertEquals(BoardConstants.FIELD, BoardPosition.pieceType('B', BoardConstants.BLACK));
        assertEquals(BoardConstants.FIELD, BoardPosition.pieceType('R', BoardConstants.BLACK));
        assertEquals(BoardConstants.FIELD, BoardPosition.pieceType('Q', BoardConstants.BLACK));
        assertEquals(BoardConstants.FIELD, BoardPosition.pieceType('K', BoardConstants.BLACK));
    }

    @Test
    public void lowercaseLetterIsItsPieceOnlyWhenAskedForBlack() {
        assertEquals(BoardConstants.PAWN, BoardPosition.pieceType('p', BoardConstants.BLACK));
        assertEquals(BoardConstants.KNIGHT, BoardPosition.pieceType('n', BoardConstants.BLACK));
        assertEquals(BoardConstants.BISHOP, BoardPosition.pieceType('b', BoardConstants.BLACK));
        assertEquals(BoardConstants.ROOK, BoardPosition.pieceType('r', BoardConstants.BLACK));
        assertEquals(BoardConstants.QUEEN, BoardPosition.pieceType('q', BoardConstants.BLACK));
        assertEquals(BoardConstants.KING, BoardPosition.pieceType('k', BoardConstants.BLACK));

        assertEquals(BoardConstants.FIELD, BoardPosition.pieceType('p', BoardConstants.WHITE));
        assertEquals(BoardConstants.FIELD, BoardPosition.pieceType('n', BoardConstants.WHITE));
        assertEquals(BoardConstants.FIELD, BoardPosition.pieceType('b', BoardConstants.WHITE));
        assertEquals(BoardConstants.FIELD, BoardPosition.pieceType('r', BoardConstants.WHITE));
        assertEquals(BoardConstants.FIELD, BoardPosition.pieceType('q', BoardConstants.WHITE));
        assertEquals(BoardConstants.FIELD, BoardPosition.pieceType('k', BoardConstants.WHITE));
    }
}
