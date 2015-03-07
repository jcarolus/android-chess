package jwtc.chess.board;

import jwtc.chess.*;
// "split" object for ChessBoard. contains the constants of the board object

public class BoardConstants 
{
	public static Valuation m_valuation = new Valuation();
	
	// states of the game ////////////////////////////////////////////////////////////////////////////
	public static final int PLAY = 1;
	public static final int CHECK = 2;
	public static final int INVALID = 3; // only occurs when king can be hit or when invalid number of pieces is on the board (more than one king). can be used when setting up a new position
	public static final int DRAW_MATERIAL = 4; // no one can win (ie KING against KING)
	public static final int DRAW_50 = 5; // after 25 full moves no hit or pawnmove occured
	public static final int MATE = 6;
	public static final int STALEMATE = 7;
	public static final int DRAW_REPEAT = 8; // draw by repetition (3 times same board position)
	// ///////////////////////////////////////////////////////////////////////////////////////////////
	
	// array index of data memebers that hold data for either black or white. these must be 0 and 1 cause arrays are of length 2
	public static final int BLACK = 0;
	public static final int WHITE = 1;
	
	// index and representation of the pieces array and values - must be from [0-5].
	public static final int PAWN = 0;
	public static final int KNIGHT = 1;
	public static final int BISHOP = 2;
	public static final int ROOK = 3;
	public static final int QUEEN = 4;
	public static final int KING = 5;
	// not a piece: a field
	public static final int FIELD = -1;
	
	// not consequently used - 64 fields on a chess board, 6 pieces
	public static final int NUM_FIELDS = 64;
	public static final int NUM_PIECES = 6;
	public static final int MAX_MOVES = 255; // maximum number of moves possible from a position - not yet above 218 found documented
	
	// all bits set to one for the rows (index is row)
	public static final long[] ROW_BITS = {255L, 65280L, 16711680L, 4278190080L, 1095216660480L, 280375465082880L, 71776119061217280L, -72057594037927936L};
	// same as ROW_BITS but for the files
	public static final long[] FILE_BITS = {72340172838076673L, 144680345676153346L, 289360691352306692L, 578721382704613384L, 1157442765409226768L, 2314885530818453536L, 4629771061636907072L, -9187201950435737472L};
											  
	// "enumeration" integer position values
	public static final int a8 = 0, b8 = 1, c8 = 2, d8 = 3, e8 = 4, f8 = 5, g8 = 6, h8 = 7;
	public static final int a7 = 8, b7 = 9, c7 = 10, d7 = 11, e7 = 12, f7 = 13, g7 = 14, h7 = 15;
	public static final int a6 = 16, b6 = 17, c6 = 18, d6 = 19, e6 = 20, f6 = 21, g6 = 22, h6 = 23;
	public static final int a5 = 24, b5 = 25, c5 = 26, d5 = 27, e5 = 28, f5 = 29, g5 = 30, h5 = 31;
	public static final int a4 = 32, b4 = 33, c4 = 34, d4 = 35, e4 = 36, f4 = 37, g4 = 38, h4 = 39;
	public static final int a3 = 40, b3 = 41, c3 = 42, d3 = 43, e3 = 44, f3 = 45, g3 = 46, h3 = 47;
	public static final int a2 = 48, b2 = 49, c2 = 50, d2 = 51, e2 = 52, f2 = 53, g2 = 54, h2 = 55;
	public static final int a1 = 56, b1 = 57, c1 = 58, d1 = 59, e1 = 60, f1 = 61, g1 = 62, h1 = 63;
	
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	// instead of calling a method row(pos), position is index on array for the rows
	public static final int[] ROW = {
			0, 0, 0, 0, 0, 0, 0, 0,
			 1, 1, 1, 1, 1, 1, 1, 1, 
			 2, 2, 2, 2, 2, 2, 2, 2,
			 3, 3, 3, 3, 3, 3, 3, 3, 
			 4, 4, 4, 4, 4, 4, 4, 4, 
			 5, 5, 5, 5, 5, 5, 5, 5, 
			 6, 6, 6, 6, 6, 6, 6, 6, 
			 7, 7, 7, 7, 7, 7, 7, 7};
	// as with ROW, but for column
	public static final int[] COL = {
			0, 1, 2, 3, 4, 5, 6, 7,
		 0, 1, 2, 3, 4, 5, 6, 7,
		 0, 1, 2, 3, 4, 5, 6, 7,
		 0, 1, 2, 3, 4, 5, 6, 7,
		 0, 1, 2, 3, 4, 5, 6, 7,
		 0, 1, 2, 3, 4, 5, 6, 7,
		 0, 1, 2, 3, 4, 5, 6, 7,
		 0, 1, 2, 3, 4, 5, 6, 7};

	// the row or rank seen from the oposite colors is mirrored, so also convenient use from an array
	// first index color, second index position
	public static final int[][] ROW_TURN = {
			{0, 0, 0, 0, 0, 0, 0, 0,
				 1, 1, 1, 1, 1, 1, 1, 1, 
				 2, 2, 2, 2, 2, 2, 2, 2,
				 3, 3, 3, 3, 3, 3, 3, 3, 
				 4, 4, 4, 4, 4, 4, 4, 4, 
				 5, 5, 5, 5, 5, 5, 5, 5, 
				 6, 6, 6, 6, 6, 6, 6, 6, 
				 7, 7, 7, 7, 7, 7, 7, 7}
			,
				 {7, 7, 7, 7, 7, 7, 7, 7,
				 	6, 6, 6, 6, 6, 6, 6, 6,
				 	 5, 5, 5, 5, 5, 5, 5, 5,
				 	4, 4, 4, 4, 4, 4, 4, 4,
				 	 3, 3, 3, 3, 3, 3, 3, 3,
				 	2, 2, 2, 2, 2, 2, 2, 2,
					1, 1, 1, 1, 1, 1, 1, 1, 
					0, 0, 0, 0, 0, 0, 0, 0}
	};
	
}
