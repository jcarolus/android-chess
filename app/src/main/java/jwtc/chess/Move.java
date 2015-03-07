package jwtc.chess;

// Move. Wrapper class for the integer representation of a move
// The positional values "from" and "to" are shifted into the integer.
// Also en-passant, castling, hit, first pawn move, promotion and promotion piece
// are part of the move.

public class Move
{
	// mask for a position [0-63], 6 bits.
	private static final int MASK_POS = 0x3F;
	// mask for a boolean [false=0, true=1], 1 bit
	private static final int MASK_BOOL = 1;
	
	// shift values
	private static final int SHIFT_TO = 6;
	private static final int SHIFT_EP = 13;
	// short castling OO
	private static final int SHIFT_OO = 14;
	// long castling OOO
	private static final int SHIFT_OOO = 15;
	private static final int SHIFT_HIT = 16;
	// is the first 2 step move of a pawn
	private static final int SHIFT_FIRSTPAWN = 17;
	// with this move the opponent king is checked
	private static final int SHIFT_CHECK = 18;
	// a pawn is promoted with this move
	private static final int SHIFT_PROMOTION = 19;
	// the piece the pawn is promoted to
	private static final int SHIFT_PROMOTIONPIECE = 20;
	
	// returns the integer representation of the simpelest move, from
	// position @from to position @to
	public static final int makeMove(final int from, final int to)
	{
		return from | (to << SHIFT_TO);
	}
	public static final int makeMoveFirstPawn(final int from, final int to)
	{
		return from | (to << SHIFT_TO) | (1 << SHIFT_FIRSTPAWN);
	}
	public static final int makeMoveHit(final int from, final int to)
	{
		//co.pl("makeMovehit " + from + "-" + to);
		return from | (to << SHIFT_TO) | (1 << SHIFT_HIT);
	}
	public static final int makeMoveEP(final int from, final int to)
	{
		return from | (to << SHIFT_TO) | (1 << SHIFT_HIT) | (1 << SHIFT_EP);
	}
	public static final int makeMoveOO(final int from, final int to)
	{
		return from | (to << SHIFT_TO) | (1 << SHIFT_OO);
	}
	public static final int makeMoveOOO(final int from, final int to)
	{
		return from | (to << SHIFT_TO) | (1 << SHIFT_OOO);
	}
	public static final int makeMovePromotion(final int from, final int to, final int piece, boolean bHit)
	{
		return from | (to << SHIFT_TO) | (1 << SHIFT_PROMOTION) | (piece << SHIFT_PROMOTIONPIECE) | (bHit == true ? (1 << SHIFT_HIT) : 0);
	}
	public static final int setCheck(final int move)
	{
		return move | (1 << SHIFT_CHECK);
	}
	// returns true when "from" and "to" are equal in both arguments
	public static final boolean equalPositions(final int m, final int m2)
	{
		return (m & MASK_POS) == (m2 & MASK_POS) && ((m >> SHIFT_TO) & MASK_POS) == ((m2 >> SHIFT_TO) & MASK_POS);
	}
	// return true when "to" in both arguments are equal
	public static final boolean equalTos(int m, int m2)
	{
		return ((m >> SHIFT_TO) & MASK_POS) == ((m2 >> SHIFT_TO) & MASK_POS);
	}

	// returns "from" of the move
	public static final int getFrom(final int move)
	{
		return move & MASK_POS;
	}
	public static final int getTo(final int move)
	{
		return (move >> SHIFT_TO) & MASK_POS;
	}
	public static final boolean isEP(final int move)
	{
		return ((move >> SHIFT_EP) & MASK_BOOL) == MASK_BOOL;
	}
	public static final boolean isOO(final int move)
	{
		return ((move >> SHIFT_OO) & MASK_BOOL) == MASK_BOOL;
	}
	public static final boolean isOOO(final int move)
	{
		return ((move >> SHIFT_OOO) & MASK_BOOL) == MASK_BOOL;
	}
	public static final boolean isHIT(final int move)
	{
		return ((move >> SHIFT_HIT) & MASK_BOOL) == MASK_BOOL;
	}
	public static final boolean isCheck(final int move)
	{
		return ((move >> SHIFT_CHECK) & MASK_BOOL) == MASK_BOOL;
	}
	public static final boolean isFirstPawnMove(final int move)
	{
		return ((move >> SHIFT_FIRSTPAWN) & MASK_BOOL) == MASK_BOOL;
	}
	public static final boolean isPromotionMove(final int move)
	{
		return ((move >> SHIFT_PROMOTION) & MASK_BOOL) == MASK_BOOL;
	}
	public static final int getPromotionPiece(final int move)
	{
		return move >> SHIFT_PROMOTIONPIECE;
	}

	// returns pgn alike string representation of the move - not full pgn format because then more information is needed
	public static final String toDbgString(final int move)
	{
		if(Move.isOO(move))
			return "O-O";
		if(Move.isOOO(move))
			return "O-O-O";
		return "[" + Pos.toString(Move.getFrom(move)) + (Move.isHIT(move) ? "x" : "-") + Pos.toString(Move.getTo(move)) + (Move.isCheck(move) ? "+" : "") + (Move.isEP(move) ? " ep" : "") + "]";
	}
	
}
