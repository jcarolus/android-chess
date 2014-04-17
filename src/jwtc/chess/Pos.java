package jwtc.chess;

// Pos - Wrapper class for a position or square on the board
// Provides only static methods
// A position is an integer between 0 and 63 for the 64 squares
// This part of the code is given under Pos to "free up" some lines in ChessBoard.
// In ChessBoard all other positional related stuff is done

public class Pos
{
	// returns positional value [0-63] for squares [a8-h1]
	// when a position cannot be created a message is sent on console out (co).
	// used to initialize values, no speed needed
	public static int fromString(final String s)
	{
		//if(s.length() != 2)
		//	co.pl("Cannot create Pos from: " + s);
		char c = s.charAt(0);
		//if(c < 'a' || c > 'h')	
		//	co.pl("Cannot create Pos from: " + c);
		int col, row = Integer.parseInt(s.substring(1));
		col = (int)c - (int)'a';
		
		return ((8-row) * 8) + col;
	}

	// returns positional value [0-63] from a column and row.
	// @col the column [0-7] (left to right)
	// @row the row [0-7] (top to bottom)
	// no check for invalid row or col is done for reasons of speed
	public static final int fromColAndRow(final int col, final int row)
	{
		return (row * 8) + col;
	}
	
	// returns the row [0-7] from top to bottom; ie values in [a8-h8] return 0.
	public static int row(final int val)
	{
		return (val >> 3) & 7;
	}
	//  returns the column [0-7] from left to right; ie values in [a8-a8] return 0.
	public static int col(final int val)
	{
		return val % 8;
	}

	// returns string representation of the value; ie "d5"
	// @val positional value [0-63] - no check on valid range
	public static String toString(final int val)
	{
		return "" + ((char)(Pos.col(val) + (int)'a')) + "" + (8-Pos.row(val));
	}
	
	// returns string representation of the row of val - human represented so from bottom to top ["1"-"8"]
	public static String rowToString(final int val)
	{
		return "" + (8-Pos.row(val));	
	}
	// returns string representation of the column. ["a"-"h"]
	public static String colToString(final int val)
	{
		return "" + ((char)(Pos.col(val) + (int)'a'));
	}
}
