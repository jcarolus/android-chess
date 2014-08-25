#pragma once

#include "common.h"

#include "ChessBoard.h"

class Move
{
public:
	Move(void);
	~Move(void);

	// mask for a position [0-63], 6 bits.
	static const int MASK_POS = 0x3F;
	// mask for a boolean [false=0, true=1], 1 bit
	static const int MASK_BOOL = 1;
	// mask for a piece, 3 bits 0-7
	static const int MASK_PIECE = 0x7; 
	
	// shift values
	static const int SHIFT_TO = 6;
	static const int SHIFT_EP = 13;
	// short castling OO
	static const int SHIFT_OO = 14;
	// long castling OOO
	static const int SHIFT_OOO = 15;
	static const int SHIFT_HIT = 16;
	// is the first 2 step move of a pawn
	static const int SHIFT_FIRSTPAWN = 17;
	// with this move the opponent king is checked
	static const int SHIFT_CHECK = 18;
	// a pawn is promoted with this move
	static const int SHIFT_PROMOTION = 19;
	// the piece the pawn is promoted to
	static const int SHIFT_PROMOTIONPIECE = 20;
	// put a piece on the board, a "house" move. the piece is stored in promotion
	static const int SHIFT_HOUSE = 24;
	//
	
	// returns the integer representation of the simpelest move, from
	// position @from to position @to

#define Move_makeMove(from, to) (from | (to << Move::SHIFT_TO))

#define Move_makeMoveFirstPawn(from, to) (from | (to << Move::SHIFT_TO) | (1 << Move::SHIFT_FIRSTPAWN))

#define Move_makeMoveHit(from, to) (from | (to << Move::SHIFT_TO) | (1 << Move::SHIFT_HIT))

#define Move_makeMoveEP(from, to) (from | (to << Move::SHIFT_TO) | (1 << Move::SHIFT_HIT) | (1 << Move::SHIFT_EP))

#define Move_makeMoveOO(from, to) (from | (to << Move::SHIFT_TO) | (1 << Move::SHIFT_OO))

#define Move_makeMoveOOO(from, to) (from | (to << Move::SHIFT_TO) | (1 << Move::SHIFT_OOO))

#define Move_makeMovePromotion(from, to, piece, bHit) (from | (to << Move::SHIFT_TO) | (1 << Move::SHIFT_PROMOTION) | (piece << Move::SHIFT_PROMOTIONPIECE) | (bHit ? (1 << Move::SHIFT_HIT) : 0))

#define Move_makeMoveHouse(pos, piece) ((pos << Move::SHIFT_TO) | (piece << Move::SHIFT_PROMOTIONPIECE) | (1 << Move::SHIFT_HOUSE))

#define Move_setCheck(move) (move | (1 << Move::SHIFT_CHECK))

// returns true when "from" and "to" are equal in both arguments
#define Move_equalPositions(m, m2) ((m & Move::MASK_POS) == (m2 & Move::MASK_POS) && ((m >> Move::SHIFT_TO) & Move::MASK_POS) == ((m2 >> Move::SHIFT_TO) & Move::MASK_POS))

// return true when "to" in both arguments are equal
#define Move_equalTos(m, m2) (((m >> Move::SHIFT_TO) & Move::MASK_POS) == ((m2 >> Move::SHIFT_TO) & Move::MASK_POS))

// returns "from" of the move
#define Move_getFrom(move) (move & Move::MASK_POS)

#define Move_getTo(move) ((move >> Move::SHIFT_TO) & Move::MASK_POS)

#define Move_isEP(move) (((move >> Move::SHIFT_EP) & Move::MASK_BOOL) == Move::MASK_BOOL)

#define Move_isOO(move) (((move >> Move::SHIFT_OO) & Move::MASK_BOOL) == Move::MASK_BOOL)

#define Move_isOOO(move) (((move >> Move::SHIFT_OOO) & Move::MASK_BOOL) == Move::MASK_BOOL)

#define Move_isHIT(move) (((move >> Move::SHIFT_HIT) & Move::MASK_BOOL) == Move::MASK_BOOL)

#define Move_isCheck(move) (((move >> Move::SHIFT_CHECK) & Move::MASK_BOOL) == Move::MASK_BOOL)

#define Move_isFirstPawnMove(move) (((move >> Move::SHIFT_FIRSTPAWN) & Move::MASK_BOOL) == Move::MASK_BOOL)

#define Move_isPromotionMove(move) (((move >> Move::SHIFT_PROMOTION) & Move::MASK_BOOL) == Move::MASK_BOOL)

#define Move_getPromotionPiece(move) ((move >> Move::SHIFT_PROMOTIONPIECE) & Move::MASK_PIECE)

#define Move_isHouseMove(move) (((move >> Move::SHIFT_HOUSE) & Move::MASK_BOOL) == Move::MASK_BOOL)

        static void toDbgString(const int move, char* buf);
};
