#include "ChessBoard.h"

#include <math.h>

ChessBoard::ChessBoard(void)
{
	m_parent = NULL;
	reset();
}

ChessBoard::~ChessBoard(void)
{
	//if(m_parent)
	//	delete m_parent;
}

////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
// attack functions - used to find out if a king is checked - so no special
// moves like en-passant and castlings have to be generated 
// return a bitb with bits set to 1 where the piece can move to
/*
BITBOARD ChessBoard::pawnAttacks(const int turn, const BITBOARD bb)
{
	// no en-passant, no promotion, only for detection of attacked squares
	// TODO with file BITS and other "BITS_pawn[pos]" this can be improved
         old
	BITBOARD bbTmp = 0L;
	int o_turn = turn ^1;

	if(turn == WHITE)
	{
		bbTmp = ((bb & ~FILE_BITS[0]) >> 9) & m_bitbPositions[o_turn];
		bbTmp |= ((bb & ~FILE_BITS[7]) >> 7) & m_bitbPositions[o_turn];
		return bbTmp;
	}
	else
	{
		bbTmp = ((bb & ~FILE_BITS[7]) << 9) & m_bitbPositions[o_turn]; 
		bbTmp |= ((bb & ~FILE_BITS[0]) << 7) & m_bitbPositions[o_turn];
		return bbTmp;
	}

}
*/
BITBOARD ChessBoard::knightAttacks(const int turn, BITBOARD bb)
{
	int pos; BITBOARD bbRet = 0;
	while(bb != 0)
	{
		pos = ChessBoard::trailingZeros(bb);
		bb &= NOT_BITS[pos];
		bbRet |= knightMoves(turn, pos);
	}
	return bbRet;
}
BITBOARD ChessBoard::bishopAttacks(const int turn, BITBOARD bb)
{
	int pos; BITBOARD bbRet = 0;
	while(bb != 0)
	{
		pos = ChessBoard::trailingZeros(bb);
		bb &= NOT_BITS[pos];
		bbRet |= bishopMoves(turn, pos);
	}
	return bbRet;
}
BITBOARD ChessBoard::rookAttacks(const int turn, BITBOARD bb)
{
	int pos; BITBOARD bbRet = 0;
	while(bb != 0)
	{
		pos = ChessBoard::trailingZeros(bb);
		bb &= NOT_BITS[pos];
		bbRet |= rookMoves(turn, pos);
	}
	return bbRet;
}
BITBOARD ChessBoard::queenAttacks(const int turn, BITBOARD bb)
{
	int pos; BITBOARD bbRet = 0;
	while(bb != 0)
	{
		pos = ChessBoard::trailingZeros(bb);
		bb &= NOT_BITS[pos];
		bbRet |= queenMoves(turn, pos);
	}
	return bbRet;
}
BITBOARD ChessBoard::kingAttacks(const int turn, BITBOARD bb)
{
	int pos; BITBOARD bbRet = 0;
	while(bb != 0)
	{
		pos = ChessBoard::trailingZeros(bb);
		bb &= NOT_BITS[pos];
		bbRet |= kingMoves(turn, pos);
	}
	return bbRet;
}

////////////////////////////////////////////////////////////////////////////////
// returns true when square of turn is attacked - it does not matter if there
// is nothing at @pos
////////////////////////////////////////////////////////////////////////////////
boolean ChessBoard::isSquareAttacked(const int turn, const int pos)
{
	BITBOARD square = BITS[pos];
	//co.pl("turn" + turn + " pos " + pos);
	//co.pl(bitbToString(bb));
	//co.pl(bitbToString(m_bitbPieces[turn^1][QUEEN] & bb));
	int o_turn = turn^1;
	
	if((m_bitbPieces[o_turn][KNIGHT] & KNIGHT_RANGE[pos]) != 0)
		return true;

        // except the knight, all pieces work within queen range
        if((QUEEN_RANGE[pos] & m_bitbPositions[o_turn]) != 0){

            // todo - is saving a method call really an improvement?

            if((BISHOP_RANGE[pos] & m_bitbPieces[o_turn][BISHOP]) != 0){
                if((bishopAttacks(o_turn, m_bitbPieces[o_turn][BISHOP] & BISHOP_RANGE[pos]) & square) != 0){
                    return true;
                }
            }
            if((QUEEN_RANGE[pos] & m_bitbPieces[o_turn][QUEEN]) != 0){
                if((queenAttacks(o_turn, m_bitbPieces[o_turn][QUEEN] & QUEEN_RANGE[pos]) & square) != 0)
                    return true;
            }
            if((ROOK_RANGE[pos] & m_bitbPieces[o_turn][ROOK]) != 0){
                if((rookAttacks(o_turn, m_bitbPieces[o_turn][ROOK] & ROOK_RANGE[pos]) & square) != 0)
                    return true;
            }
            // use the pos square for the pawn range, so from direction of turn
            if((m_bitbPieces[o_turn][PAWN] & PAWN_RANGE[turn][pos]) != 0)
                    return true;
            //TODO more efficient pawn attack range (instead of king_range)
            //if((pawnAttacks(o_turn, m_bitbPieces[o_turn][PAWN] & KING_RANGE[pos]) & square) != 0)
            //	return true;

            if((m_bitbPieces[o_turn][KING] & KING_RANGE[pos]) != 0)
                    return true;
        }
	return false;
}

////////////////////////////////////////////////////////////////////////////////
// returns bitb of all attack moves. unused 
////////////////////////////////////////////////////////////////////////////////
/*
BITBOARD ChessBoard::getAttacks(const int turn)
{
	return pawnAttacks(turn, m_bitbPieces[turn][PAWN]) |
		   knightAttacks(turn, m_bitbPieces[turn][KNIGHT]) |
		   bishopAttacks(turn, m_bitbPieces[turn][BISHOP]) |
		   rookAttacks(turn, m_bitbPieces[turn][ROOK]) |
		   queenAttacks(turn, m_bitbPieces[turn][QUEEN]) |
		   kingAttacks(turn, m_bitbPieces[turn][KING]);
}
*/

////////////////////////////////////////////////////////////////////////////////
// calculate the state of the board
// generate all moves - filter the moves that lead to illegal board situations 
// like (self check)
// when a move generates a board in which the opponent king is attacked, set 
// the move to a "checking" move
////////////////////////////////////////////////////////////////////////////////
void ChessBoard::calcState(ChessBoard* board)
{
	if(isEnded())
		return;
	genMoves();

	int move; 
	m_indexMoves = 0;
	while(hasMoreMoves())
	{
		move = getNextMove();
		
		//char buf[20];
		//Move::toDbgString(move, buf);
		//DEBUG_PRINT(buf);
		//co.pl("Filtering " + );

		makeMove(move, board);

		// check if king is attacked - since a move is done, this is in m_o_kingPos
		if(board->isSquareAttacked(m_turn, board->m_o_kingPos))
		{
			//co.pl("removed");
			removeMoveElementAt();
		}
		else
		{
			// check if opponent king is checked
			if(board->isSquareAttacked(m_o_turn, board->m_kingPos))
			{	
				//co.pl("CHECK! " + m_indexMoves);
				// set checked flag in move - so in makeMove m_state can be set to check
				m_arrMoves[m_indexMoves-1] = Move_setCheck(move);
			}
		}
	}

	//co.pl("attackedbits ");
	//co.pl(ChessBoard::bitbToString(m_bitbAttackMoveSquares));
	//co.pl("After filter " + m_sizeMoves + " - state " + m_state);

	// game over when no moves left
	if(m_sizeMoves == 0)
	{
		// state set to check in makeMove
		if(m_state == CHECK)
		{
			m_state = MATE;
			//co.pl("MATE");
		}
		else
		{
			m_state = STALEMATE;
			//co.pl("STALEMATE");
		}
	}
	//printB();
}
// this is called from search, so check for king of turn
boolean ChessBoard::checkInCheck()
{
    // working:
    if(isSquareAttacked(m_turn, m_kingPos)){
	
    //if(m_bitbAttackMoveSquares & BITS[m_o_kingPos]){
        // todo consider not taking king moves in attackmovessquares?
    //    if((KING_RANGE[m_kingPos] & BITS[m_o_kingPos]) == 0){

            m_state = CHECK;
            return true;
    //    }
    }
    return false;
}
// this is called from search, so check for opponent king!
// only valid result if called after genmoves
boolean ChessBoard::checkInSelfCheck()
{
    // orig:
    //return (m_bitbAttackMoveSquares & BITS[m_o_kingPos]);
    // working
    return isSquareAttacked(m_o_turn, m_o_kingPos);
}
	
int ChessBoard::getState()
{
	return m_state;
}
boolean ChessBoard::isLegalPosition()
{
    if((m_bitbPieces[WHITE][PAWN] & (ROW_BITS[0] | ROW_BITS[7])) ||
       (m_bitbPieces[BLACK][PAWN] & (ROW_BITS[0] | ROW_BITS[7])))
    {
        return false;
    }
    if(ChessBoard::bitCount(m_bitbPieces[WHITE][PAWN]) > 8 ||
       ChessBoard::bitCount(m_bitbPieces[BLACK][PAWN]) > 8)
    {
        return false;
    }
    if(isSquareAttacked(m_o_turn, m_o_kingPos))
    {
        return false;
    }
    return true;
}
/*
String ChessBoard::getStateToString()
{
	String msg = "";
	switch(m_state)
	{
	case ChessBoard::MATE: msg = "Mate"; break;
	case ChessBoard::DRAW_MATERIAL: msg = "Draw (material)"; break;
	case ChessBoard::CHECK: msg = "Check"; break;
	case ChessBoard::STALEMATE: msg = "Draw (stalemate)"; break;
	case ChessBoard::DRAW_50: msg = "Draw (50 move rule)"; break;
	case ChessBoard::DRAW_REPEAT: msg = "Draw (repeat)"; break;
	default: msg = "In play"; break;
	}
	return msg;
}
*/

////////////////////////////////////////////////////////////////////////////////
// returns true when the game is ended
// TODO repeat check can be made more efficient with repeat index of hashkey
////////////////////////////////////////////////////////////////////////////////
boolean ChessBoard::isEnded()
{
	if(m_state == MATE || m_state == STALEMATE){
		return true;
        }
	// TODO skip this when m_state == CHECK?
	if(m_50RuleCount == 100)
	{
		m_state = DRAW_50;
		return true;
	}
	//check DRAW by material
        // first check for no pawns, rooks and queens on either side.
        // ASSUME: value of knight or bishop is never twice as big as the other
        if(m_bitbPieces[m_turn][PAWN] == 0  && m_bitbPieces[m_o_turn][PAWN]  == 0 &&
           m_bitbPieces[m_turn][ROOK] == 0  && m_bitbPieces[m_o_turn][ROOK]  == 0 &&
           m_bitbPieces[m_turn][QUEEN] == 0 && m_bitbPieces[m_o_turn][QUEEN] == 0)
        {
            // KNk or KBk is draw
            if(m_o_quality == 0 &&
		(m_quality <= ChessBoard::PIECE_VALUES[KNIGHT] || m_quality <= ChessBoard::PIECE_VALUES[BISHOP]))
            {
                m_state = ChessBoard::DRAW_MATERIAL;
		return true;
            }
            if(m_quality == 0 &&
               (m_o_quality <= ChessBoard::PIECE_VALUES[KNIGHT] || m_o_quality <= ChessBoard::PIECE_VALUES[BISHOP]))
            {
                m_state = ChessBoard::DRAW_MATERIAL;
		return true;
            }
            // also KNkn and KBkb, KBkn are almost always draw; theoretical mates only with king in corner (and own piece next to it)
            if((m_o_quality <= ChessBoard::PIECE_VALUES[KNIGHT] || m_o_quality <= ChessBoard::PIECE_VALUES[BISHOP]) &&
               (m_quality   <= ChessBoard::PIECE_VALUES[KNIGHT] || m_quality   <= ChessBoard::PIECE_VALUES[BISHOP]))
            {
                // test for either king NOT in a corner
                if(!(m_o_kingPos == a8 || m_o_kingPos == h8 || m_o_kingPos == a1 || m_o_kingPos == h1 ||
                     m_kingPos   == a8 || m_kingPos   == h8 || m_kingPos   == a1 || m_kingPos   == h1))
                {
                    m_state = ChessBoard::DRAW_MATERIAL;
                    return true;
                }
            }

        }


	//DRAW by repetition, no need to check with noHitcount < 4, because repetition needs at least 4 sequential moves
	// that can lead to the same position
	if(m_50RuleCount > 3 && m_parent != NULL)
	{
		//start at parent
		ChessBoard* tmpBoard = m_parent;
		int repeatCount = 0;
		while(tmpBoard != NULL)
		{
			if(tmpBoard->m_hashKey == m_hashKey)
				repeatCount++;
			if(repeatCount == 2)
			{
				m_state = ChessBoard::DRAW_REPEAT;
				return true;
			}
                        // after hit or pawn move never the same
			if(tmpBoard->m_50RuleCount == 0)
				break;

			tmpBoard = tmpBoard->m_parent;
		}
	}
	return false;
}

// called from search, a little different than isEnded (no MATE and STALEMATE)
boolean ChessBoard::checkEnded()
{
	if(m_50RuleCount == 100)
	{
		m_state = DRAW_50;
		return true;
	}
	if(m_50RuleCount > 3 && m_parent != NULL)
	{
		//start at parent
		ChessBoard* tmpBoard = m_parent->m_parent;
		int repeatCount = 0;
		while(tmpBoard != NULL)
		{
			
			if(tmpBoard->m_hashKey == m_hashKey)
				repeatCount++;
			if(repeatCount == 2)
			{
				m_state = ChessBoard::DRAW_REPEAT;
				return true;
			}
                        // after hit or pawn move never the same
			if(tmpBoard->m_50RuleCount == 0)
				break;
			tmpBoard = tmpBoard->m_parent;
		}
	}
	return false;
}


////////////////////////////////////////////////////////////////////////////////
// move related methods
////////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////////
// return 0 when move is not ambigious, otherwise the ambigious move.
// ambigous when pieces of the same kind move to the same destination
////////////////////////////////////////////////////////////////////////////////
int ChessBoard::ambigiousMove()
{
	if(m_parent != NULL)
	{
		ChessBoard* tmpBoard = new ChessBoard();
		m_parent->duplicate(tmpBoard);
		
		tmpBoard->m_indexMoves = 0;
		tmpBoard->genMoves(); // NEW, all (illegal) moves
		int m2;
		while(tmpBoard->hasMoreMoves())
		{
			m2 = tmpBoard->getNextMove();
			if(m_myMove != m2 && Move_equalTos(m2, m_myMove))
			{
				if(Move_getFrom(m2) != Move_getFrom(m_myMove) && (tmpBoard->m_bitbPieces[m_o_turn][PAWN] & BITS[Move_getFrom(m_myMove)]) == 0)
				{
					if(tmpBoard->pieceAt(m_o_turn, Move_getFrom(m_myMove)) == tmpBoard->pieceAt(m_o_turn, Move_getFrom(m2)))
					{
						delete tmpBoard;
						return m2;
					}
				}
			}
		}
		delete tmpBoard;
	}
	return 0;
}

////////////////////////////////////////////////////////////////////////////////
// returns true if the move is a valid move - the move will be made when valid
////////////////////////////////////////////////////////////////////////////////
boolean ChessBoard::requestMove(const int from, const int to, ChessBoard* board, ChessBoard* tmpBoard, int promoPiece)
{
	if(isEnded())
		return false;
	m_indexMoves = 0;
	int move;
	while(hasMoreMoves())
	{
		move = getNextMove();
		if(Move_isPromotionMove(move)){
			if(promoPiece != Move_getPromotionPiece(move))
				continue;
		}
		if(Move_equalPositions(move, Move_makeMove(from, to)))
		{

			makeMove(move, board);

			// requestMove is a definitive move, so calcState can be invoked
			board->calcState(tmpBoard);
			return true;
		}
	}
	return false;
}

////////////////////////////////////////////////////////////////////////////////
// Used in random fischer chess where a king move can be a plain move or a 
// castle
////////////////////////////////////////////////////////////////////////////////
boolean ChessBoard::isAmbiguousCastle(const int from, const int to){
	m_indexMoves = 0;
	int move, cnt = 0;

        if(abs(Pos::col(from) - Pos::col(to)) <= 1){

            while(hasMoreMoves())
            {
                    move = getNextMove();
                    if(Move_equalPositions(move, Move_makeMove(from, to))){
                            if(Move_isOO(move) || Move_isOOO(move)){
                                    cnt++;
                            }
                    }
            }
            return (cnt > 0);
        }
        return false;
}
////////////////////////////////////////////////////////////////////////////////
// return the move that is castling from the two provided positions
////////////////////////////////////////////////////////////////////////////////
int ChessBoard::getCastleMove(const int from, const int to){
	m_indexMoves = 0;
	int move; 
	while(hasMoreMoves())
	{
		move = getNextMove();
		if(Move_equalPositions(move, Move_makeMove(from, to))){
			if(Move_isOO(move) || Move_isOOO(move)){
				return move;
			}
		}
	}
	return 0;
}
////////////////////////////////////////////////////////////////////////////////
// iterator over all moves, if valid move, than make the move
////////////////////////////////////////////////////////////////////////////////
boolean ChessBoard::requestMove(const int m, ChessBoard* board, ChessBoard* tmpBoard)
{

	if(isEnded())
		return false;
	m_indexMoves = 0;
	int move;
	while(hasMoreMoves())
	{
		move = getNextMove();
		if(move == m)
		{

			makeMove(move, board);

			// requestMove is a definitive move, so calcState can be invoked
			board->calcState(tmpBoard);
			return true;
		}
	}
	return false;
}

////////////////////////////////////////////////////////////////////////////////
// make move @move, and initialize datastructure of board @ret to the new 
// position
////////////////////////////////////////////////////////////////////////////////
void ChessBoard::makeMove(const int move, ChessBoard* ret)
{
	//ret->reset();
	
	const int from = Move_getFrom(move), to = Move_getTo(move);
        const int pieceFrom = pieceAt(m_turn, from);

        // BOF COPY
        memcpy(ret, this, SIZEOF_BOARD);
        ret->m_bitbAttackMoveSquares = 0L;
        // EOF COPY

        // BOF COPY
        /*
	ret->m_hashKey = m_hashKey;
	
	ret->m_bitbAttackMoveSquares = 0L;
	
	ret->m_bitb = m_bitb;
	ret->m_bitb_45 = m_bitb_45;
	ret->m_bitb_90 = m_bitb_90;
	ret->m_bitb_315 = m_bitb_315;
	
	for(int i = 0; i < NUM_PIECES; i++)
	{
		ret->m_bitbPieces[BLACK][i] = this->m_bitbPieces[BLACK][i];
		ret->m_bitbPieces[WHITE][i] = this->m_bitbPieces[WHITE][i];
	}
	ret->m_bitbPositions[m_turn] = m_bitbPositions[m_turn];
	ret->m_bitbPositions[m_o_turn] = m_bitbPositions[m_o_turn];

	ret->m_castlings[BLACK] = m_castlings[BLACK];
	ret->m_castlings[WHITE] = m_castlings[WHITE];
        */
	// EOF COPYY

        // if it is a 'first pawn move' set ep square
        if(Move_isFirstPawnMove(move))
	{
		ret->m_ep = to + (m_turn == WHITE ? 8 : -8);
		//co.pl("Setting EP " + to + " from move " + Move::toDbgString(move));
	}
	else
		ret->m_ep = -1;

        // keep count of the board
	ret->m_numBoard = m_numBoard+1;
	
	if(Move_isCheck(move))
		ret->m_state = CHECK;
        else
            ret->m_state = PLAY; // not the final state, but different to this
        //

        // administration for castling
	if(pieceFrom == ROOK)
	{
		if(COL[from] == ChessBoard::COL_AROOK)
                {
			ret->m_castlings[m_turn] = m_castlings[m_turn] | MASK_AROOK;
                }
		else if(COL[from] == ChessBoard::COL_HROOK)
                {
			ret->m_castlings[m_turn] = m_castlings[m_turn] | MASK_HROOK;
                }
	}
        // administration for king position
        if(pieceFrom == KING)
	{
		ret->m_castlings[m_turn] = m_castlings[m_turn] | MASK_KING;
		ret->m_kingPos = m_o_kingPos;
		ret->m_o_kingPos = to;
	}
	else
	{
		ret->m_kingPos = m_o_kingPos;
		ret->m_o_kingPos = m_kingPos;
	}

	// debug "assertation"
	//TODO remove (is a result from incorrect administration of m_pieces)
	// status is fixed, but since final makeMove is not done yet, leave this code
        /*
	if((m_bitbPositions[m_turn] & BITS[from]) == 0)
	{
		//co.pl("MakeMove pieces = -1 at move " + Move::toDbgString(move));
		//printB();
		//System.exit(0);
	}
        */
	
	if(Move_isHIT(move))
	{
		ret->m_50RuleCount = 0;
		ret->m_o_quality = m_quality;
		
		// separate handling for en-passant, because the captured piece is not on the to square
		if(Move_isEP(move))
		{
			ret->m_quality = m_o_quality - ChessBoard::PIECE_VALUES[PAWN];
			if(m_turn == WHITE)
			{
				ret->m_hashKey ^= HASH_KEY[m_o_turn][PAWN][to+8];
				ret->m_bitbPositions[m_o_turn] &= NOT_BITS[to+8];
				ret->m_bitbPieces[m_o_turn][PAWN] &= NOT_BITS[to+8];
				
				ret->m_bitb &= NOT_BITS[to+8];
				ret->m_bitb_45 &= ~ROT_45_BITS[to+8];
				ret->m_bitb_90 &= ~ROT_90_BITS[to+8];
				ret->m_bitb_315 &= ~ROT_315_BITS[to+8];
			}
			else
			{
				ret->m_hashKey ^= HASH_KEY[m_o_turn][PAWN][to-8];
				ret->m_bitbPositions[m_o_turn] &= NOT_BITS[to-8];
				ret->m_bitbPieces[m_o_turn][PAWN]  &= NOT_BITS[to-8];
				
				ret->m_bitb &= NOT_BITS[to-8];
				ret->m_bitb_45 &= ~ROT_45_BITS[to-8];
				ret->m_bitb_90 &= ~ROT_90_BITS[to-8];
				ret->m_bitb_315 &= ~ROT_315_BITS[to-8];
			}
		}
		else
		{
			// normal hit
                        const int pieceTo = pieceAt(m_o_turn, to);
			ret->m_hashKey ^= HASH_KEY[m_o_turn][pieceTo][to];
			ret->m_quality = m_o_quality - ChessBoard::PIECE_VALUES[pieceTo];
			ret->m_bitbPieces[m_o_turn][pieceTo] &= NOT_BITS[to];
			ret->m_bitbPositions[m_o_turn] &= NOT_BITS[to];
			
			// bitb and rotated bitb's do not have to be changed here
		}
	}
	else // not a hit
	{
		ret->m_o_quality = m_quality;
		ret->m_quality = m_o_quality;
		if((m_bitbPieces[m_turn][PAWN] & BITS[from]) != 0)
			ret->m_50RuleCount = 0;
		else
			ret->m_50RuleCount = m_50RuleCount + 1;
	}
	
	//////////////////////////////////////////////////////////////////////////////////
	// ? set to, first check for random Fischer castling where king goes to rook square?

        // set to

	ret->m_hashKey ^= HASH_KEY[m_turn][pieceFrom][to];
	ret->m_bitbPieces[m_turn][pieceFrom] |= BITS[to];
	ret->m_bitbPositions[m_turn] |= BITS[to];
	ret->m_bitb |= BITS[to];
	ret->m_bitb_45 |= ROT_45_BITS[to];
	ret->m_bitb_90 |= ROT_90_BITS[to];
	ret->m_bitb_315 |= ROT_315_BITS[to];
	//////////////////////////////////////////////////////////////////////////////////
	// clean up from
	if(from != to){ // if from == to, when castling in random Fischer it is possible the kings stays
		ret->m_hashKey ^= HASH_KEY[m_turn][pieceFrom][from];
		ret->m_bitbPieces[m_turn][pieceFrom] &= NOT_BITS[from];
		ret->m_bitbPositions[m_turn] &= NOT_BITS[from];
		
		ret->m_bitb &= NOT_BITS[from];
		ret->m_bitb_45 &= ~ROT_45_BITS[from];
		ret->m_bitb_90 &= ~ROT_90_BITS[from];
		ret->m_bitb_315 &= ~ROT_315_BITS[from];
	}
	///////////////////////////////////////////////////////////////////////////////////
	// replace promotion piece
	if(Move_isPromotionMove(move))
	{
		ret->m_o_quality += ChessBoard::PIECE_VALUES[Move_getPromotionPiece(move)];
		ret->m_bitbPieces[m_turn][Move_getPromotionPiece(move)] |= BITS[to];
		ret->m_bitbPieces[m_turn][PAWN] &= NOT_BITS[to];
		ret->m_hashKey ^= HASH_KEY[m_turn][PAWN][to];
		ret->m_hashKey ^= HASH_KEY[m_turn][Move_getPromotionPiece(move)][to];
	}
	
	////////////////////////////////////////////////////////////////////////////////////
	// 'extra' move for short or long castling
	if(Move_isOO(move))
	{
                ret->m_castlings[m_turn] = m_castlings[m_turn] | MASK_CASTLED | MASK_KING;
		if(m_turn == WHITE)
		{
			int rookFrom = h1;
			if(ChessBoard::COL_HROOK != 7){ // random Fisher castling
				rookFrom = from+1;
				while(rookFrom <= h1){
					if(COL[rookFrom] == ChessBoard::COL_HROOK)
						break;
					rookFrom++;
				}
				// assert rookFrom <= h1
			}
			// only move if rook is not already on spot (random fischer)
			if(rookFrom != f1){
				ret->m_bitbPositions[m_turn] |= BITS[f1]; // already done that? => no; to = pos of king
				ret->m_bitbPieces[m_turn][ROOK] |= BITS[f1];
				ret->m_bitbPieces[m_turn][ROOK] &= NOT_BITS[rookFrom];
				ret->m_bitb |= BITS[f1];
				ret->m_bitb_45 |= ROT_45_BITS[f1];
				ret->m_bitb_90 |= ROT_90_BITS[f1];
				ret->m_bitb_315 |= ROT_315_BITS[f1];
				
				// if king is not on this square!
				if((ret->m_bitbPieces[m_turn][KING] & BITS[rookFrom]) == 0){
					ret->m_bitbPositions[m_turn] &= NOT_BITS[rookFrom];
					ret->m_bitb &= NOT_BITS[rookFrom];
					ret->m_bitb_45 &= ~ROT_45_BITS[rookFrom];
					ret->m_bitb_90 &= ~ROT_90_BITS[rookFrom];
					ret->m_bitb_315 &= ~ROT_315_BITS[rookFrom];
				}
			}
			ret->m_hashKey ^= HASH_OO[WHITE];
		}
		else
		{
			int rookFrom = h8;
			if(ChessBoard::COL_HROOK != 7){ // random Fisher castling
				rookFrom = from+1;
				while(rookFrom <= h8){
					if(COL[rookFrom] == ChessBoard::COL_HROOK)
						break;
					rookFrom++;
				}
				// assert rookFrom <= h1
			}
			// only move if rook is not already on spot
			if(rookFrom != f8){
				ret->m_bitbPositions[m_turn] |= BITS[f8];
				
				ret->m_bitbPieces[m_turn][ROOK] |= BITS[f8];
				ret->m_bitbPieces[m_turn][ROOK] &= NOT_BITS[rookFrom];
				ret->m_bitb |= BITS[f8];
				ret->m_bitb_45 |= ROT_45_BITS[f8];
				ret->m_bitb_90 |= ROT_90_BITS[f8];
				ret->m_bitb_315 |= ROT_315_BITS[f8];
				
				// if king is not on this square!
				if((ret->m_bitbPieces[m_turn][KING] & BITS[rookFrom]) == 0){
					ret->m_bitbPositions[m_turn] &= NOT_BITS[rookFrom];
					ret->m_bitb &= NOT_BITS[rookFrom];
					ret->m_bitb_45 &= ~ROT_45_BITS[rookFrom];
					ret->m_bitb_90 &= ~ROT_90_BITS[rookFrom];
					ret->m_bitb_315 &= ~ROT_315_BITS[rookFrom];
				}
			}
			ret->m_hashKey ^= HASH_OO[BLACK];
		}
	}
	else if(Move_isOOO(move))
	{
                ret->m_castlings[m_turn] = m_castlings[m_turn] | MASK_CASTLED | MASK_KING;
		if(m_turn == WHITE)
		{
			int rookFrom = a1;
			if(ChessBoard::COL_AROOK != 0){ // random Fisher castling
				rookFrom = from-1;
				while(rookFrom >= a1){
					if(COL[rookFrom] == ChessBoard::COL_AROOK)
						break;
					rookFrom--;
				}
				// assert rookFrom <= h1
			}
			// only move if rook is not already on spot
			if(rookFrom != d1){
				ret->m_bitbPositions[m_turn] |= BITS[d1];
				ret->m_bitbPieces[m_turn][ROOK] |= BITS[d1];
				ret->m_bitbPieces[m_turn][ROOK] &= NOT_BITS[rookFrom];
				ret->m_bitb |= BITS[d1];
				ret->m_bitb_45 |= ROT_45_BITS[d1];
				ret->m_bitb_90 |= ROT_90_BITS[d1];
				ret->m_bitb_315 |= ROT_315_BITS[d1];
				
				// if king is not on this square!
				if((ret->m_bitbPieces[m_turn][KING] & BITS[rookFrom]) == 0){
					ret->m_bitbPositions[m_turn] &= NOT_BITS[rookFrom];
					ret->m_bitb &= NOT_BITS[rookFrom];
					ret->m_bitb_45 &= ~ROT_45_BITS[rookFrom];
					ret->m_bitb_90 &= ~ROT_90_BITS[rookFrom];
					ret->m_bitb_315 &= ~ROT_315_BITS[rookFrom];
				}
			}
			ret->m_hashKey ^= HASH_OOO[WHITE];
		}
		else
		{
			int rookFrom = a8;
			if(ChessBoard::COL_AROOK != 0){ // random Fisher castling
				rookFrom = from-1;
				while(rookFrom >= a8){
					if(COL[rookFrom] == ChessBoard::COL_AROOK)
						break;
					rookFrom--;
				}
				// assert rookFrom <= h1
			}
			// only move if rook is not already on spot
			if(rookFrom != d8){
				ret->m_bitbPositions[m_turn] |= BITS[d8];
				ret->m_bitbPieces[m_turn][ROOK] |= BITS[d8];
				ret->m_bitbPieces[m_turn][ROOK] &= NOT_BITS[rookFrom];
				ret->m_bitb |= BITS[d8];
				ret->m_bitb_45 |= ROT_45_BITS[d8];
				ret->m_bitb_90 |= ROT_90_BITS[d8];
				ret->m_bitb_315 |= ROT_315_BITS[d8];
				
				// if king is not on this square!
				if((ret->m_bitbPieces[m_turn][KING] & BITS[rookFrom]) == 0){
					ret->m_bitbPositions[m_turn] &= NOT_BITS[rookFrom];
					ret->m_bitb &= NOT_BITS[rookFrom];
					ret->m_bitb_45 &= ~ROT_45_BITS[rookFrom];
					ret->m_bitb_90 &= ~ROT_90_BITS[rookFrom];
					ret->m_bitb_315 &= ~ROT_315_BITS[rookFrom];
				}
			}
			ret->m_hashKey ^= HASH_OOO[BLACK];
		}
	}
	
	/*
	co.pl("[[[----ps\n" + ret->piecesToString());
	co.pl("----bb");
	co.pl(ChessBoard::bitbToString(ret->m_bitb));
	co.pl("----bbt");
	co.pl(ChessBoard::bitbToString(ret->m_bitbPositions[m_turn]));
	co.pl("----bbot");
	co.pl(ChessBoard::bitbToString(ret->m_bitbPositions[m_o_turn]));
	co.pl("----]]]");
	*/

	// finalize
	ret->m_parent = this;
	ret->m_myMove = move;
	
	ret->m_o_turn = this->m_turn;
	ret->m_turn = this->m_o_turn;
	ret->m_hashKey ^= HASH_TURN;
	
	
	// debug
	//ret->printB();
}


////////////////////////////////////////////////////////////////////////////////
// returns true when one of the moves in m_arrMoves is a hit, check or 
// promotion move
////////////////////////////////////////////////////////////////////////////////
boolean ChessBoard::containsQuiescenceMove()
{
	int i, move;
	for(i = 0; i < m_sizeMoves; i++)
	{
		move = m_arrMoves[i];
		if(Move_isHIT(move) || Move_isCheck(move) || Move_isPromotionMove(move))
		{
			return true;
		}
	}
	return false;
}

////////////////////////////////////////////////////////////////////////////////
// methods that add moves to m_arrMoves

////////////////////////////////////////////////////////////////////////////////
// add the moves from position @from to the positions in bitb @bb
// the attackedsquares bitb is updated
////////////////////////////////////////////////////////////////////////////////
void ChessBoard::addMoves(const int from, BITBOARD bb)
{
	int to; 
	//co.pl("addmoves " + from);
	//co.pl(ChessBoard::bitbToString(bb));
	
	m_bitbAttackMoveSquares |= bb;
	while(bb != 0)
	{
		to = ChessBoard::trailingZeros(bb);
		
		//co.pl("AddMove from " + from + " to " + to);
		bb &= NOT_BITS[to];
		if((m_bitbPositions[m_o_turn] & BITS[to]) != 0)
		{
			if((m_bitbPieces[m_o_turn][KING] & BITS[to]) != 0)
			{
				//co.pl("1 INVALID STATE FROM MOVE -- " + from + " - " + to);
				return;
			}
			else
				addMoveElement(Move_makeMoveHit(from, to));
		}
		else
			addMoveElement(Move_makeMove(from, to));
	}
}
void ChessBoard::addKingMove(const int move)
{
	addMoveElement(move);
	m_bitbAttackMoveSquares |= BITS[Move_getTo(move)];
}

void ChessBoard::addPawnCaptureMove(const int move)
{
	addMoveElement(move);
	m_bitbAttackMoveSquares |= BITS[Move_getTo(move)];
}

void ChessBoard::addMove(const int from, const int to)
{
	if((m_bitbPositions[m_o_turn] & BITS[to]) != 0)
	{
		if((m_bitbPieces[m_o_turn][KING] & BITS[to]) != 0)
		{
			//co.pl("2 INVALID STATE FROM MOVE -- " + from + " - " + to);
			return;
		}
	}
	m_bitbAttackMoveSquares |= BITS[to];
	addMoveElement(Move_makeMove(from, to));
}

////////////////////////////////////////////////////////////////////////////////
// return the move that lead to this board
////////////////////////////////////////////////////////////////////////////////
int ChessBoard::getMyMove()
{
	return m_myMove;
}
////////////////////////////////////////////////////////////////////////////////
// generate all moves
////////////////////////////////////////////////////////////////////////////////
void ChessBoard::genMoves()
{
	m_sizeMoves = 0;
	m_indexMoves = 0;
	genKnightMoves();
	genBishopMoves();
	genPawnMoves();
	genRookMoves();
	genQueenMoves();
	genKingMoves();
}

////////////////////////////////////////////////////////////////////////////////
// move generation methods


////////////////////////////////////////////////////////////////////////////////
void ChessBoard::genMovesHouse()
{
	genMoves();
	// TODO
}


////////////////////////////////////////////////////////////////////////////////
void ChessBoard::genPawnMoves()
{
	BITBOARD bbPiece = m_bitbPieces[m_turn][PAWN];

	// bbOthers bitb for opponent pieces + en-passant position when available
	BITBOARD bbTmp, bbOthers = m_bitbPositions[m_o_turn] | (m_ep == -1 ? 0L : BITS[m_ep]);

	int iPos;
	
	if(m_turn == WHITE)
	{
		////////////////////////////////////////////////////////////////////////////////
		// 1 step forward on free square
		bbTmp = (bbPiece >> 8) & ~m_bitb;	
		while(bbTmp != 0)
		{
			iPos = trailingZeros(bbTmp);
			bbTmp &= NOT_BITS[iPos];

			
			if(iPos <= 7) // promotion
			{
				addMoveElement(Move_makeMovePromotion(iPos+8, iPos, QUEEN, false));
				addMoveElement(Move_makeMovePromotion(iPos+8, iPos, ROOK, false));
				addMoveElement(Move_makeMovePromotion(iPos+8, iPos, BISHOP, false));
				addMoveElement(Move_makeMovePromotion(iPos+8, iPos, KNIGHT, false));
			}
			else
				addMoveElement(Move_makeMove(iPos+8, iPos));
		}
		/////////////////////////////////////////////////////////////////////////////////
		// 2 steps, first pawn move
		bbTmp = ((bbPiece & ROW_BITS[6]) >> 8) & ~m_bitb;
		bbTmp = (bbTmp >> 8) & ~m_bitb;

		while(bbTmp != 0)
		{
			iPos = trailingZeros(bbTmp);
			bbTmp &= NOT_BITS[iPos];

			addMoveElement(Move_makeMoveFirstPawn(iPos+16, iPos));
		}

		//////////////////////////////////////////////////////////////////////////////////
		// hits
		bbTmp = bbPiece & ~FILE_BITS[0];		// to the left 
		bbTmp = (bbTmp >> 9) & bbOthers;

		while(bbTmp != 0)
		{
			iPos = trailingZeros(bbTmp);
			bbTmp &= NOT_BITS[iPos];

			if(iPos <= 7)
			{
				addPawnCaptureMove(Move_makeMovePromotion(iPos+9, iPos, QUEEN, true));
				addPawnCaptureMove(Move_makeMovePromotion(iPos+9, iPos, ROOK, true));
				addPawnCaptureMove(Move_makeMovePromotion(iPos+9, iPos, BISHOP, true));
				addPawnCaptureMove(Move_makeMovePromotion(iPos+9, iPos, KNIGHT, true));
			}
			else if(iPos == m_ep)
			{
				addPawnCaptureMove(Move_makeMoveEP(iPos+9, iPos));
			}
			else
			{
				addPawnCaptureMove(Move_makeMoveHit(iPos+9, iPos));
			}
		}
		bbTmp = bbPiece & ~FILE_BITS[7];		// to the right
		bbTmp = (bbTmp >> 7) & bbOthers;

		while(bbTmp != 0)
		{
			iPos = trailingZeros(bbTmp);
			bbTmp &= NOT_BITS[iPos];

			if(iPos <= 7)
			{
				addPawnCaptureMove(Move_makeMovePromotion(iPos+7, iPos, QUEEN, true));
				addPawnCaptureMove(Move_makeMovePromotion(iPos+7, iPos, ROOK, true));
				addPawnCaptureMove(Move_makeMovePromotion(iPos+7, iPos, BISHOP, true));
				addPawnCaptureMove(Move_makeMovePromotion(iPos+7, iPos, KNIGHT, true));
			}
			else if(iPos == m_ep)
			{
				addPawnCaptureMove(Move_makeMoveEP(iPos+7, iPos));
			}
			else
			{
				addPawnCaptureMove(Move_makeMoveHit(iPos+7, iPos));
			}
		}
	}
	else // BLACK ////////////////////////////////////////////////////////////////////
	{
		///////////////////////////////////////////////////////////////////////////////////
		bbTmp = (bbPiece << 8) & ~m_bitb;
		while(bbTmp != 0)
		{
			iPos = trailingZeros(bbTmp);
			bbTmp &= NOT_BITS[iPos];

			if(iPos >= 56)
			{
				addMoveElement(Move_makeMovePromotion(iPos-8, iPos, QUEEN, false));
				addMoveElement(Move_makeMovePromotion(iPos-8, iPos, ROOK, false));
				addMoveElement(Move_makeMovePromotion(iPos-8, iPos, BISHOP, false));
				addMoveElement(Move_makeMovePromotion(iPos-8, iPos, KNIGHT, false));
			}
			else
				addMoveElement(Move_makeMove(iPos-8, iPos));
		}
		///////////////////////////////////////////////////////////////////////////////////
		bbTmp = ((bbPiece & ROW_BITS[1]) << 8) & ~m_bitb;
		bbTmp = (bbTmp << 8) & ~m_bitb;

		while(bbTmp != 0)
		{
			iPos = trailingZeros(bbTmp);
			bbTmp &= NOT_BITS[iPos];

			addMoveElement(Move_makeMoveFirstPawn(iPos-16, iPos));
		}
		///////////////////////////////////////////////////////////////////////////////////
		// hits
		bbTmp = bbPiece & ~FILE_BITS[7];
		bbTmp = (bbTmp << 9) & bbOthers;

		while(bbTmp != 0)
		{
			iPos = trailingZeros(bbTmp);
			bbTmp &= NOT_BITS[iPos];

			if(iPos >= 56)
			{
				addPawnCaptureMove(Move_makeMovePromotion(iPos-9, iPos, QUEEN, true));
				addPawnCaptureMove(Move_makeMovePromotion(iPos-9, iPos, ROOK, true));
				addPawnCaptureMove(Move_makeMovePromotion(iPos-9, iPos, BISHOP, true));
				addPawnCaptureMove(Move_makeMovePromotion(iPos-9, iPos, KNIGHT, true));
			}
			else if(iPos == m_ep)
			{
				addPawnCaptureMove(Move_makeMoveEP(iPos-9, iPos));
			}
			else
			{
				addPawnCaptureMove(Move_makeMoveHit(iPos-9, iPos));
			}
		}
		///////////////////////////////////////////////////////////////////////////////////
		bbTmp = bbPiece & ~FILE_BITS[0];
		bbTmp = (bbTmp << 7) & bbOthers;

		while(bbTmp != 0)
		{
			iPos = trailingZeros(bbTmp);
			bbTmp &= NOT_BITS[iPos];

			if(iPos >= 56)
			{
				addPawnCaptureMove(Move_makeMovePromotion(iPos-7, iPos, QUEEN, true));
				addPawnCaptureMove(Move_makeMovePromotion(iPos-7, iPos, ROOK, true));
				addPawnCaptureMove(Move_makeMovePromotion(iPos-7, iPos, BISHOP, true));
				addPawnCaptureMove(Move_makeMovePromotion(iPos-7, iPos, KNIGHT, true));
			}
			else if(iPos == m_ep)
			{
				addPawnCaptureMove(Move_makeMoveEP(iPos-7, iPos));
			}
			else
			{
				addPawnCaptureMove(Move_makeMoveHit(iPos-7, iPos));
			}
		}
	}
}


////////////////////////////////////////////////////////////////////////////////
// method pairs - one returning the bitb of moves, other one for generation
////////////////////////////////////////////////////////////////////////////////

BITBOARD ChessBoard::knightMoves(const int turn, const int pos)
{
	return (~m_bitbPositions[turn]) & ChessBoard::KNIGHT_RANGE[pos];
}
void ChessBoard::genKnightMoves()
{
	BITBOARD bbPiece = m_bitbPieces[m_turn][KNIGHT];
	int iPos;
	while(bbPiece != 0)
	{
		iPos = ChessBoard::trailingZeros(bbPiece);
		bbPiece &= NOT_BITS[iPos];
		addMoves(iPos, knightMoves(m_turn, iPos));
	}
}

////////////////////////////////////////////////////////////////////////////////
BITBOARD ChessBoard::rookMoves(const int turn, const int pos)
{
	//co.pl("90 pos " + pos + " << " + SHIFT_90[pos] + " = " + ((int)(m_bitb_90 >> SHIFT_90[pos]) & 0xFF));
	return (~m_bitbPositions[turn]) & 
			(
			(RANK_MOVES[pos][(int)(m_bitb >> SHIFT_0[pos]) & 0xFF]) |
			(FILE_MOVES[pos][(int)(m_bitb_90 >> SHIFT_90[pos]) & 0xFF])
			);
}
void ChessBoard::genRookMoves()
{
	BITBOARD bbPiece = m_bitbPieces[m_turn][ROOK];
	int iPos;
	while(bbPiece != 0)
	{
		iPos = ChessBoard::trailingZeros(bbPiece);
		bbPiece &= NOT_BITS[iPos];
		addMoves(iPos, rookMoves(m_turn, iPos));
	}
}

////////////////////////////////////////////////////////////////////////////////
BITBOARD ChessBoard::bishopMoves(const int turn, const int pos)
{
//		co.pl("45 pos " + pos + " << " + SHIFT_45[pos] + " mask " + ChessBoard::bits8ToString(MASK_45[pos]) + " on " + ChessBoard::bits8ToString((int)((m_bitb_45 >> SHIFT_45[pos]) & 0xFF)) + " = " + ChessBoard::bits8ToString(((int)(m_bitb_45 >> SHIFT_45[pos]) & MASK_45[pos])));
//		co.pl("315 pos " + pos + " << " + SHIFT_315[pos]  + " mask " + ChessBoard::bits8ToString(MASK_315[pos]) + " on " + ChessBoard::bits8ToString((int)((m_bitb_315 >> SHIFT_315[pos]) & 0xFF)) + " = " + ChessBoard::bits8ToString(((int)(m_bitb_315 >> SHIFT_315[pos]) & MASK_315[pos])));
	return (~m_bitbPositions[turn]) & 
			(
			(DIAG_45_MOVES[pos][(int)(m_bitb_45 >> SHIFT_45[pos]) & MASK_45[pos]]) | 
			(DIAG_315_MOVES[pos][(int)(m_bitb_315 >> SHIFT_315[pos]) & MASK_315[pos]])
			);
}
void ChessBoard::genBishopMoves()
{
	BITBOARD bbPiece = m_bitbPieces[m_turn][BISHOP];
	int iPos;
	while(bbPiece != 0)
	{
		iPos = ChessBoard::trailingZeros(bbPiece);
		bbPiece &= NOT_BITS[iPos];
		addMoves(iPos, bishopMoves(m_turn, iPos));
	}
}

////////////////////////////////////////////////////////////////////////////////
BITBOARD ChessBoard::queenMoves(const int turn, const int pos)
{
	return rookMoves(turn, pos) | bishopMoves(turn, pos);
}
void ChessBoard::genQueenMoves()
{
	BITBOARD bbPiece = m_bitbPieces[m_turn][QUEEN];
	int iPos;
	while(bbPiece != 0)
	{
		iPos = ChessBoard::trailingZeros(bbPiece);
		bbPiece &= NOT_BITS[iPos];
		addMoves(iPos, queenMoves(m_turn, iPos));
	}
}

////////////////////////////////////////////////////////////////////////////////
BITBOARD ChessBoard::kingMoves(const int turn, const int pos)
{
	return (~m_bitbPositions[turn]) & KING_RANGE[pos];
}
////////////////////////////////////////////////////////////////////////////////
// no while(bb) - only 1 king
////////////////////////////////////////////////////////////////////////////////
void ChessBoard::genKingMoves()
{
	const int pos = trailingZeros(m_bitbPieces[m_turn][KING]);
	addMoves(pos, kingMoves(m_turn, pos));
	genExtraKingMoves(pos);
}

////////////////////////////////////////////////////////////////////////////////
// generate castlings, when they are available
// TODO can isSquareAttacked be replaced by attacked bits of parent board?
void ChessBoard::genExtraKingMoves(const int pos)
{
	if(m_turn == WHITE)
	{
		if(hasOO(m_turn) &&
				!isSquareAttacked(m_turn, pos) && 
				!isSquareAttacked(m_turn, f1) && 
				!isSquareAttacked(m_turn, g1))
		{
			// default starting position of rook and king, default chess rules apply
			if(pos == e1 && ChessBoard::COL_HROOK == 7)
			{
				//String s = printB();
				if(isPosFree(f1) && isPosFree(g1) && 
					 (m_bitbPieces[WHITE][ROOK] & BITS[h1]) != 0)
				{
					addKingMove(Move_makeMoveOO(pos, g1));
				}
			}
			else // random Fischer chess
			{
				// start looking from the king to the rook; to the right for short castle
				int posRook = pos+1;
				while(posRook <= h1 && isPosFree(posRook) && !isSquareAttacked(m_turn, posRook)){
					posRook++;
				}
				if(posRook <= h1){ // the rook is to the right of the king
					// check if we found the rook and if it's on its original rank
					if(COL[posRook] == ChessBoard::COL_HROOK && (m_bitbPieces[WHITE][ROOK] & BITS[posRook]) != 0){
						// can we castle
						if(isPosFree(f1) && isPosFree(g1) ||    // both squares free
                                                    posRook == f1 && isPosFree(g1) ||   // rook allready on f1, g1 free
                                                    posRook == g1 && pos == f1 ||       // swap places
                                                    posRook == g1 && isPosFree(f1) ||   // rook in place of king
						    pos == g1 && isPosFree(f1) ||       // king allready on g1, f1 free
						    pos == f1 && isPosFree(g1)          // king on f1 and g1 free
						   )
						{
                                                    //DEBUG_PRINT
							addKingMove(Move_makeMoveOO(pos, g1));
						}
					}
				}
			}
		}
		if(hasOOO(m_turn) && 
				!isSquareAttacked(m_turn, pos) && 
				!isSquareAttacked(m_turn, d1) && 
				!isSquareAttacked(m_turn, c1))
		{
			// default starting position of rook and king, default chess rules apply
			if(pos == e1 && ChessBoard::COL_AROOK == 0)
			{
				if(isPosFree(d1) && isPosFree(c1) && isPosFree(b1) && (m_bitbPieces[WHITE][ROOK] & BITS[a1]) != 0)
				{
					addKingMove(Move_makeMoveOOO(pos, c1));
				}
			}
			else // random Fischer chess
			{
				// start looking from the king to the rook
				int posRook = pos-1;
				while(posRook >= a1 && isPosFree(posRook) && !isSquareAttacked(m_turn, posRook)){
					posRook--;
				}
				if(posRook >= a1){
					// check if we found the rook 
					if(COL[posRook] == ChessBoard::COL_AROOK && (m_bitbPieces[WHITE][ROOK] & BITS[posRook]) != 0){
						// can we castle
						if(isPosFree(d1) && isPosFree(c1) ||    // d1 and c1 free
                                                    posRook == d1 && isPosFree(c1) ||   // rook allready on d1, c1 free
                                                    posRook == c1 && pos == d1 ||       // swap places
						    pos == c1 && isPosFree(d1) ||       // king allready on c1
						    pos == d1 && isPosFree(c1) ||       // king on d1, c1 free
                                                    posRook == c1 && isPosFree(d1)      // rook on kings place, d1 free
						   )
						{
							addKingMove(Move_makeMoveOOO(pos, c1));
						}
					}
				}
			}
		}
	}
	else
	{
		if(hasOO(m_turn) && 
				!isSquareAttacked(m_turn, pos)  && 
				!isSquareAttacked(m_turn, f8) && 
				!isSquareAttacked(m_turn, g8))
		{
			// default starting position of rook and king, default chess rules apply
			if(pos == e8 && ChessBoard::COL_HROOK == 7)
			{
				 if(isPosFree(f8) && isPosFree(g8) && (m_bitbPieces[BLACK][ROOK] & BITS[h8]) != 0)
				 {
					 addKingMove(Move_makeMoveOO(pos, g8));
				 }
			}
			else if(ChessBoard::COL_HROOK != 5)// random Fischer chess
			{
				// start looking from the king to the rook
				int posRook = pos+1;
				while(posRook <= h8 && isPosFree(posRook) && !isSquareAttacked(m_turn, posRook)){
					posRook++;
				}
				if(posRook <= h8){
					// check if we found the rook 
					if(COL[posRook] == ChessBoard::COL_HROOK && (m_bitbPieces[BLACK][ROOK] & BITS[posRook]) != 0){
						// can we castle
						if(isPosFree(f8) && isPosFree(g8) ||
                                                    posRook == f8 && isPosFree(g8) ||
                                                    posRook == g8 && pos == f8 ||
                                                    posRook == g8 && isPosFree(f8) ||   // rook in place of king
						    pos == g8 && isPosFree(f8) ||
						    pos == f8 && isPosFree(g8)
						   )
						{
							addKingMove(Move_makeMoveOO(pos, g8));
						}
					}
				}
			}
		}
		if(hasOOO(m_turn) && 
				 !isSquareAttacked(m_turn, pos)  && 
				 !isSquareAttacked(m_turn, d8) && 
				 !isSquareAttacked(m_turn, c8))
		{
			// default starting position of rook and king, default chess rules apply
			if(pos == e8 && ChessBoard::COL_AROOK == 0){
				 if(isPosFree(d8) && isPosFree(c8) && isPosFree(b8) && (m_bitbPieces[BLACK][ROOK] & BITS[a8]) != 0)
				 {
					 addKingMove(Move_makeMoveOOO(pos, c8));
				 }
			}
			else // random Fischer chess
			{
				// start looking from the king to the rook
				int posRook = pos-1;
				while(posRook >= a8 && isPosFree(posRook) && !isSquareAttacked(m_turn, posRook)){
					posRook--;
				}
				if(posRook >= a8){
					// check if we found the rook 
					if(COL[posRook] == ChessBoard::COL_AROOK && (m_bitbPieces[BLACK][ROOK] & BITS[posRook]) != 0){
						// can we castle
						if(isPosFree(d8) && isPosFree(c8) ||
							posRook == d8 && isPosFree(c8) ||
							posRook == c8 && pos == d8 ||
                                                        pos == c8 && isPosFree(d8) ||
                                                        pos == d8 && isPosFree(c8) ||
                                                        posRook == c8 && isPosFree(d8)
						   )
						{
							addKingMove(Move_makeMoveOOO(pos, c8));
						}
					}
				}
			}
		}
	}
	//co.pl("{" + this + "*" + r.ToString() + "*");
}

////////////////////////////////////////////////////////////////////////////////
// reset the move index, moves are allready generated
////////////////////////////////////////////////////////////////////////////////
void ChessBoard::getMoves()
{
	m_indexMoves = 0;
}

////////////////////////////////////////////////////////////////////////////////
// sort generated moves
////////////////////////////////////////////////////////////////////////////////
void ChessBoard::getScoredMoves()
{
	int i, j, tmp, tmpMove, from, to, piece;
	for(i = 0; i < m_sizeMoves; i++)
	{
		m_arrScoreMoves[i] = scoreMove(m_arrMoves[i]);
	}
	for(i = 0; i < m_sizeMoves - 1; i++)
	{
		for(j = 0; j < m_sizeMoves - 1 - i; j++)
		{
			if(m_arrScoreMoves[j+1] > m_arrScoreMoves[j])
			{
				tmp = m_arrScoreMoves[j];
				m_arrScoreMoves[j] = m_arrScoreMoves[j+1];
				m_arrScoreMoves[j+1] = tmp;
				
				tmpMove = m_arrMoves[j];
				m_arrMoves[j] = m_arrMoves[j+1];
				m_arrMoves[j+1] = tmpMove;
			}
		}
	}
	
	m_indexMoves = 0;
}
void ChessBoard::scoreMoves()
{
	for(int i = 0; i < m_sizeMoves; i++)
	{
		m_arrScoreMoves[i] = scoreMove(m_arrMoves[i]);
	}
	m_indexMoves = 0;
}
void ChessBoard::scoreMovesPV(const int move)
{
        for(int i = 0; i < m_sizeMoves; i++)
	{
            if(m_arrMoves[i] == move)
                m_arrScoreMoves[i] = 100000;
            else
		m_arrScoreMoves[i] = scoreMove(m_arrMoves[i]);
	}
	m_indexMoves = 0;
}
void ChessBoard::setMyMoveCheck()
{
	m_myMove = Move_setCheck(m_myMove);
}
// lazy sorting the m_arrMoves, on each call get the best scored move and put on 
// m_indexMoves and return. advance m_indexMoves
int ChessBoard::getNextScoredMove()
{
	int bestScore = -1, bestIndex = 0;
	for(int i = m_indexMoves; i < m_sizeMoves; i++){
		if(m_arrScoreMoves[i] > bestScore){
			bestScore = m_arrScoreMoves[i];
			bestIndex = i;
		}
	}
	// swap score
	bestScore = m_arrScoreMoves[m_indexMoves];
	m_arrScoreMoves[m_indexMoves] = m_arrScoreMoves[bestIndex];
	m_arrScoreMoves[bestIndex] = bestScore;
	// swap move
	bestScore = m_arrMoves[m_indexMoves];
	m_arrMoves[m_indexMoves] = m_arrMoves[bestIndex];
	m_arrMoves[bestIndex] = bestScore;
	return m_arrMoves[m_indexMoves++];
}

////////////////////////////////////////////////////////////////////////////////
// sort generated moves with extra score for transposition and killer moves
////////////////////////////////////////////////////////////////////////////////
void ChessBoard::getScoredMovesTT(const int ttMove, const int killerMove, const int killerMove2)
{
	int i, j, tmp, tmpMove;
	for(i = 0; i < m_sizeMoves; i++)
	{
		if(m_arrMoves[i] == ttMove)
			m_arrScoreMoves[i] = 2000;
		else if(m_arrMoves[i] == killerMove || m_arrMoves[i] == killerMove2)
			m_arrScoreMoves[i] = 1500;
		else
		{
			m_arrScoreMoves[i] = scoreMove(m_arrMoves[i]);
		}
	}
	for(i = 0; i < m_sizeMoves - 1; i++)
	{
		for(j = 0; j < m_sizeMoves - 1 - i; j++)
		{
			if(m_arrScoreMoves[j+1] > m_arrScoreMoves[j])
			{
				tmp = m_arrScoreMoves[j];
				m_arrScoreMoves[j] = m_arrScoreMoves[j+1];
				m_arrScoreMoves[j+1] = tmp;
				
				tmpMove = m_arrMoves[j];
				m_arrMoves[j] = m_arrMoves[j+1];
				m_arrMoves[j+1] = tmpMove;
			}
		}
	}
	
	m_indexMoves = 0;
}

////////////////////////////////////////////////////////////////////////////////
// basic move score 
////////////////////////////////////////////////////////////////////////////////
int ChessBoard::scoreMove(int move)
{
	const int from = Move_getFrom(move), to = Move_getTo(move);
	const int piece = pieceAt(m_turn, from);
	
	if(Move_isHIT(move))
	{
		if(Move_isEP(move))
			return 30000;
		return 30000 + ChessBoard::PIECE_VALUES[pieceAt(m_o_turn, to)] - ChessBoard::PIECE_VALUES[piece];
	}
	// we don't know if a move is check!
	if(/*Move_isCheck(move) || */Move_isPromotionMove(move))
	{
		return 25000;
	}
	
	if(piece == KING)
	{
		if(m_o_quality == 0)
			return 20000;
		return 0;
	}
	if(piece == KNIGHT)
		return 2000 + (HOOK_DISTANCE[from][d5] - HOOK_DISTANCE[to][e4]);
	if(piece == PAWN)
		return 1900 + ROW_TURN[m_turn][to] + (HOOK_DISTANCE[from][d5] - HOOK_DISTANCE[to][e4]);
	return 1000 + ROW_TURN[m_turn][to];
}


////////////////////////////////////////////////////////////////////////////////
// returns comma seperated per 5 moves new-lined string of pgn moves.
////////////////////////////////////////////////////////////////////////////////
void ChessBoard::getPGNMoves(ChessBoard* board, char *sz)
{
	strcpy(sz, "");
	int move, i = 0; char tmp[20] = ""; ChessBoard* tmpBoard = new ChessBoard();
	m_indexMoves = 0;
	while(hasMoreMoves())
	{
		move = getNextMove();
		makeMove(move, board);
		board->calcState(tmpBoard);
		board->myMoveToString(tmp);
		strcat(sz, tmp);
		strcat(sz, ",");
		if(i % 5 == 4)
			strcat(sz, "\n");
		i++;
	}
	delete tmpBoard;
}

// returns the history of moves in pgn format
/*
String ChessBoard::getHistoryDebug()
{
	ChessBoard* tmpBoard;
	String sz = ""; 
	tmpBoard = this;
	while(tmpBoard != NULL)
	{
		sz = Move::toDbgString(tmpBoard->m_myMove) + " " + sz;
		tmpBoard = tmpBoard->m_parent;
	}		
	return sz;
}
*/

////////////////////////////////////////////////////////////////////////////////
// BASIC evaluation function
// returns the value of the board, called from search method to compare board 
// values
////////////////////////////////////////////////////////////////////////////////
int ChessBoard::boardValue()
{
	// no state!
	/*
	if(m_state == ChessBoard::MATE)
		return -ChessBoard::VALUATION_MATE;
	if(m_state == ChessBoard::DRAW_MATERIAL || m_state == ChessBoard::DRAW_50)
		return ChessBoard::VALUATION_DRAW;
	if(m_state == ChessBoard::DRAW_REPEAT)
		return ChessBoard::VALUATION_DRAW_REPEAT;
	*/
	// standard super simple evaluation. sum of material quality
	return m_quality - m_o_quality;
}

////////////////////////////////////////////////////////////////////////////////
// boardValue will call this method when one of the players only has its king 
// on the board and no pawns
////////////////////////////////////////////////////////////////////////////////
int ChessBoard::loneKingValue(const int turn)
{
	if(turn == m_turn){
		return ChessBoard::VALUATION_LONE_KING_BONUS - ChessBoard::VALUATION_LONE_KING * HOOK_DISTANCE[m_kingPos][m_o_kingPos] - ChessBoard::VALUATION_KING_ENDINGS[m_o_kingPos] + m_quality;
        }
	return ChessBoard::VALUATION_LONE_KING_BONUS - ChessBoard::VALUATION_LONE_KING * HOOK_DISTANCE[m_o_kingPos][m_kingPos] - ChessBoard::VALUATION_KING_ENDINGS[m_kingPos] + m_o_quality;
}

////////////////////////////////////////////////////////////////////////////////
// return "value" of king bishop knight against lone king
////////////////////////////////////////////////////////////////////////////////
int ChessBoard::kbnkValue(const int turn)
{
	int winnerKingPos, loserKingPos, value = 0;
	if(m_turn == turn)
	{
		winnerKingPos = m_kingPos;
		loserKingPos = m_o_kingPos;
		if((m_bitbPieces[m_turn][KING] & ChessBoard::CENTER_4x4_SQUARES) != 0)
			value = 20;
	}
	else
	{
		winnerKingPos = m_o_kingPos;
		loserKingPos = m_kingPos;
		if((m_bitbPieces[m_o_turn][KING] & ChessBoard::CENTER_4x4_SQUARES) != 0)
			value = 20;
	}
	value += (300 - 6 * HOOK_DISTANCE[winnerKingPos][loserKingPos]);
	value -= ((m_bitbPieces[turn][BISHOP] & WHITE_SQUARES) == 0) ? ChessBoard::VALUATION_KBNK_SCORE[0][loserKingPos] : ChessBoard::VALUATION_KBNK_SCORE[1][loserKingPos];
	value -= ChessBoard::VALUATION_KING_ENDINGS[loserKingPos];
	value -= HOOK_DISTANCE[trailingZeros(m_bitbPieces[turn][BISHOP])][loserKingPos];
	value -= HOOK_DISTANCE[trailingZeros(m_bitbPieces[turn][KNIGHT])][loserKingPos];
	
	return value;
}

////////////////////////////////////////////////////////////////////////////////
// evaluation to promote promoting...
////////////////////////////////////////////////////////////////////////////////
int ChessBoard::promotePawns(const int turn)
{
	int value = 0;
	BITBOARD bb = m_bitbPieces[turn][PAWN];
	int pos;
	if(m_turn == turn)
	{
		value += (15 - HOOK_DISTANCE[m_kingPos][m_o_kingPos]);
		value += ROW_TURN[turn][m_kingPos];
	}
	else
	{
		value += (15 - HOOK_DISTANCE[m_kingPos][m_o_kingPos]);
		value += ROW_TURN[turn][m_o_kingPos];
	}
	 
	while(bb != 0)
	{
		pos = ChessBoard::trailingZeros(bb);
		bb &= NOT_BITS[pos];
		value += 10 * ROW_TURN[turn][pos];
	}
	return value;
}

////////////////////////////////////////////////////////////////////////////////
// Extended evaluation function
// 
////////////////////////////////////////////////////////////////////////////////
int ChessBoard::boardValueExtension()
{
	// no m_state
	/*
	if(m_state == ChessBoard::MATE)
		return -ChessBoard::VALUATION_MATE;
	if(m_state == ChessBoard::DRAW_MATERIAL || m_state == ChessBoard::DRAW_50)
		return ChessBoard::VALUATION_DRAW;
	if(m_state == ChessBoard::DRAW_REPEAT)
	{
		// penalty when better or equal quality
		if(m_quality >= m_o_quality)
			return ChessBoard::VALUATION_DRAW_REPEAT;
		return ChessBoard::VALUATION_DRAW;
	}
	*/

        /*
	if(m_parent == NULL)
            return 0;
	//co.pl("boardValue called with NULL parent!!!");
	*/
	// lone king
	if(m_quality == 0)
	{
		// no pawns to promote but enough mating material (m_state != DRAW_MATERIAL)
		if(m_bitbPieces[m_o_turn][PAWN] == 0)
		{
			// kbnk is special case
			if((m_o_quality == ChessBoard::PIECE_VALUES[KNIGHT] + ChessBoard::PIECE_VALUES[BISHOP]) && ChessBoard::bitCount(m_bitbPieces[m_o_turn][KNIGHT]) == 1 && ChessBoard::bitCount(m_bitbPieces[m_o_turn][BISHOP]) == 1)
				return -kbnkValue(m_o_turn);
			return -loneKingValue(m_o_turn);
		}
		// promote pawns
		return -promotePawns(m_o_turn);
	} // opponent has lone king
	else if(m_o_quality == 0)
	{
		if(m_bitbPieces[m_turn][PAWN] == 0)
		{
			if((m_quality == ChessBoard::PIECE_VALUES[KNIGHT] + ChessBoard::PIECE_VALUES[BISHOP]) && ChessBoard::bitCount(m_bitbPieces[m_turn][KNIGHT]) == 1 && ChessBoard::bitCount(m_bitbPieces[m_turn][BISHOP]) == 1)
				return kbnkValue(m_turn);
			return loneKingValue(m_turn);
		}
		return promotePawns(m_turn);
	}
	// TODO some known end-game evaluations (kqkq, kqkr, krkn, krkb...)


        // always start with
	// standard basic evaluation. sum of material quality
	int val = m_quality - m_o_quality;

        /*
	// center and king squares attacked
        
	val += ChessBoard::bitCount(m_bitbAttackMoveSquares & (ChessBoard::CENTER_SQUARES | KING_RANGE[m_o_kingPos])) * 2;
	val -= ChessBoard::bitCount(m_parent->m_bitbAttackMoveSquares & (ChessBoard::CENTER_SQUARES | KING_RANGE[m_kingPos])) * 2;
	
	// attacked square count
	val += ChessBoard::bitCount(m_bitbAttackMoveSquares);
	val -= ChessBoard::bitCount(m_parent->m_bitbAttackMoveSquares);
	
	// attacked pieces
	val += ChessBoard::bitCount(m_bitbAttackMoveSquares & m_bitbPositions[m_o_turn]);
	val -= ChessBoard::bitCount(m_parent->m_bitbAttackMoveSquares & m_bitbPositions[m_turn]);
        */
	
	val += pawnValueExtension(m_turn);
	val -= pawnValueExtension(m_o_turn);
	
	val += kingValueExtension(m_turn);
	val -= kingValueExtension(m_o_turn);
	
	val += queenValueExtension(m_turn);
	val -= queenValueExtension(m_o_turn);
	
	val += knightValueExtension(m_turn);
	val -= knightValueExtension(m_o_turn);
	
	val += bishopValueExtension(m_turn);
	val -= bishopValueExtension(m_o_turn);
        
	val += rookValueExtension(m_turn);
	val -= rookValueExtension(m_o_turn);
	
	return val;
}

////////////////////////////////////////////////////////////////////////////////
//
// penalty for early queen move
////////////////////////////////////////////////////////////////////////////////
int ChessBoard::queenValueExtension(const int turn)
{
     BITBOARD bbPiece = m_bitbPieces[turn][QUEEN];
	if(bbPiece != 0)
	{
            // TODO this assumes a default game and other pieces
            if(m_numBoard < 12){
		const BITBOARD bbRows = ROW_BITS[ROW_TURN[turn][0]];
		if((bbRows & bbPiece) == 0) // Queen not on first row
			return VALUATION_EARLY_QUEEN;
            } else {
                
                int iPos; int val = 0;
                while(bbPiece != 0) {
                    iPos = ChessBoard::trailingZeros(bbPiece);
                    bbPiece &= NOT_BITS[iPos];

                    val += ChessBoard::bitCount(rookMoves(turn, iPos)) * VALUATION_ROOK_MOBILITY;
                    val += ChessBoard::bitCount(bishopMoves(turn, iPos)) * VALUATION_BISHOP_MOBILITY;
                }
                return val;
            }
	}
	return 0;
}

int ChessBoard::kingValueExtension(const int turn)
{
    int val;
    if(m_castlings[turn] & MASK_CASTLED){
        val = VALUATION_CASTLED;
    } else {
        if(m_castlings[turn] == 0)
            val = VALUATION_CASTLING_POSSIBLE;
        else
            val = VALUATION_CASTLING_NOT_POSSIBLE;
    }
    //m_bitbPieces[turn][KING]
    if(turn == m_turn){
        return (ChessBoard::bitCount(KING_RANGE[m_kingPos] & m_bitbPieces[turn][PAWN]) * VALUATION_PAWN_IN_KING_RANGE) + val;
    }
    return (ChessBoard::bitCount(KING_RANGE[m_o_kingPos] & m_bitbPieces[turn][PAWN]) * VALUATION_PAWN_IN_KING_RANGE) + val;
}
int ChessBoard::knightValueExtension(const int turn)
{
    ///
    BITBOARD bbPiece = m_bitbPieces[turn][KNIGHT];
    int iPos; int val = ChessBoard::bitCount(m_bitbPieces[turn][KNIGHT] & CENTER_4x4_SQUARES) * VALUATION_KNIGHT_CENTER;
    while(bbPiece != 0) {
        iPos = ChessBoard::trailingZeros(bbPiece);
        bbPiece &= NOT_BITS[iPos];

        val += ChessBoard::bitCount(knightMoves(turn, iPos)) * VALUATION_KNIGHT_MOBILITY;
    }
    return val;
}
int ChessBoard::rookValueExtension(const int turn)
{
    const BITBOARD bbRooks = m_bitbPieces[turn][ROOK];
    BITBOARD bbPiece = bbRooks;
    int iPos; int val = 0, col = -1, row = -1;

    /*
      */

    while(bbPiece != 0) {
        iPos = ChessBoard::trailingZeros(bbPiece);
        bbPiece &= NOT_BITS[iPos];

        val += ChessBoard::bitCount(rookMoves(turn, iPos)) * VALUATION_ROOK_MOBILITY;

        if(row == -1){
            row = Pos::row(iPos);
            col = Pos::col(iPos);
        } else {
            if(row == Pos::row(iPos))
                val += VALUATION_ROOK_SAME_ROW_FILE;
            if(col == Pos::col(iPos))
                val += VALUATION_ROOK_SAME_ROW_FILE;
            
            // CONNECTED
            if(((~m_bitbPositions[turn]) | (bbRooks & NOT_BITS[iPos])) &
			(
			(RANK_MOVES[iPos][(int)(m_bitb >> SHIFT_0[iPos]) & 0xFF]) |
			(FILE_MOVES[iPos][(int)(m_bitb_90 >> SHIFT_90[iPos]) & 0xFF])
			)){
                val += VALUATION_ROOK_CONNECTED;
            }
        }

    }
    return val;
}

// for the bishop the moveability is key
// the nr of attack squares
// different valuation for single bishop
// penalty for the number of colored squares that are occupied by own pawns
//
int ChessBoard::bishopValueExtension(const int turn)
{
    BITBOARD bbPiece = m_bitbPieces[turn][BISHOP];
    int iPos; int val = 0; 
    if(bbPiece > 0){
        if(ChessBoard::bitCount(bbPiece) == 1){
            iPos = ChessBoard::trailingZeros(bbPiece);

            val += ChessBoard::bitCount(bishopAttacks(turn, iPos)) * 5;
            if(iPos % 2 == 0){
                val -= ChessBoard::bitCount(WHITE_SQUARES & m_bitbPieces[turn][PAWN]);
            }
            else {
                val -= ChessBoard::bitCount(BLACK_SQUARES & m_bitbPieces[turn][PAWN]);
            }
        } else {
            val += VALUATION_BISHOP_PAIR; // bonus for pair or more bishops
            while(bbPiece != 0)
            {
                    iPos = ChessBoard::trailingZeros(bbPiece);
                    bbPiece &= NOT_BITS[iPos];

                    val += ChessBoard::bitCount(bishopMoves(turn, iPos)) * VALUATION_BISHOP_MOBILITY;
            }
        }
    }
    return val;
}
int ChessBoard::pawnValueExtension(const int turn)
{
    int val = 0;
    const BITBOARD bbPawn = m_bitbPieces[turn][PAWN];
    const BITBOARD bbPawnOpp = m_bitbPieces[turn^1][PAWN];
    BITBOARD bbPiece = bbPawn;

    if(turn == WHITE){
        if(bbPawn & (D2 | E2))
            val += VALUATION_PAWN_CENTRE_FIRST_ROW;

    } else {
        if(bbPawn & (D7 | E7))
            val += VALUATION_PAWN_CENTRE_FIRST_ROW;
    }
    //

    int iPos;
    while(bbPiece != 0)
    {
            iPos = ChessBoard::trailingZeros(bbPiece);
            bbPiece &= NOT_BITS[iPos];

            val += ROW_TURN[turn][iPos] * VALUATION_PAWN_ROW; // advance pawn

            if(PASSED_PAWN_MASK[turn][iPos] & bbPawn)
                val += VALUATION_PAWN_FILE_NEIGHBOUR; // neighbours on files

            if(PAWN_RANGE[turn][iPos] & bbPawn)
                val += VALUATION_PAWN_CONNECTED; // covering neighbours

            if(FILE_BITS[COL[iPos]] & (bbPawn & NOT_BITS[iPos])) 
                val += VALUATION_PAWN_DOUBLED; // doubled pawn
            
            if((PASSED_PAWN_MASK[turn][iPos] & bbPawnOpp) == 0) // passed pawn
                val += VALUATION_PAWN_PASSED;
            
    }
    //
    return val;
}
////////////////////////////////////////////////////////////////////////////////
// used in random fischer setup to check if colNum is available column
////////////////////////////////////////////////////////////////////////////////
int ChessBoard::getAvailableCol(int colNum){
	int col = 0, i = 0, pos;
	do{
		pos = Pos::fromColAndRow(col, 0);
		if(isPosFree(pos))
			i++;
		col++;
	}while(i <= colNum);
	col--;
	return col;
}





////////////////////////////////////////////////////////////////////////////////


////////////////////////////////////////////////////////////////////////////////
// set all default values
// king positions are e1 and e8, but bitboards are emptied
////////////////////////////////////////////////////////////////////////////////
void ChessBoard::reset()
{
	m_numBoard = 1;
	m_parent = NULL;
	m_myMove = 0;

	m_turn = WHITE;
	m_o_turn = BLACK;
	m_kingPos = e1;
	m_o_kingPos = e8;
	m_state = -1;
	m_ep = -1;
	m_castlings[BLACK] = 0;
	m_castlings[WHITE] = 0;
	m_50RuleCount = 0;
	m_hashKey = 0L;
	
	m_bitbPositions[BLACK] = 0L;
	m_bitbPositions[WHITE] = 0L;
	m_bitb = 0L;
	m_bitb_45 = 0L;
	m_bitb_90 = 0L;
	m_bitb_315 = 0L;
	m_bitbAttackMoveSquares = 0L;
	
	for(int i = 0; i < NUM_PIECES; i++)
	{
		m_bitbPieces[BLACK][i] = 0L;
		m_bitbPieces[WHITE][i] = 0L;
	}

        //COL_AROOK = 0;
        //COL_HROOK = 7;
}

////////////////////////////////////////////////////////////////////////////////
// after pieces have been added, commit the board; ie do necassary calc
////////////////////////////////////////////////////////////////////////////////
void ChessBoard::commitBoard()
{
	m_state = PLAY;
	initHashKey();
	calcQuality();
	//DEBUG_PRINT("commitBoard\n", 0);
}

////////////////////////////////////////////////////////////////////////////////
// duplicate this object
////////////////////////////////////////////////////////////////////////////////
void ChessBoard::duplicate(ChessBoard* ret)
{
    memcpy(ret, this, SIZEOF_BOARD);
    /*
	ret->m_turn = this->m_turn;
	ret->m_o_turn = this->m_o_turn;
	ret->m_ep = this->m_ep;
	ret->m_state = this->m_state;
	ret->m_castlings[BLACK] = this->m_castlings[BLACK];
	ret->m_castlings[WHITE] = this->m_castlings[WHITE];
	ret->m_50RuleCount = this->m_50RuleCount;
	ret->m_hashKey = this->m_hashKey;
	ret->m_parent = this->m_parent;
	ret->m_myMove = this->m_myMove;
	ret->m_numBoard = this->m_numBoard;
	
	ret->m_quality = this->m_quality;
	ret->m_o_quality = this->m_o_quality;
	ret->m_kingPos = this->m_kingPos;
	ret->m_o_kingPos = this->m_o_kingPos;
	
	ret->m_bitbPositions[BLACK] = this->m_bitbPositions[BLACK];
	ret->m_bitbPositions[WHITE] = this->m_bitbPositions[WHITE];
	ret->m_bitb = this->m_bitb;
	ret->m_bitb_45 = this->m_bitb_45;
	ret->m_bitb_90 = this->m_bitb_90;
	ret->m_bitb_315 = this->m_bitb_315;
	ret->m_bitbAttackMoveSquares = this->m_bitbAttackMoveSquares;
	
	for(int i = 0; i < NUM_PIECES; i++)
	{
		ret->m_bitbPieces[BLACK][i] = this->m_bitbPieces[BLACK][i];
		ret->m_bitbPieces[WHITE][i] = this->m_bitbPieces[WHITE][i];
	}
	ret->m_sizeMoves = this->m_sizeMoves;
	for(int i = 0; i < m_sizeMoves; i++)
		ret->m_arrMoves[i] = this->m_arrMoves[i];
     */
}

////////////////////////////////////////////////////////////////////////////////
// convinient method to return pointer to first board
////////////////////////////////////////////////////////////////////////////////
ChessBoard* ChessBoard::getFirstBoard()
{
	ChessBoard *ret = this;
	while(ret->m_parent != NULL)
		ret = ret->m_parent;
	return ret;
}

////////////////////////////////////////////////////////////////////////////////
//public members
////////////////////////////////////////////////////////////////////////////////


int ChessBoard::getNumBoard()
{
	return m_numBoard;
}
BITBOARD ChessBoard::getHashKey()
{
	return m_hashKey;
}
int ChessBoard::countPieces()
{
	return ChessBoard::bitCount(m_bitb);
}
int ChessBoard::getNoHitCount()
{
	return m_50RuleCount;
}
int ChessBoard::getTurn()
{
	return m_turn;	
}
void ChessBoard::switchTurn()
{
	int tmp = m_o_kingPos;
	m_o_kingPos = m_kingPos;
	m_kingPos = tmp;

	tmp = m_turn;
	m_turn = m_o_turn;
	m_o_turn = tmp;
	
	tmp = m_quality;
	m_quality = m_o_quality;
	m_o_quality = tmp;
}
int ChessBoard::opponentTurn()
{
	return m_o_turn;
}
boolean ChessBoard::isPieceOfTurnAt(const int p)
{
	return (m_bitbPositions[m_turn] & BITS[p]) != 0;
}
int ChessBoard::getEP()
{
	return m_ep;	
}
boolean ChessBoard::hasOO(const int t)
{
	return (m_castlings[t] & MASK_HROOK) == 0 && (m_castlings[t] & MASK_KING) == 0;	
}
boolean ChessBoard::hasOOO(const int t)
{
	return (m_castlings[t] & MASK_AROOK) == 0 && (m_castlings[t] & MASK_KING) == 0;
}
BITBOARD ChessBoard::bitbPositions()
{
	return m_bitbPositions[m_turn];	
}
BITBOARD ChessBoard::bitbOpponentPositions()
{
	return m_bitbPositions[m_o_turn];	
}
BITBOARD ChessBoard::bitb()
{
	return m_bitb;
}
BITBOARD ChessBoard::bitbAttacked()
{
	return m_bitbAttackMoveSquares;
}
BITBOARD ChessBoard::bitbTurnPiece(const int t, const int p)
{
	return m_bitbPieces[t][p];
}
boolean ChessBoard::isPosFree(const int p)
{
	return (m_bitb & BITS[p]) == 0;
}
boolean ChessBoard::isPosFriend(const int p)
{
	return (m_bitbPositions[m_turn] & BITS[p]) != 0;
}
boolean ChessBoard::isPosEnemy(const int p)
{
	return (m_bitbPositions[m_o_turn] & BITS[p]) != 0;
}
int ChessBoard::pieceAt(const int t, const int p)
{
    const BITBOARD bb = BITS[p];
	if((m_bitbPositions[t] & bb) != 0)
	{
		if((m_bitbPieces[t][PAWN] & bb) != 0)
			return PAWN;
		if((m_bitbPieces[t][KNIGHT] & bb) != 0)
			return KNIGHT;
		if((m_bitbPieces[t][BISHOP] & bb) != 0)
			return BISHOP;
		if((m_bitbPieces[t][ROOK] & bb) != 0)
			return ROOK;
		if((m_bitbPieces[t][QUEEN] & bb) != 0)
			return QUEEN;
		if((m_bitbPieces[t][KING] & bb) != 0)
			return KING;
	}
	return FIELD;
}
boolean ChessBoard::isPieceOfColorAt(const int t, const int p)
{
	return (m_bitbPositions[t] & BITS[p]) != 0;
}
boolean ChessBoard::isFieldAt(const int p)
{
	return (m_bitb & BITS[p]) == 0;  
}
int ChessBoard::getIndex(const int col, const int row)
{
	return (row * 8) + col;
}

////////////////////////////////////////////////////////////////////////////////
// return FEN notation of board (just the pieces on the board board)
////////////////////////////////////////////////////////////////////////////////
void ChessBoard::toFENBoard(char* s){
	
	strcpy(s, "");
	
	char sP[2], buf[50];
	char arrP[2][7] = {
		"pnbrqk", 
		"PNBRQK"
	};
	sP[1] = '\0';
	
	int numEmpty = 0, piece; 
	for(int i = 0; i < 64; i++){
		sP[0] = '\0';
		
		if(isPieceOfColorAt(BLACK, i)){
			piece = pieceAt(BLACK, i);
			sP[0] = arrP[BLACK][piece];
		} else if(isPieceOfColorAt(WHITE, i)){
			piece = pieceAt(WHITE, i);
			sP[0] = arrP[WHITE][piece];
		} 
		if(i > 0 && i % 8 == 0){
			if(numEmpty > 0){
				sprintf(buf, "%d\0", numEmpty);
				strcat(s, buf);
				numEmpty = 0;
			}
			if(i < 62)
				strcat(s, "/");
		}
		if(sP[0] == '\0'){
			numEmpty++;
		} else {
			
			if(numEmpty > 0){
				sprintf(buf, "%d\0", numEmpty);
				strcat(s, buf);
			}
			strcat(s, sP);
			numEmpty = 0;
		}
	}
	if(numEmpty > 0){
		sprintf(buf, "%d\0", numEmpty);
		strcat(s, buf);
	}
	strcat(s, " ");
	if(m_turn == WHITE)
		strcat(s, "w");
	else
		strcat(s, "b");
}

////////////////////////////////////////////////////////////////////////////////
// return complete FEN representation of the board
////////////////////////////////////////////////////////////////////////////////
void ChessBoard::toFEN(char* s){
	
	toFENBoard(s);
	char buf[10];
	
	strcat(s, " ");
	
	boolean bCastle = false;
	if(hasOO(WHITE)){
		strcat(s, "K");
		bCastle = true;
	}
	if(hasOOO(WHITE)){
		strcat(s, "Q");
		bCastle = true;
	}
	if(hasOO(BLACK)){
		strcat(s, "k");
		bCastle = true;
	}
	if(hasOOO(BLACK)){
		strcat(s, "q");
		bCastle = true;
	}
	if(false == bCastle)
		strcat(s, "-");
	strcat(s, " ");
	if(m_ep == -1){
		strcat(s, "-"); 
	} else {
		Pos::toString(m_ep, buf);
		strcat(s, buf);
	}
	
	strcat(s, " ");
	sprintf(buf, "%d\0", m_50RuleCount);
	strcat(s, buf);
	strcat(s, " ");
	int cnt = 0;
	ChessBoard* tmpBoard;
	tmpBoard = this;
	while(tmpBoard->m_parent != NULL){
		cnt++;
		tmpBoard = tmpBoard->m_parent;
	}
	cnt = cnt / 2 + 1;
	sprintf(buf, "%d\0", cnt);
	strcat(s, buf);
}

////////////////////////////////////////////////////////////////////////////////
// in case of a setup, set 'white can castle short'=>wccs etc and ep square
// and number of moves for 50 move rule
////////////////////////////////////////////////////////////////////////////////
void ChessBoard::setCastlingsEPAnd50(boolean wccl, boolean wccs, boolean bccl, boolean bccs, int ep, int r50)
{
	m_ep = ep;
	m_50RuleCount = r50;
	COL_AROOK = 0;
	COL_HROOK = 7;
	if(wccl){
		int posRook = m_kingPos-1;
		while(posRook >= a1){
			if((m_bitbPieces[WHITE][ROOK] & BITS[posRook]) != 0)
				COL_AROOK = COL[posRook];
			posRook--;
		}
	}
	else
		m_castlings[WHITE] |= MASK_AROOK;
	if(wccs){
		int posRook = m_kingPos+1;
		while(posRook <= h1){
			if((m_bitbPieces[WHITE][ROOK] & BITS[posRook]) != 0)
				COL_HROOK = COL[posRook];
			posRook++;
		}
	}
	else
		m_castlings[WHITE] |= MASK_HROOK;
		
	if(!wccl && !wccs)
		m_castlings[WHITE] |= MASK_KING;
		
	if(bccl){
		int posRook = m_o_kingPos-1;
		while(posRook >= a8){
			if((m_bitbPieces[BLACK][ROOK] & BITS[posRook]) != 0)
				COL_AROOK = COL[posRook];
			posRook--;
		}
	}
	else
		m_castlings[BLACK] |= MASK_AROOK;
	if(bccs){
		int posRook = m_o_kingPos+1;
		while(posRook <= h8){
			if((m_bitbPieces[BLACK][ROOK] & BITS[posRook]) != 0)
				COL_HROOK = COL[posRook];
			posRook++;
		}
	}
	else
		m_castlings[BLACK] |= MASK_HROOK;
		
	if(!bccl && !bccs)
		m_castlings[BLACK] |= MASK_KING;

        DEBUG_PRINT("setCastlingsEP - A %d, H %d, cw %d, cb %d", COL_AROOK, COL_HROOK, m_castlings[WHITE], m_castlings[BLACK]);
}

////////////////////////////////////////////////////////////////////////////////
// change variables so that side to move is turn
////////////////////////////////////////////////////////////////////////////////
void ChessBoard::setTurn(const int turn)
{
	if(m_turn == turn)
		return;
	switchTurn();
}
////////////////////////////////////////////////////////////////////////////////
// get the number of captured pieces of turn and piece
////////////////////////////////////////////////////////////////////////////////
int ChessBoard::getNumCaptured(int turn, int piece)
{
	ChessBoard* tmpBoard = this;
	int cnt = ChessBoard::bitCount(m_bitbPieces[turn][piece]);
        #if DEBUG_LEVEL & 2
                DEBUG_PRINT("CountCaptured [%d, %d]@%d", turn, piece, cnt);
        #endif

	while(tmpBoard->m_parent != NULL)
	{
            #if DEBUG_LEVEL & 2
                DEBUG_PRINT(".%d", ChessBoard::bitCount(tmpBoard->m_bitbPieces[turn][piece]));
            #endif
            tmpBoard = tmpBoard->m_parent;
	}
	return ChessBoard::bitCount(tmpBoard->m_bitbPieces[turn][piece]) - cnt;
}


////////////////////////////////////////////////////////////////////////////////
// init hash key, zobrist approach
////////////////////////////////////////////////////////////////////////////////
void ChessBoard::initHashKey()
{
	int turn, piece, pos;
	m_hashKey = 0L;
	for(turn = BLACK; turn <= WHITE; turn++)
	{
		for(pos = a8; pos <= h1; pos++)
		{
			if(isPieceOfColorAt(turn, pos))
			{
				piece = pieceAt(turn, pos);
				m_hashKey ^= HASH_KEY[turn][piece][pos];
			}
		}
	}
	m_hashKey ^= HASH_OO[BLACK];
	m_hashKey ^= HASH_OO[WHITE];
	m_hashKey ^= HASH_OOO[BLACK];
	m_hashKey ^= HASH_OOO[WHITE];
	m_hashKey ^= HASH_TURN;
}

////////////////////////////////////////////////////////////////////////////////
// put a piece on the board. update all applicable memebers - bitb's etc.
// assumes m_turn == WHITE !!!!
////////////////////////////////////////////////////////////////////////////////
void ChessBoard::put(const int pos, const int piece, const int turn)
{
	BITBOARD bb = BITS[pos];
	m_bitbPieces[turn][piece] |= bb;
	m_bitbPositions[turn] |= bb;
	m_bitb |= bb;
	m_bitb_45 |= ROT_45_BITS[pos];
	m_bitb_90 |= ROT_90_BITS[pos];
	m_bitb_315 |= ROT_315_BITS[pos];

	if(piece == KING)
	{
		if(turn == WHITE)
			m_kingPos = pos;
		else
			m_o_kingPos = pos;
	} else {
            if(turn == WHITE)
                m_quality += PIECE_VALUES[piece];
            else
                m_o_quality += PIECE_VALUES[piece];

        }
}

////////////////////////////////////////////////////////////////////////////////
// put a piece on the board. 
// also update hashkey: NO KING as piece!
// returns false if put on top of another piece
// or if a piece gets attacked in case attack is not allowed
////////////////////////////////////////////////////////////////////////////////
boolean ChessBoard::putHouse(const int pos, const int piece, ChessBoard *nextBoard, ChessBoard *tmpBoard, const boolean allowAttack)
{
	BITBOARD bb = BITS[pos];
	
	// m_bitbAttackMoveSquares
	
	// not stepping on another piece
	if((bb & m_bitb) == 0)
	{
		// duplicate this board to nextBoard
		duplicate(nextBoard);
		
		// now add the piece
		nextBoard->m_bitbPieces[m_turn][piece] |= bb;
		nextBoard->m_bitbPositions[m_turn] |= bb;
		nextBoard->m_bitb |= bb;
		nextBoard->m_bitb_45 |= ROT_45_BITS[pos];
		nextBoard->m_bitb_90 |= ROT_90_BITS[pos];
		nextBoard->m_bitb_315 |= ROT_315_BITS[pos];
		nextBoard->m_hashKey ^= HASH_KEY[m_turn][piece][pos];
		
		nextBoard->m_quality += ChessBoard::PIECE_VALUES[piece];
		nextBoard->m_numBoard = m_numBoard+1;
		nextBoard->m_myMove = 0; // no move!
		
		nextBoard->switchTurn();

		// with calcState, generate moves
		nextBoard->calcState(tmpBoard);
		
		const int state = nextBoard->getState();

DEBUG_PRINT("step, state %d\n", nextBoard->getState());

		if((allowAttack || state == ChessBoard::PLAY) && state != ChessBoard::MATE)
		{
			
			nextBoard->m_parent = this;
			return true;	
		}
		
	}
	return false;
}

////////////////////////////////////////////////////////////////////////////////
// remove a piece of turn
////////////////////////////////////////////////////////////////////////////////
void ChessBoard::remove(const int t, const int p){
	m_hashKey ^= HASH_KEY[t][pieceAt(t, p)][p];
	m_bitbPieces[t][pieceAt(t, p)] &= NOT_BITS[p];
	m_bitbPositions[t] &= NOT_BITS[p];
	
	m_bitb &= NOT_BITS[p];
	m_bitb_45 &= ~ROT_45_BITS[p];
	m_bitb_90 &= ~ROT_90_BITS[p];
	m_bitb_315 &= ~ROT_315_BITS[p];
}

////////////////////////////////////////////////////////////////////////////////
// undo a move
// the current board is not deleted since it exists in refurbish table for reuse
////////////////////////////////////////////////////////////////////////////////
ChessBoard* ChessBoard::undoMove()
{
	return m_parent;
}

/////////////////////////////////////////////////////////////////////////////////////////
// methods that operate on the move array m_arrMoves
////////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////////
// returns true if moves available in generated move array
////////////////////////////////////////////////////////////////////////////////
boolean ChessBoard::hasMoreMoves()
{
	return m_indexMoves >= 0 && m_indexMoves < m_sizeMoves;
}
////////////////////////////////////////////////////////////////////////////////
// advance pointer in generated moves array
////////////////////////////////////////////////////////////////////////////////
int ChessBoard::getNextMove()
{
	return m_arrMoves[m_indexMoves++];
}
int ChessBoard::getNumMoves()
{
	return m_sizeMoves;
}
////////////////////////////////////////////////////////////////////////////////
// replace current selected element with the last element, hereby overwriting 
// the current element and decreasing size
// used for removing illegal moves
////////////////////////////////////////////////////////////////////////////////
void ChessBoard::removeMoveElementAt()
{
	m_indexMoves--;
	m_sizeMoves--;
	m_arrMoves[m_indexMoves] = m_arrMoves[m_sizeMoves];
}
void ChessBoard::addMoveElement(const int move)
{
	m_arrMoves[m_sizeMoves++] = move;
}
int ChessBoard::remainingMoves()
{
	return m_sizeMoves - m_indexMoves;
}

////////////////////////////////////////////////////////////////////////////////
// resturns pgn string representation of the move that lead to @board; 
// the move in the m_myMove member of the board
////////////////////////////////////////////////////////////////////////////////
void ChessBoard::myMoveToString(char* buf)
{
	strcpy(buf, "");
	if(m_myMove == 0){
		return;
	}
		
	if(Move_isOO(m_myMove)){
		strcat(buf, "O-O");
		strcat(buf, (this->getState() == ChessBoard::CHECK ? "+" : ""));
	}
	else if(Move_isOOO(m_myMove))
	{
		strcat(buf, "O-O-O");
		strcat(buf, (this->getState() == ChessBoard::CHECK ? "+" : ""));
	}
	else if(Move_isPromotionMove(m_myMove))
	{
		char tmp[10];
		if(Move_isHIT(m_myMove)){
			Pos::colToString(Move_getFrom(m_myMove), tmp);
			strcat(buf, tmp);
			strcat(buf, "x");
		}
		Pos::toString(Move_getTo(m_myMove), tmp);
		strcat(buf, tmp);
		strcat(buf, "=");
		ChessBoard::pieceToString(this->pieceAt(this->opponentTurn(), Move_getTo(m_myMove)), tmp);
		strcat(buf, tmp);
		if(this->getState() == ChessBoard::CHECK)
			strcat(buf, "+");
		else if(this->getState() == ChessBoard::MATE)
			strcat(buf, "#");
	}				
	else
	{
		char tmp[10];
		ChessBoard::pieceToString(this->pieceAt(this->opponentTurn(), Move_getTo(m_myMove)), tmp);
		strcat(buf, tmp);
		int m = this->ambigiousMove();
		if(m != 0){
			const int posFromAmb = Move_getFrom(m);
			const int posFrom = Move_getFrom(m_myMove);
			if(Pos::col(posFromAmb) == Pos::col(posFrom)){
				Pos::rowToString(posFrom, tmp);
				strcat(buf, tmp);
			}
			else {
				Pos::colToString(posFrom, tmp);
				strcat(buf, tmp);
			}
			//sRet += Pos.col(Move_getFrom(m)) == Pos.col(getFrom(board.getMyMove())) ? Pos.rowToString(m) : Pos.colToString(board.getMyMove());
		}
		if(Move_isHIT(m_myMove))
		{
			if(this->pieceAt(this->opponentTurn(), Move_getTo(m_myMove)) == ChessBoard::PAWN){
				Pos::colToString(Move_getFrom(m_myMove), tmp);
				strcat(buf, tmp);
			}
			strcat(buf, "x");
		}
		Pos::toString(Move_getTo(m_myMove), tmp);
		strcat(buf, tmp);
		
		if(this->getState() == ChessBoard::CHECK)
			strcat(buf, "+");
		else if(this->getState() == ChessBoard::MATE)
			strcat(buf, "#");
	}
}

//////////////////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////////
//	 to initialize the quality members after a position is set up
////////////////////////////////////////////////////////////////////////////////
void ChessBoard::calcQuality()
{
	m_quality = 0;
	m_o_quality = 0;
	int p;
	for(int i = 0; i < ChessBoard::NUM_FIELDS; i++)
	{
		if(isPieceOfColorAt(m_turn, i))
		{
			p = pieceAt(m_turn, i);
			m_quality += ChessBoard::PIECE_VALUES[p];
		}
		else if(isPieceOfColorAt(m_o_turn, i))
		{
			p = pieceAt(m_o_turn, i);
			m_o_quality += ChessBoard::PIECE_VALUES[p];
		}
	}
}
	





/////////////////////////////////////////////////////////////////////////////////////////////
// init consts
////////////////////////////////////////////////////////////////////////////////

// pre-calculated array of 64 bits BITBOARD values for the squares
const BITBOARD ChessBoard::BITS[64] = {1LL, 2LL, 4LL, 8LL, 16LL, 32LL, 64LL, 128LL, 256LL, 512LL, 1024LL, 2048LL, 4096LL, 8192LL, 16384LL, 32768LL, 65536LL, 131072LL, 262144LL, 524288LL, 1048576LL, 2097152LL, 4194304LL, 8388608LL, 16777216LL, 33554432LL, 67108864LL, 134217728LL, 268435456LL, 536870912LL, 1073741824LL, 2147483648LL, 4294967296LL, 8589934592LL, 17179869184LL, 34359738368LL, 68719476736LL, 137438953472LL, 274877906944LL, 549755813888LL, 1099511627776LL, 2199023255552LL, 4398046511104LL, 8796093022208LL, 17592186044416LL, 35184372088832LL, 70368744177664LL, 140737488355328LL, 281474976710656LL, 562949953421312LL, 1125899906842624LL, 2251799813685248LL, 4503599627370496LL, 9007199254740992LL, 18014398509481984LL, 36028797018963968LL, 72057594037927936LL, 144115188075855872LL, 288230376151711744LL, 576460752303423488LL, 1152921504606846976LL, 2305843009213693952LL, 4611686018427387904LL, -9223372036854775808ULL};
// above array, but ~
const BITBOARD ChessBoard::NOT_BITS[64] = {-2LL, -3LL, -5LL, -9LL, -17LL, -33LL, -65LL, -129LL, -257LL, -513LL, -1025LL, -2049LL, -4097LL, -8193LL, -16385LL, -32769LL, -65537LL, -131073LL, -262145LL, -524289LL, -1048577LL, -2097153LL, -4194305LL, -8388609LL, -16777217LL, -33554433LL, -67108865LL, -134217729LL, -268435457LL, -536870913LL, -1073741825LL, -2147483649LL, -4294967297LL, -8589934593LL, -17179869185LL, -34359738369LL, -68719476737LL, -137438953473LL, -274877906945LL, -549755813889LL, -1099511627777LL, -2199023255553LL, -4398046511105LL, -8796093022209LL, -17592186044417LL, -35184372088833LL, -70368744177665LL, -140737488355329LL, -281474976710657LL, -562949953421313LL, -1125899906842625LL, -2251799813685249LL, -4503599627370497LL, -9007199254740993LL, -18014398509481985LL, -36028797018963969LL, -72057594037927937LL, -144115188075855873LL, -288230376151711745LL, -576460752303423489LL, -1152921504606846977LL, -2305843009213693953LL, -4611686018427387905LL, 9223372036854775807LL};

// all bits set to one for the rows (index is row)
const BITBOARD ChessBoard::ROW_BITS[8] = {255LL, 65280LL, 16711680LL, 4278190080LL, 1095216660480LL, 280375465082880LL, 71776119061217280LL, -72057594037927936LL};
// same as ROW_BITS but for the files
const BITBOARD ChessBoard::FILE_BITS[8] = {72340172838076673LL, 144680345676153346LL, 289360691352306692LL, 578721382704613384LL, 1157442765409226768LL, 2314885530818453536LL, 4629771061636907072LL, -9187201950435737472LL};
	
const size_t ChessBoard::SIZEOF_BOARD = sizeof(ChessBoard);
////////////////////////////////////////////////////////////////////////////////

BITBOARD ChessBoard::HASH_KEY[2][NUM_PIECES][64];
BITBOARD ChessBoard::HASH_OO[2];
BITBOARD ChessBoard::HASH_OOO[2];
BITBOARD ChessBoard::HASH_TURN;


////////////////////////////////////////////////////////////////////////////////
// shift, rotation and mask arrays. index is position
////////////////////////////////////////////////////////////////////////////////

// shifts on non rotated bitboard, used for rank move generation
const int ChessBoard::SHIFT_0[64] = {
		0,  0,  0,  0,  0,  0,  0,  0,
		8,  8,  8,  8,  8,  8,  8,  8,
		16, 16, 16, 16, 16, 16, 16, 16,
		24, 24, 24, 24, 24, 24, 24, 24,
		32, 32, 32, 32, 32, 32, 32, 32,
		40, 40, 40, 40, 40, 40, 40, 40,
		48, 48, 48, 48, 48, 48, 48, 48,
		56, 56, 56, 56, 56, 56, 56, 56
};

////////////////////////////////////////////////////////////////////////////////
// "rotation" table, for the 45 degrees diagonal move generation
////////////////////////////////////////////////////////////////////////////////
const int ChessBoard::ROT_45[64] = {
		a8, b8, d8, g8, c7, h7, f6, e5,   
		c8, e8, h8, d7, a6, g6, f5, e4,
		f8, a7, e7, b6, h6, g5, f4, d3,
		b7, f7, c6, a5, h5, g4, e3, b2,
		g7, d6, b5, a4, h4, f3, c2, g2,
		e6, c5, b4, a3, g3, d2, h2, c1,
		d5, c4, b3, h3, e2, a1, d1, f1,
		d4, c3, a2, f2, b1, e1, g1, h1
};
////////////////////////////////////////////////////////////////////////////////
// same as above, but with BIT positions
////////////////////////////////////////////////////////////////////////////////
const BITBOARD ChessBoard::ROT_45_BITS[64] = {
		A8, B8, D8, G8, C7, H7, F6, E5,   
		C8, E8, H8, D7, A6, G6, F5, E4,
		F8, A7, E7, B6, H6, G5, F4, D3,
		B7, F7, C6, A5, H5, G4, E3, B2,
		G7, D6, B5, A4, H4, F3, C2, G2,
		E6, C5, B4, A3, G3, D2, H2, C1,
		D5, C4, B3, H3, E2, A1, D1, F1,
		D4, C3, A2, F2, B1, E1, G1, H1
};
////////////////////////////////////////////////////////////////////////////////
// the shifts used on the 45 bitb
////////////////////////////////////////////////////////////////////////////////
const int ChessBoard::SHIFT_45[64] = {
		 0,  1,  3,  6, 10, 15, 21, 28,
		 1,  3,  6, 10, 15, 21, 28, 36,
		 3,  6, 10, 15, 21, 28, 36, 43,
		 6, 10, 15, 21, 28, 36, 43, 49,
		10, 15, 21, 28, 36, 43, 49, 54,
		15, 21, 28, 36, 43, 49, 54, 58,
		21, 28, 36, 43, 49, 54, 58, 61,
		28, 36, 43, 49, 54, 58, 61, 63
};
////////////////////////////////////////////////////////////////////////////////
// the masks used on the 45 bitb
////////////////////////////////////////////////////////////////////////////////
const int ChessBoard::MASK_45[64] = {
		0x01, 0x03, 0x07, 0x0F, 0x1F, 0x3F, 0x7F, 0xFF,
		0x03, 0x07, 0x0F, 0x1F, 0x3F, 0x7F, 0xFF, 0x7F, 
		0x07, 0x0F, 0x1F, 0x3F, 0x7F, 0xFF, 0x7F, 0x3F, 
		0x0F, 0x1F, 0x3F, 0x7F, 0xFF, 0x7F, 0x3F, 0x1F, 
		0x1F, 0x3F, 0x7F, 0xFF, 0x7F, 0x3F, 0x1F, 0x0F, 
		0x3F, 0x7F, 0xFF, 0x7F, 0x3F, 0x1F, 0x0F, 0x07, 
		0x7F, 0xFF, 0x7F, 0x3F, 0x1F, 0x0F, 0x07, 0x03, 
		0xFF, 0x7F, 0x3F, 0x1F, 0x0F, 0x07, 0x03, 0x01};
////////////////////////////////////////////////////////////////////////////////
// "rotation" table for file move generation
////////////////////////////////////////////////////////////////////////////////
/*
const int ChessBoard::ROT_90[64] = {
		a1, a2, a3, a4, a5, a6, a7, a8,
		b1, b2, b3, b4, b5, b6, b7, b8,
		c1, c2, c3, c4, c5, c6, c7, c8,
		d1, d2, d3, d4, d5, d6, d7, d8,
		e1, e2, e3, e4, e5, e6, e7, e8,
		f1, f2, f3, f4, f5, f6, f7, f8,
		g1, g2, g3, g4, g5, g6, g7, g8,
		h1, h2, h3, h4, h5, h6, h7, h8
};
*/
////////////////////////////////////////////////////////////////////////////////
const BITBOARD ChessBoard::ROT_90_BITS[64] = {
		A1, A2, A3, A4, A5, A6, A7, A8,
		B1, B2, B3, B4, B5, B6, B7, B8,
		C1, C2, C3, C4, C5, C6, C7, C8,
		D1, D2, D3, D4, D5, D6, D7, D8,
		E1, E2, E3, E4, E5, E6, E7, E8,
		F1, F2, F3, F4, F5, F6, F7, F8,
		G1, G2, G3, G4, G5, G6, G7, G8,
		H1, H2, H3, H4, H5, H6, H7, H8
		};
////////////////////////////////////////////////////////////////////////////////
const int ChessBoard::SHIFT_90[64] = {
		56, 48, 40, 32, 24, 16, 8, 0,
		56, 48, 40, 32, 24, 16, 8, 0,
		56, 48, 40, 32, 24, 16, 8, 0,
		56, 48, 40, 32, 24, 16, 8, 0,
		56, 48, 40, 32, 24, 16, 8, 0,
		56, 48, 40, 32, 24, 16, 8, 0,
		56, 48, 40, 32, 24, 16, 8, 0,
		56, 48, 40, 32, 24, 16, 8, 0
		
};
////////////////////////////////////////////////////////////////////////////////
/*
const int ChessBoard::ROT_315[64] = {
		e5, e4, d3, b2, g2, c1, f1, h1,
		f6, f5, f4, e3, c2, h2, d1, g1,
		h7, g6, g5, g4, f3, d2, a1, e1,
		c7, a6, h6, h5, h4, g3, e2, b1,
		g8, d7, b6, a5, a4, a3, h3, f2,
		d8, h8, e7, c6, b5, b4, b3, a2,
		b8, e8, a7, f7, d6, c5, c4, c3,
		a8, c8, f8, b7, g7, e6, d5, d4};
*/
////////////////////////////////////////////////////////////////////////////////
const BITBOARD ChessBoard::ROT_315_BITS[64] = {
		E5, E4, D3, B2, G2, C1, F1, H1,
		F6, F5, F4, E3, C2, H2, D1, G1,
		H7, G6, G5, G4, F3, D2, A1, E1,
		C7, A6, H6, H5, H4, G3, E2, B1,
		G8, D7, B6, A5, A4, A3, H3, F2,
		D8, H8, E7, C6, B5, B4, B3, A2,
		B8, E8, A7, F7, D6, C5, C4, C3,
		A8, C8, F8, B7, G7, E6, D5, D4
};
////////////////////////////////////////////////////////////////////////////////
const int ChessBoard::SHIFT_315[64] = {
		28, 36, 43, 49, 54, 58, 61, 63,
		21, 28, 36, 43, 49, 54, 58, 61,
		15, 21, 28, 36, 43, 49, 54, 58,
		10, 15, 21, 28, 36, 43, 49, 54,
		6, 10, 15, 21, 28, 36, 43, 49,
		3,  6, 10, 15, 21, 28, 36, 43,
		1,  3,  6, 10, 15, 21, 28, 36,
		0,  1,  3,  6, 10, 15, 21, 28
};
////////////////////////////////////////////////////////////////////////////////
const int ChessBoard::MASK_315[64] = {
		
		0xFF, 0x7F, 0x3F, 0x1F, 0x0F, 0x07, 0x03, 0x01,
		0x7F, 0xFF, 0x7F, 0x3F, 0x1F, 0x0F, 0x07, 0x03,
		0x3F, 0x7F, 0xFF, 0x7F, 0x3F, 0x1F, 0x0F, 0x07,
		0x1F, 0x3F, 0x7F, 0xFF, 0x7F, 0x3F, 0x1F, 0x0F,
		0x0F, 0x1F, 0x3F, 0x7F, 0xFF, 0x7F, 0x3F, 0x1F,
		0x07, 0x0F, 0x1F, 0x3F, 0x7F, 0xFF, 0x7F, 0x3F,
		0x03, 0x07, 0x0F, 0x1F, 0x3F, 0x7F, 0xFF, 0x7F,
		0x01, 0x03, 0x07, 0x0F, 0x1F, 0x3F, 0x7F, 0xFF
};
////////////////////////////////////////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////////
// instead of calling a method row(pos), position is index on array for the rows
const int ChessBoard::ROW[64] = {
		0, 0, 0, 0, 0, 0, 0, 0,
		 1, 1, 1, 1, 1, 1, 1, 1, 
		 2, 2, 2, 2, 2, 2, 2, 2,
		 3, 3, 3, 3, 3, 3, 3, 3, 
		 4, 4, 4, 4, 4, 4, 4, 4, 
		 5, 5, 5, 5, 5, 5, 5, 5, 
		 6, 6, 6, 6, 6, 6, 6, 6, 
		 7, 7, 7, 7, 7, 7, 7, 7};
////////////////////////////////////////////////////////////////////////////////
// as with ROW, but for column
const int ChessBoard::COL[64] = {
		0, 1, 2, 3, 4, 5, 6, 7,
	 0, 1, 2, 3, 4, 5, 6, 7,
	 0, 1, 2, 3, 4, 5, 6, 7,
	 0, 1, 2, 3, 4, 5, 6, 7,
	 0, 1, 2, 3, 4, 5, 6, 7,
	 0, 1, 2, 3, 4, 5, 6, 7,
	 0, 1, 2, 3, 4, 5, 6, 7,
	 0, 1, 2, 3, 4, 5, 6, 7};

////////////////////////////////////////////////////////////////////////////////
// bitb arrays, indexed on position. contains the the bit positions that can be 
// reached from that position
const int ChessBoard::PIECE_VALUES[6] = {100, 320, 340, 500, 900, 0};
const BITBOARD ChessBoard::BISHOP_RANGE[64] = {-9205322385119247872LL, 36099303471056128LL, 141012904249856LL, 550848566272LL, 6480472064LL, 1108177604608LL, 283691315142656LL, 72624976668147712LL, 4620710844295151618LL, -9205322385119182843LL, 36099303487963146LL, 141017232965652LL, 1659000848424LL, 283693466779728LL, 72624976676520096LL, 145249953336262720LL, 2310355422147510788LL, 4620710844311799048LL, -9205322380790986223LL, 36100411639206946LL, 424704217196612LL, 72625527495610504LL, 145249955479592976LL, 290499906664153120LL, 1155177711057110024LL, 2310355426409252880LL, 4620711952330133792LL, -9205038694072573375LL, 108724279602332802LL, 145390965166737412LL, 290500455356698632LL, 580999811184992272LL, 577588851267340304LL, 1155178802063085600LL, 2310639079102947392LL, 4693335752243822976LL, -9060072569221905919LL, 326598935265674242LL, 581140276476643332LL, 1161999073681608712LL, 288793334762704928LL, 577868148797087808LL, 1227793891648880768LL, 2455587783297826816LL, 4911175566595588352LL, -8624392940535152127LL, 1197958188344280066LL, 2323857683139004420LL, 144117404414255168LL, 360293502378066048LL, 720587009051099136LL, 1441174018118909952LL, 2882348036221108224LL, 5764696068147249408LL, -6917353036926680575LL, 4611756524879479810LL, 567382630219904LL, 1416240237150208LL, 2833579985862656LL, 5667164249915392LL, 11334324221640704LL, 22667548931719168LL, 45053622886727936LL, 18049651735527937LL};
const BITBOARD ChessBoard::ROOK_RANGE[64] = {72340172838076926LL, 144680345676153597LL, 289360691352306939LL, 578721382704613623LL, 1157442765409226991LL, 2314885530818453727LL, 4629771061636907199LL, -9187201950435737473LL, 72340172838141441LL, 144680345676217602LL, 289360691352369924LL, 578721382704674568LL, 1157442765409283856LL, 2314885530818502432LL, 4629771061636939584LL, -9187201950435737728LL, 72340172854657281LL, 144680345692602882LL, 289360691368494084LL, 578721382720276488LL, 1157442765423841296LL, 2314885530830970912LL, 4629771061645230144LL, -9187201950435803008LL, 72340177082712321LL, 144680349887234562LL, 289360695496279044LL, 578721386714368008LL, 1157442769150545936LL, 2314885534022901792LL, 4629771063767613504LL, -9187201950452514688LL, 72341259464802561LL, 144681423712944642LL, 289361752209228804LL, 578722409201797128LL, 1157443723186933776LL, 2314886351157207072LL, 4629771607097753664LL, -9187201954730704768LL, 72618349279904001LL, 144956323094725122LL, 289632270724367364LL, 578984165983651848LL, 1157687956502220816LL, 2315095537539358752LL, 4629910699613634624LL, -9187203049947365248LL, 143553341945872641LL, 215330564830528002LL, 358885010599838724LL, 645993902138460168LL, 1220211685215703056LL, 2368647251370188832LL, 4665518383679160384LL, -9187483425412448128LL, -143832609275707135LL, -215607624513486334LL, -359157654989044732LL, -646257715940161528LL, -1220457837842395120LL, -2368858081646862304LL, -4665658569255796672LL, 9187484529235886208LL};
const BITBOARD ChessBoard::QUEEN_RANGE[64] = {-9132982212281170946LL, 180779649147209725LL, 289501704256556795LL, 578721933553179895LL, 1157442771889699055LL, 2314886638996058335LL, 4630054752952049855LL, -9114576973767589761LL, 4693051017133293059LL, -9060642039442965241LL, 325459994840333070LL, 578862399937640220LL, 1157444424410132280LL, 2315169224285282160LL, 4702396038313459680LL, -9041951997099475008LL, 2382695595002168069LL, 4765391190004401930LL, -8915961689422492139LL, 614821794359483434LL, 1157867469641037908LL, 2387511058326581416LL, 4775021017124823120LL, -8896702043771649888LL, 1227517888139822345LL, 2455035776296487442LL, 4910072647826412836LL, -8626317307358205367LL, 1266167048752878738LL, 2460276499189639204LL, 4920271519124312136LL, -8606202139267522416LL, 649930110732142865LL, 1299860225776030242LL, 2600000831312176196LL, 5272058161445620104LL, -7902628846034972143LL, 2641485286422881314LL, 5210911883574396996LL, -8025202881049096056LL, 361411684042608929LL, 722824471891812930LL, 1517426162373248132LL, 3034571949281478664LL, 6068863523097809168LL, -6309297402995793375LL, 5827868887957914690LL, -6863345366808360828LL, 287670746360127809LL, 575624067208594050LL, 1079472019650937860LL, 2087167920257370120LL, 4102559721436811280LL, 8133343319517438240LL, -2251834653247520191LL, -4575726900532968318LL, -143265226645487231LL, -214191384276336126LL, -356324075003182076LL, -640590551690246136LL, -1209123513620754416LL, -2346190532715143136LL, -4620604946369068736LL, 9205534180971414145LL};
const BITBOARD ChessBoard::KNIGHT_RANGE[64] = {132096LL, 329728LL, 659712LL, 1319424LL, 2638848LL, 5277696LL, 10489856LL, 4202496LL, 33816580LL, 84410376LL, 168886289LL, 337772578LL, 675545156LL, 1351090312LL, 2685403152LL, 1075839008LL, 8657044482LL, 21609056261LL, 43234889994LL, 86469779988LL, 172939559976LL, 345879119952LL, 687463207072LL, 275414786112LL, 2216203387392LL, 5531918402816LL, 11068131838464LL, 22136263676928LL, 44272527353856LL, 88545054707712LL, 175990581010432LL, 70506185244672LL, 567348067172352LL, 1416171111120896LL, 2833441750646784LL, 5666883501293568LL, 11333767002587136LL, 22667534005174272LL, 45053588738670592LL, 18049583422636032LL, 145241105196122112LL, 362539804446949376LL, 725361088165576704LL, 1450722176331153408LL, 2901444352662306816LL, 5802888705324613632LL, -6913025356609880064LL, 4620693356194824192LL, 288234782788157440LL, 576469569871282176LL, 1224997833292120064LL, 2449995666584240128LL, 4899991333168480256LL, -8646761407372591104LL, 1152939783987658752LL, 2305878468463689728LL, 1128098930098176LL, 2257297371824128LL, 4796069720358912LL, 9592139440717824LL, 19184278881435648LL, 38368557762871296LL, 4679521487814656LL, 9077567998918656LL};
const BITBOARD ChessBoard::KING_RANGE[64] = {770LL, 1797LL, 3594LL, 7188LL, 14376LL, 28752LL, 57504LL, 49216LL, 197123LL, 460039LL, 920078LL, 1840156LL, 3680312LL, 7360624LL, 14721248LL, 12599488LL, 50463488LL, 117769984LL, 235539968LL, 471079936LL, 942159872LL, 1884319744LL, 3768639488LL, 3225468928LL, 12918652928LL, 30149115904LL, 60298231808LL, 120596463616LL, 241192927232LL, 482385854464LL, 964771708928LL, 825720045568LL, 3307175149568LL, 7718173671424LL, 15436347342848LL, 30872694685696LL, 61745389371392LL, 123490778742784LL, 246981557485568LL, 211384331665408LL, 846636838289408LL, 1975852459884544LL, 3951704919769088LL, 7903409839538176LL, 15806819679076352LL, 31613639358152704LL, 63227278716305408LL, 54114388906344448LL, 216739030602088448LL, 505818229730443264LL, 1011636459460886528LL, 2023272918921773056LL, 4046545837843546112LL, 8093091675687092224LL, -2260560722335367168LL, -4593460513685372928LL, 144959613005987840LL, 362258295026614272LL, 724516590053228544LL, 1449033180106457088LL, 2898066360212914176LL, 5796132720425828352LL, -6854478632857894912LL, 4665729213955833856LL};

//TODO BITBOARD[][] PAWN_RANGE (two indexes, because pawn only in one direction)
// combined BISHOP, ROOK, and KNIGHT ranges
//const BITBOARD[] MOVE_CROSS = {-9132982212281038850LL, 180779649147539453LL, 289501704257216507LL, 578721933554499319LL, 1157442771892337903LL, 2314886639001336031LL, 4630054752962539711LL, -9114576973763387265LL, 4693051017167109639LL, -9060642039358554865LL, 325459995009219359LL, 578862400275412798LL, 1157444425085677436LL, 2315169225636372472LL, 4702396040998862832LL, -9041951996023636000LL, 2382695603659212551LL, 4765391211613458191LL, -8915961646187602145LL, 614821880829263422LL, 1157867642580597884LL, 2387511404205701368LL, 4775021704588030192LL, -8896701768356863776LL, 1227520104343209737LL, 2455041308214890258LL, 4910083715958251300LL, -8626295171094528439LL, 1266211321280232594LL, 2460365044244346916LL, 4920447509705322568LL, -8606131633082277744LL, 650497458799315217LL, 1301276396887151138LL, 2602834273062822980LL, 5277725044946913672LL, -7891295079032385007LL, 2664152820428055586LL, 5255965472313067588LL, -8007153297626460024LL, 506652789238731041LL, 1085364276338762306LL, 2242787250538824836LL, 4485294125612632072LL, 8970307875760115984LL, -506408697671179743LL, -1085156468651965374LL, -2242652010613536636LL, 575905529148285249LL, 1152093637079876226LL, 2304469852943057924LL, 4537163586841610248LL, 9002551054605291536LL, -513418087855152864LL, -1098894869259861439LL, -2269848432069278590LL, -142137127715389055LL, -211934086904511998LL, -351528005282823164LL, -630998412249528312LL, -1189939234739318768LL, -2307821974952271840LL, -4615925424881254080LL, 9214611748970332801LL};
const int ChessBoard::ROW_TURN[2][64] = {
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
	
////////////////////////////////////////////////////////////////////////////////
const int ChessBoard::VALUATION_KING_ENDINGS[64] = {
				0,  6, 12, 18, 18, 12,  6,  0,
				6, 12, 18, 24, 24, 18, 12,  6,
				12, 18, 24, 32, 32, 24, 18, 12,
				18, 24, 32, 48, 48, 32, 24, 18,
				18, 24, 32, 48, 48, 32, 24, 18,
				12, 18, 24, 32, 32, 24, 18, 12,
				6, 12, 18, 24, 24, 18, 12,  6,
				0,  6, 12, 18, 18, 12,  6,  0
};
////////////////////////////////////////////////////////////////////////////////
// kbnk score, first index is "square color" of the bishop, second index the position of the losing king
// after gnu-chess
const int ChessBoard::VALUATION_KBNK_SCORE[2][64] = {
			{
		0, 10, 20, 30, 40, 50, 60, 70,
		10, 20, 30, 40, 50, 60, 70, 60,
		20, 30, 40, 50, 60, 70, 60, 50,
		30, 40, 50, 60, 70, 60, 50, 40,
		40, 50, 60, 70, 60, 50, 40, 30,
		50, 60, 70, 60, 50, 40, 30, 20,
		60, 70, 60, 50, 40, 30, 20, 10,
		70, 60, 50, 40, 30, 20, 10,  0
			},
			{
		70, 60, 50, 40, 30, 20, 10,  0,
		60, 70, 60, 50, 40, 30, 20, 10,
		50, 60, 70, 60, 50, 40, 30, 20,
		40, 50, 60, 70, 60, 50, 40, 30,
		30, 40, 50, 60, 70, 60, 50, 40,
		20, 30, 40, 50, 60, 70, 60, 50,
		10, 20, 30, 40, 50, 60, 70, 60,
		0, 10, 20, 30, 40, 50, 60, 70,
			}
			
	};


////////////////////////////////////////////////////////////////////////////////
// trailing zeros precalculated on 8bit numbers
const char ChessBoard::TRAILING_ZEROS_8_BITS[256] = {0,0,1,0,2,0,1,0,3,0,1,0,2,0,1,0,4,0,1,0,2,0,1,0,3,0,1,0,2,0,1,0,5,0,1,0,2,0,1,0,3,0,1,0,2,0,1,0,4,0,1,0,2,0,1,0,3,0,1,0,2,0,1,0,6,0,1,0,2,0,1,0,3,0,1,0,2,0,1,0,4,0,1,0,2,0,1,0,3,0,1,0,2,0,1,0,5,0,1,0,2,0,1,0,3,0,1,0,2,0,1,0,4,0,1,0,2,0,1,0,3,0,1,0,2,0,1,0,7,0,1,0,2,0,1,0,3,0,1,0,2,0,1,0,4,0,1,0,2,0,1,0,3,0,1,0,2,0,1,0,5,0,1,0,2,0,1,0,3,0,1,0,2,0,1,0,4,0,1,0,2,0,1,0,3,0,1,0,2,0,1,0,6,0,1,0,2,0,1,0,3,0,1,0,2,0,1,0,4,0,1,0,2,0,1,0,3,0,1,0,2,0,1,0,5,0,1,0,2,0,1,0,3,0,1,0,2,0,1,0,4,0,1,0,2,0,1,0,3,0,1,0,2,0,1,0};
// too much to precalc, filled by instantiation of a Game object
// precalculated bitcount of 8bit numbers
//const char ChessBoard::BIT_COUNT_8_BITS[] =  {0,1,1,2,1,2,2,3,1,2,2,3,2,3,3,4,1,2,2,3,2,3,3,4,2,3,3,4,3,4,4,5,1,2,2,3,2,3,3,4,2,3,3,4,3,4,4,5,2,3,3,4,3,4,4,5,3,4,4,5,4,5,5,6,1,2,2,3,2,3,3,4,2,3,3,4,3,4,4,5,2,3,3,4,3,4,4,5,3,4,4,5,4,5,5,6,2,3,3,4,3,4,4,5,3,4,4,5,4,5,5,6,3,4,4,5,4,5,5,6,4,5,5,6,5,6,6,7,1,2,2,3,2,3,3,4,2,3,3,4,3,4,4,5,2,3,3,4,3,4,4,5,3,4,4,5,4,5,5,6,2,3,3,4,3,4,4,5,3,4,4,5,4,5,5,6,3,4,4,5,4,5,5,6,4,5,5,6,5,6,6,7,2,3,3,4,3,4,4,5,3,4,4,5,4,5,5,6,3,4,4,5,4,5,5,6,4,5,5,6,5,6,6,7,3,4,4,5,4,5,5,6,4,5,5,6,5,6,6,7,4,5,5,6,5,6,6,7,5,6,6,7,6,7,7,8};

int ChessBoard::trailingZeros(const BITBOARD bb)
{
	if((bb & 0xFFFFFFFF00000000LL) != 0)
	{
		if((bb & 0x0000FFFF00000000LL) != 0)
			return TRAILING_ZEROS_16_BITS[(int)((bb >> 32) & 0xFFFF)] + 32;
		return TRAILING_ZEROS_16_BITS[(int)((bb >> 48) & 0xFFFF)] + 48;	
	}
	else
	{
		if((bb & 0xFFFF) != 0)
			return TRAILING_ZEROS_16_BITS[(int)(bb & 0xFFFF)];
		return TRAILING_ZEROS_16_BITS[(int)((bb >> 16) & 0xFFFF)] + 16;
	}
}

// number of bits in bitb @bb
int ChessBoard::bitCount(const BITBOARD bb)
{
    /*
	return BIT_COUNT_8_BITS[(int)((bb >> 56) & 0xFF)] + BIT_COUNT_8_BITS[(int)((bb >> 48) & 0xFF)] + 
		       BIT_COUNT_8_BITS[(int)((bb >> 40) & 0xFF)] + BIT_COUNT_8_BITS[(int)((bb >> 32) & 0xFF)] + 
			   BIT_COUNT_8_BITS[(int)((bb >> 24) & 0xFF)] + BIT_COUNT_8_BITS[(int)((bb >> 16) & 0xFF)] + 
			   BIT_COUNT_8_BITS[(int)((bb >> 8) & 0xFF)] + BIT_COUNT_8_BITS[(int)(bb & 0xFF)]; 
	*/
	return (
		ChessBoard::BIT_COUNT_16_BITS[(int)((bb >> 48) & 0xffff)] +
		ChessBoard::BIT_COUNT_16_BITS[(int)((bb >> 32) & 0xffff)] +
		ChessBoard::BIT_COUNT_16_BITS[(int)((bb >> 16) & 0xffff)] +
		ChessBoard::BIT_COUNT_16_BITS[(int)(bb & 0xffff)]);

}


////////////////////////////////////////////////////////////////////////////////
int ChessBoard::DISTANCE[64][64];
int ChessBoard::HOOK_DISTANCE[64][64];
int ChessBoard::COL_AROOK = 0;
int ChessBoard::COL_HROOK = 7;
BITBOARD ChessBoard::HASH_KEYS[773];
BITBOARD ChessBoard::RANK_MOVES[64][256];
BITBOARD ChessBoard::DIAG_45_MOVES[64][256];
BITBOARD ChessBoard::FILE_MOVES[64][256];
BITBOARD ChessBoard::DIAG_315_MOVES[64][256];
BITBOARD ChessBoard::PASSED_PAWN_MASK[2][64];
BITBOARD ChessBoard::PAWN_RANGE[2][64];
char ChessBoard::TRAILING_ZEROS_16_BITS[65536];
char ChessBoard::BIT_COUNT_16_BITS[65536];

int ChessBoard::ARRVALUATION[64];

////////////////////////////////////////////////////////////////////////////////
// initialize all statics
////////////////////////////////////////////////////////////////////////////////
void ChessBoard::initStatics(){
	initBitCount();
	initMoveArrays();
	initTrailingZeros();
	initDistance();
	initHashKeys();
	initPassedPawnMask();
        initPawnRange();

}

void ChessBoard::initValuation(){

    //{100, 320, 340, 500, 900, 0};

    ARRVALUATION[PAWN] = 100;
    ARRVALUATION[KNIGHT] = 100;
    ARRVALUATION[BISHOP] = 100;
    ARRVALUATION[ROOK] = 100;
    ARRVALUATION[QUEEN] = 100;
    ARRVALUATION[KING] = 100;

    ARRVALUATION[INDEX_VALUATION_DRAW_REPEAT] = -10;
}

void ChessBoard::initBitCount(){
   int i, j, n;
   ChessBoard::BIT_COUNT_16_BITS[0] = 0;
   ChessBoard::BIT_COUNT_16_BITS[1] = 1; 
   i = 1;
   for (n = 2; n <= 16; n++)
   {
      i <<= 1;
      for (j = i; j <= i + (i-1); j++)  
         ChessBoard::BIT_COUNT_16_BITS[j] = 1 + ChessBoard::BIT_COUNT_16_BITS[j - i]; 
   }
}

////////////////////////////////////////////////////////////////////////////////
void ChessBoard::initDistance(){
	///////////////////////////////////
	int from, to;
	int d1, d2;

	for (from = 0; from < 64; from++)
	{
		for (to = from; to < 64; to++)
		{
			d1 = (to & 0x07) - (from & 0x07);
			if (d1 < 0) d1 = -d1;
			d2 = (to >> 3) - (from >> 3);
			if (d2 < 0) d2 = -d2;
			DISTANCE[from][to] = d1 > d2 ? d1 : d2;
			DISTANCE[to][from] = d1 > d2 ? d1 : d2;
			HOOK_DISTANCE[from][to] = d1 + d2;
			HOOK_DISTANCE[to][from] = d1 + d2;
		}
	}
	///////////////////////////////////
}

////////////////////////////////////////////////////////////////////////////////
// once generated random, now reused (used in opening database table)
////////////////////////////////////////////////////////////////////////////////
void ChessBoard::initHashKeys(){

// hash keys
	HASH_KEYS[0] = -3887515391794633976LL;
	HASH_KEYS[1] = 1618713325472305604LL;
	HASH_KEYS[2] = -2122491600727178445LL;
	HASH_KEYS[3] = -2464830289230506483LL;
	HASH_KEYS[4] = 8297201798049077347LL;
	HASH_KEYS[5] = -3090843879573007843LL;
	HASH_KEYS[6] = 3582832667336433280LL;
	HASH_KEYS[7] = 6833303169546872419LL;
	HASH_KEYS[8] = -160899919781831547LL;
	HASH_KEYS[9] = -2262497291626748420LL;
	HASH_KEYS[10] = -3072634403468528011LL;
	HASH_KEYS[11] = 6859599596957403415LL;
	HASH_KEYS[12] = -8136426246383276169LL;
	HASH_KEYS[13] = -9100890461305418218LL;
	HASH_KEYS[14] = 7102617452634897238LL;
	HASH_KEYS[15] = 7987117607452259056LL;
	HASH_KEYS[16] = 2271324216057486159LL;
	HASH_KEYS[17] = -4310880931146689164LL;
	HASH_KEYS[18] = -4940128844699982136LL;
	HASH_KEYS[19] = -4155974406089528563LL;
	HASH_KEYS[20] = -3328620188191523854LL;
	HASH_KEYS[21] = 5246598219355986596LL;
	HASH_KEYS[22] = -2784908590925285652LL;
	HASH_KEYS[23] = -780782224110294897LL;
	HASH_KEYS[24] = 2352981736425778842LL;
	HASH_KEYS[25] = 6147354870713647679LL;
	HASH_KEYS[26] = 2311590175353950794LL;
	HASH_KEYS[27] = -4989567775299348595LL;
	HASH_KEYS[28] = 657118869277616605LL;
	HASH_KEYS[29] = 8049397526582595583LL;
	HASH_KEYS[30] = -5574761150728940746LL;
	HASH_KEYS[31] = 1416542083662841864LL;
	HASH_KEYS[32] = -8912679667168658157LL;
	HASH_KEYS[33] = -7313994829489575682LL;
	HASH_KEYS[34] = 5566826630200622115LL;
	HASH_KEYS[35] = 4240141227045175987LL;
	HASH_KEYS[36] = -6068491842871904084LL;
	HASH_KEYS[37] = -8477820689424859932LL;
	HASH_KEYS[38] = -3550733021811109117LL;
	HASH_KEYS[39] = -3456616896791738609LL;
	HASH_KEYS[40] = -3998186030067106528LL;
	HASH_KEYS[41] = -1310548843913914166LL;
	HASH_KEYS[42] = -2469252391705565837LL;
	HASH_KEYS[43] = 135921311297990947LL;
	HASH_KEYS[44] = 5553303195127591860LL;
	HASH_KEYS[45] = -7263857571843104906LL;
	HASH_KEYS[46] = 8479421558178100158LL;
	HASH_KEYS[47] = -8898259035891988643LL;
	HASH_KEYS[48] = -2876418438943265550LL;
	HASH_KEYS[49] = 3473621807883086819LL;
	HASH_KEYS[50] = 2368156701501588716LL;
	HASH_KEYS[51] = -4452395828860063720LL;
	HASH_KEYS[52] = -3104198608121468510LL;
	HASH_KEYS[53] = 7121743522942933876LL;
	HASH_KEYS[54] = 4379906253017523019LL;
	HASH_KEYS[55] = 1601155686663294860LL;
	HASH_KEYS[56] = -6563605828135482902LL;
	HASH_KEYS[57] = 8237464688391650996LL;
	HASH_KEYS[58] = -2908746279798473014LL;
	HASH_KEYS[59] = 9127601564266892678LL;
	HASH_KEYS[60] = -1424667846527032031LL;
	HASH_KEYS[61] = 5971365416222651356LL;
	HASH_KEYS[62] = 363021461205989900LL;
	HASH_KEYS[63] = 913084266175578882LL;
	HASH_KEYS[64] = -6189372464412587648LL;
	HASH_KEYS[65] = -3002693767339481033LL;
	HASH_KEYS[66] = -979554569399296754LL;
	HASH_KEYS[67] = -1220625276646551499LL;
	HASH_KEYS[68] = 3219907687368025133LL;
	HASH_KEYS[69] = -5216559098636303843LL;
	HASH_KEYS[70] = 725351712876671445LL;
	HASH_KEYS[71] = -4182046424690812509LL;
	HASH_KEYS[72] = -5372107386372386705LL;
	HASH_KEYS[73] = 1034762372522717765LL;
	HASH_KEYS[74] = 1652303623644645192LL;
	HASH_KEYS[75] = 8858565048235978494LL;
	HASH_KEYS[76] = 4882395574982529111LL;
	HASH_KEYS[77] = -3744836066628604341LL;
	HASH_KEYS[78] = 1804804830168587733LL;
	HASH_KEYS[79] = -6692893398193924136LL;
	HASH_KEYS[80] = 8627919309423520632LL;
	HASH_KEYS[81] = 8855979641264558440LL;
	HASH_KEYS[82] = -3023065845219689832LL;
	HASH_KEYS[83] = -694288578012313098LL;
	HASH_KEYS[84] = -2864019839242160377LL;
	HASH_KEYS[85] = -8389290010720983234LL;
	HASH_KEYS[86] = 2817458033488126736LL;
	HASH_KEYS[87] = -6334030900859435355LL;
	HASH_KEYS[88] = -326444417329265031LL;
	HASH_KEYS[89] = -8301955228770313065LL;
	HASH_KEYS[90] = -7448534967355686519LL;
	HASH_KEYS[91] = 7786946300211585352LL;
	HASH_KEYS[92] = -6773433793914879624LL;
	HASH_KEYS[93] = -5391682907695457742LL;
	HASH_KEYS[94] = 2209142212137005149LL;
	HASH_KEYS[95] = -6126726848380589045LL;
	HASH_KEYS[96] = 4710699039160526131LL;
	HASH_KEYS[97] = -5893783869932944013LL;
	HASH_KEYS[98] = 7729188283735452958LL;
	HASH_KEYS[99] = -2270552722320863667LL;
	HASH_KEYS[100] = 4835411571316468640LL;
	HASH_KEYS[101] = 1376932795657202434LL;
	HASH_KEYS[102] = 6579385618470204823LL;
	HASH_KEYS[103] = 5603765830212747820LL;
	HASH_KEYS[104] = 7082805172250418008LL;
	HASH_KEYS[105] = -7619569524430335908LL;
	HASH_KEYS[106] = 8079611058185603746LL;
	HASH_KEYS[107] = 1822859649409071745LL;
	HASH_KEYS[108] = 1321472875971362427LL;
	HASH_KEYS[109] = -4962538606417220443LL;
	HASH_KEYS[110] = 7754212555309440172LL;
	HASH_KEYS[111] = 2193951321357774897LL;
	HASH_KEYS[112] = -5337572998044253304LL;
	HASH_KEYS[113] = 758633325419727857LL;
	HASH_KEYS[114] = -2405065145023324546LL;
	HASH_KEYS[115] = 1495502898408937558LL;
	HASH_KEYS[116] = -6477381692426894035LL;
	HASH_KEYS[117] = -7321322992354240538LL;
	HASH_KEYS[118] = 5793969849605112171LL;
	HASH_KEYS[119] = 6384166267618853305LL;
	HASH_KEYS[120] = -7067953440360335160LL;
	HASH_KEYS[121] = -5603510226804766192LL;
	HASH_KEYS[122] = 1745604099864336742LL;
	HASH_KEYS[123] = 8974309444965751486LL;
	HASH_KEYS[124] = -8104283513965738137LL;
	HASH_KEYS[125] = -4563498078769036054LL;
	HASH_KEYS[126] = 1230871996501305501LL;
	HASH_KEYS[127] = -1999451243446985000LL;
	HASH_KEYS[128] = -5420497002073731838LL;
	HASH_KEYS[129] = -4727867464857479498LL;
	HASH_KEYS[130] = -428565668746521922LL;
	HASH_KEYS[131] = 9186552008772594934LL;
	HASH_KEYS[132] = 6133187556349395043LL;
	HASH_KEYS[133] = 9102469400428208648LL;
	HASH_KEYS[134] = 2040633341071309068LL;
	HASH_KEYS[135] = -3647534040977718124LL;
	HASH_KEYS[136] = -4596293072898375048LL;
	HASH_KEYS[137] = 5367651714067148939LL;
	HASH_KEYS[138] = -5572124903686340933LL;
	HASH_KEYS[139] = 2139691612454955917LL;
	HASH_KEYS[140] = 6668406916137719456LL;
	HASH_KEYS[141] = 621757085273484368LL;
	HASH_KEYS[142] = 924686238998541093LL;
	HASH_KEYS[143] = 1120648275109756025LL;
	HASH_KEYS[144] = -1855972302613773730LL;
	HASH_KEYS[145] = 8740460901241946447LL;
	HASH_KEYS[146] = -8962301031592089728LL;
	HASH_KEYS[147] = 1339901850123918154LL;
	HASH_KEYS[148] = -7850880952994786146LL;
	HASH_KEYS[149] = -588214458158585976LL;
	HASH_KEYS[150] = -8534221254614062855LL;
	HASH_KEYS[151] = 3549331442362569844LL;
	HASH_KEYS[152] = 4949914453112362469LL;
	HASH_KEYS[153] = 8149094262223279283LL;
	HASH_KEYS[154] = 8515753652720051489LL;
	HASH_KEYS[155] = -6310383481305816720LL;
	HASH_KEYS[156] = -4330787545806856150LL;
	HASH_KEYS[157] = -7887477125133347318LL;
	HASH_KEYS[158] = 1791881858316809695LL;
	HASH_KEYS[159] = -3862993146235873466LL;
	HASH_KEYS[160] = -6156965076217700849LL;
	HASH_KEYS[161] = 8863600266574303633LL;
	HASH_KEYS[162] = 3085847756939416809LL;
	HASH_KEYS[163] = 6960872996706483258LL;
	HASH_KEYS[164] = -6017732995716844809LL;
	HASH_KEYS[165] = -444754870109835097LL;
	HASH_KEYS[166] = -2438041400935417996LL;
	HASH_KEYS[167] = -5663413866710989666LL;
	HASH_KEYS[168] = 5957269100557929197LL;
	HASH_KEYS[169] = -6276245087726767820LL;
	HASH_KEYS[170] = 789788858867995111LL;
	HASH_KEYS[171] = 8971951051190188547LL;
	HASH_KEYS[172] = 9044133453507105290LL;
	HASH_KEYS[173] = -1604046211836420310LL;
	HASH_KEYS[174] = 6455089998987723824LL;
	HASH_KEYS[175] = -6152101886047958995LL;
	HASH_KEYS[176] = -6967608803926272867LL;
	HASH_KEYS[177] = 377756316981442389LL;
	HASH_KEYS[178] = 4924681456913280205LL;
	HASH_KEYS[179] = -7910046933544291688LL;
	HASH_KEYS[180] = -7438609959223572889LL;
	HASH_KEYS[181] = -2198465175231860782LL;
	HASH_KEYS[182] = -2229821671104043831LL;
	HASH_KEYS[183] = -7694532307193138507LL;
	HASH_KEYS[184] = 8503693372609934022LL;
	HASH_KEYS[185] = -1111829576312771962LL;
	HASH_KEYS[186] = 1566934104068137940LL;
	HASH_KEYS[187] = 8691997373314235786LL;
	HASH_KEYS[188] = 8865555456937301103LL;
	HASH_KEYS[189] = 2810153466171543336LL;
	HASH_KEYS[190] = 3056613240054516545LL;
	HASH_KEYS[191] = -5667188051815419528LL;
	HASH_KEYS[192] = 8893444315696878798LL;
	HASH_KEYS[193] = 8067500586468510759LL;
	HASH_KEYS[194] = 1465649885910145729LL;
	HASH_KEYS[195] = 8533893400235027350LL;
	HASH_KEYS[196] = 7030305639957378109LL;
	HASH_KEYS[197] = -5101788496870620911LL;
	HASH_KEYS[198] = 6771438412318952313LL;
	HASH_KEYS[199] = 4250681506479441444LL;
	HASH_KEYS[200] = -8191297536450487957LL;
	HASH_KEYS[201] = -3455490924106133542LL;
	HASH_KEYS[202] = -5669693530196601964LL;
	HASH_KEYS[203] = -4636203911500497445LL;
	HASH_KEYS[204] = 6534540868859169126LL;
	HASH_KEYS[205] = -1971557178876602575LL;
	HASH_KEYS[206] = -3962004141932916067LL;
	HASH_KEYS[207] = 3809144462974386062LL;
	HASH_KEYS[208] = 117481242756106019LL;
	HASH_KEYS[209] = 2311979346248094696LL;
	HASH_KEYS[210] = -1785240900216265478LL;
	HASH_KEYS[211] = 7853251472305921599LL;
	HASH_KEYS[212] = -7999764293516027013LL;
	HASH_KEYS[213] = -3611720877068440946LL;
	HASH_KEYS[214] = -1738821695932833878LL;
	HASH_KEYS[215] = -2370398858134327152LL;
	HASH_KEYS[216] = 3796451873879409752LL;
	HASH_KEYS[217] = 2921988186604837265LL;
	HASH_KEYS[218] = 1623809204430533644LL;
	HASH_KEYS[219] = -5150410113737828628LL;
	HASH_KEYS[220] = -4568533527248270039LL;
	HASH_KEYS[221] = 7289045792102847077LL;
	HASH_KEYS[222] = -6999509828185425041LL;
	HASH_KEYS[223] = -788893325380343578LL;
	HASH_KEYS[224] = -3932764938004787603LL;
	HASH_KEYS[225] = 2361178405875383390LL;
	HASH_KEYS[226] = -5120569186933888242LL;
	HASH_KEYS[227] = 7747597524690553652LL;
	HASH_KEYS[228] = 7360000415704225065LL;
	HASH_KEYS[229] = -2963539764495459132LL;
	HASH_KEYS[230] = 1859705235597815806LL;
	HASH_KEYS[231] = -1318086235298517719LL;
	HASH_KEYS[232] = 6331505454896034549LL;
	HASH_KEYS[233] = 510996574760069728LL;
	HASH_KEYS[234] = 3665049285881328165LL;
	HASH_KEYS[235] = -8613890019909423403LL;
	HASH_KEYS[236] = 6244715087368451518LL;
	HASH_KEYS[237] = 3194908100468508195LL;
	HASH_KEYS[238] = -1625033242586529494LL;
	HASH_KEYS[239] = -7323514254077497794LL;
	HASH_KEYS[240] = 2198760155154827021LL;
	HASH_KEYS[241] = -6980950365495157512LL;
	HASH_KEYS[242] = -6144746050329762075LL;
	HASH_KEYS[243] = 6707517442009660889LL;
	HASH_KEYS[244] = -8050410326411512694LL;
	HASH_KEYS[245] = 8093706381524201939LL;
	HASH_KEYS[246] = 1612034286169400450LL;
	HASH_KEYS[247] = -6996631789599915611LL;
	HASH_KEYS[248] = -3528780056422686935LL;
	HASH_KEYS[249] = -5083745349208453261LL;
	HASH_KEYS[250] = 7882886446586008791LL;
	HASH_KEYS[251] = 5176812869040649573LL;
	HASH_KEYS[252] = 3664574794694480696LL;
	HASH_KEYS[253] = 5396489919698444681LL;
	HASH_KEYS[254] = -9157164784979969756LL;
	HASH_KEYS[255] = -2361237989500298810LL;
	HASH_KEYS[256] = 3950007704068163496LL;
	HASH_KEYS[257] = -8613425321224079396LL;
	HASH_KEYS[258] = -8689171927127462778LL;
	HASH_KEYS[259] = -7190139634146997507LL;
	HASH_KEYS[260] = 3482396017029695153LL;
	HASH_KEYS[261] = -6338945908298519046LL;
	HASH_KEYS[262] = 400341727935643607LL;
	HASH_KEYS[263] = -7103173158639665779LL;
	HASH_KEYS[264] = -6705314951167067023LL;
	HASH_KEYS[265] = -1896858307405873438LL;
	HASH_KEYS[266] = 6513509086618139871LL;
	HASH_KEYS[267] = -5656268714961754951LL;
	HASH_KEYS[268] = -2011089874026434652LL;
	HASH_KEYS[269] = 2057715842900770279LL;
	HASH_KEYS[270] = 9023501048092005083LL;
	HASH_KEYS[271] = 2593359352743542824LL;
	HASH_KEYS[272] = 9144188156505776372LL;
	HASH_KEYS[273] = 798667041403336825LL;
	HASH_KEYS[274] = 1599243356457232831LL;
	HASH_KEYS[275] = 5151060270491324816LL;
	HASH_KEYS[276] = -2232779657397972334LL;
	HASH_KEYS[277] = -2534327220187578503LL;
	HASH_KEYS[278] = 5849668041650642470LL;
	HASH_KEYS[279] = -588857220363683313LL;
	HASH_KEYS[280] = 5600374601756948584LL;
	HASH_KEYS[281] = 7212482888937888267LL;
	HASH_KEYS[282] = 2110792859954750726LL;
	HASH_KEYS[283] = 902367287322230996LL;
	HASH_KEYS[284] = -1905956766707036338LL;
	HASH_KEYS[285] = 4088038814546566800LL;
	HASH_KEYS[286] = -448092850334787743LL;
	HASH_KEYS[287] = -5956795592566882318LL;
	HASH_KEYS[288] = -4830979358929461914LL;
	HASH_KEYS[289] = 4140549203356660874LL;
	HASH_KEYS[290] = -295952490710145441LL;
	HASH_KEYS[291] = -5724201479597770108LL;
	HASH_KEYS[292] = -8831407853698639506LL;
	HASH_KEYS[293] = -5731183007753191832LL;
	HASH_KEYS[294] = 6902163272766567916LL;
	HASH_KEYS[295] = -7193242962448737530LL;
	HASH_KEYS[296] = -5692436858843284837LL;
	HASH_KEYS[297] = 2002222090299304762LL;
	HASH_KEYS[298] = -5097836317105790374LL;
	HASH_KEYS[299] = 1192363907443447734LL;
	HASH_KEYS[300] = -5150034003472797604LL;
	HASH_KEYS[301] = -3055363567211122539LL;
	HASH_KEYS[302] = -5216462935531476383LL;
	HASH_KEYS[303] = -3658209732912791221LL;
	HASH_KEYS[304] = 5351231405539557835LL;
	HASH_KEYS[305] = 1155289930809502704LL;
	HASH_KEYS[306] = -1531056502414048250LL;
	HASH_KEYS[307] = -6501844636218050261LL;
	HASH_KEYS[308] = -7048837910668126619LL;
	HASH_KEYS[309] = -3750424163474100936LL;
	HASH_KEYS[310] = -8211580464165447807LL;
	HASH_KEYS[311] = 2176565211774438830LL;
	HASH_KEYS[312] = 193892498440922305LL;
	HASH_KEYS[313] = 2421158690344068946LL;
	HASH_KEYS[314] = 1165253992564941710LL;
	HASH_KEYS[315] = 331218271389532430LL;
	HASH_KEYS[316] = 7261687785571420807LL;
	HASH_KEYS[317] = 6753978533863869245LL;
	HASH_KEYS[318] = 6772643247157971259LL;
	HASH_KEYS[319] = 3918224938634704665LL;
	HASH_KEYS[320] = 2532990361938873529LL;
	HASH_KEYS[321] = 2120468966147393659LL;
	HASH_KEYS[322] = 6968483730285215456LL;
	HASH_KEYS[323] = 6207755648136071689LL;
	HASH_KEYS[324] = 7267760122459782812LL;
	HASH_KEYS[325] = 3924362090104633342LL;
	HASH_KEYS[326] = 2784205888953183520LL;
	HASH_KEYS[327] = -8644054434469820030LL;
	HASH_KEYS[328] = 8082288411495355002LL;
	HASH_KEYS[329] = -2764380738465125112LL;
	HASH_KEYS[330] = 5779947458761486779LL;
	HASH_KEYS[331] = 6367842191549685588LL;
	HASH_KEYS[332] = 2257594253718917211LL;
	HASH_KEYS[333] = 8663293076965994412LL;
	HASH_KEYS[334] = 1846894762042559206LL;
	HASH_KEYS[335] = 4186760652056944015LL;
	HASH_KEYS[336] = -8252568835065334136LL;
	HASH_KEYS[337] = -8270973521362051650LL;
	HASH_KEYS[338] = -5464712518215832359LL;
	HASH_KEYS[339] = -8069154232649649031LL;
	HASH_KEYS[340] = -1277661012611088116LL;
	HASH_KEYS[341] = -95357743189101325LL;
	HASH_KEYS[342] = -7443382036915782817LL;
	HASH_KEYS[343] = -767875950486777817LL;
	HASH_KEYS[344] = -5189230214301837016LL;
	HASH_KEYS[345] = -2648204999753579202LL;
	HASH_KEYS[346] = -8081921559889694883LL;
	HASH_KEYS[347] = -571880559557665230LL;
	HASH_KEYS[348] = -2892224343752266309LL;
	HASH_KEYS[349] = -4371733375637124787LL;
	HASH_KEYS[350] = -2716384213391630043LL;
	HASH_KEYS[351] = 3518771490874014496LL;
	HASH_KEYS[352] = 1553200436560179755LL;
	HASH_KEYS[353] = 6136564650244958530LL;
	HASH_KEYS[354] = -5817327949773849207LL;
	HASH_KEYS[355] = 1874850494295460181LL;
	HASH_KEYS[356] = -7859812188287793434LL;
	HASH_KEYS[357] = -672036404241767307LL;
	HASH_KEYS[358] = -6003210869971083137LL;
	HASH_KEYS[359] = -5114388707316601576LL;
	HASH_KEYS[360] = 8799514711611394238LL;
	HASH_KEYS[361] = 6307876399496815217LL;
	HASH_KEYS[362] = 6365611197306345876LL;
	HASH_KEYS[363] = -7302133096144079963LL;
	HASH_KEYS[364] = 7819249784644438229LL;
	HASH_KEYS[365] = -9112267726051005131LL;
	HASH_KEYS[366] = 7998144252220167995LL;
	HASH_KEYS[367] = -3674739091575791480LL;
	HASH_KEYS[368] = 2426700822439635981LL;
	HASH_KEYS[369] = -4254607976563649706LL;
	HASH_KEYS[370] = 8769622314427529983LL;
	HASH_KEYS[371] = -1823879529073843348LL;
	HASH_KEYS[372] = 6785821819741401916LL;
	HASH_KEYS[373] = 4776639775547420836LL;
	HASH_KEYS[374] = -591701069943024442LL;
	HASH_KEYS[375] = 5577718361355777431LL;
	HASH_KEYS[376] = -97667392636409205LL;
	HASH_KEYS[377] = 4781388376248344935LL;
	HASH_KEYS[378] = 6834568231092389636LL;
	HASH_KEYS[379] = -3835927319287504959LL;
	HASH_KEYS[380] = 4917087117365608875LL;
	HASH_KEYS[381] = -486335170614633793LL;
	HASH_KEYS[382] = 4653318830638879330LL;
	HASH_KEYS[383] = 1765420434548942905LL;
	HASH_KEYS[384] = -2591990372235801904LL;
	HASH_KEYS[385] = -5154167364320203479LL;
	HASH_KEYS[386] = -8002429918089623307LL;
	HASH_KEYS[387] = 7098599610192816141LL;
	HASH_KEYS[388] = -4339065837888347361LL;
	HASH_KEYS[389] = -577008427431858720LL;
	HASH_KEYS[390] = 5608188586651331259LL;
	HASH_KEYS[391] = -3536366728755574439LL;
	HASH_KEYS[392] = -5549958932751547778LL;
	HASH_KEYS[393] = 9063646804031752461LL;
	HASH_KEYS[394] = 3161856130797381874LL;
	HASH_KEYS[395] = 7629932951810010216LL;
	HASH_KEYS[396] = -8096409828538633609LL;
	HASH_KEYS[397] = 5105255630512044862LL;
	HASH_KEYS[398] = 7705616069183459859LL;
	HASH_KEYS[399] = 6672229822151505526LL;
	HASH_KEYS[400] = 3687630329544421129LL;
	HASH_KEYS[401] = 3207304101437600443LL;
	HASH_KEYS[402] = 1084391866653386067LL;
	HASH_KEYS[403] = -7170829669943605775LL;
	HASH_KEYS[404] = -2591722885387915831LL;
	HASH_KEYS[405] = 1459570483965762136LL;
	HASH_KEYS[406] = 8191346075557285516LL;
	HASH_KEYS[407] = 4704832709488136616LL;
	HASH_KEYS[408] = 2131507237353655754LL;
	HASH_KEYS[409] = 5198835677462678041LL;
	HASH_KEYS[410] = -1055415275593692731LL;
	HASH_KEYS[411] = 7945258426175729115LL;
	HASH_KEYS[412] = -1130343500367335585LL;
	HASH_KEYS[413] = 7015387170630558107LL;
	HASH_KEYS[414] = 8404975718728675215LL;
	HASH_KEYS[415] = -5449145536379455019LL;
	HASH_KEYS[416] = 3532739361082897252LL;
	HASH_KEYS[417] = 8505961478853577143LL;
	HASH_KEYS[418] = 9218476268363180014LL;
	HASH_KEYS[419] = -8563055744391547335LL;
	HASH_KEYS[420] = -1852299229510736808LL;
	HASH_KEYS[421] = -41389936914981368LL;
	HASH_KEYS[422] = -4313196071746042229LL;
	HASH_KEYS[423] = -2363062584482053556LL;
	HASH_KEYS[424] = 588803308198823030LL;
	HASH_KEYS[425] = -6016306370215264518LL;
	HASH_KEYS[426] = 5701355170354532006LL;
	HASH_KEYS[427] = -7265946686712495673LL;
	HASH_KEYS[428] = -4129990556889496029LL;
	HASH_KEYS[429] = 1264095501232589652LL;
	HASH_KEYS[430] = -1938837091308446102LL;
	HASH_KEYS[431] = -8525452914785588490LL;
	HASH_KEYS[432] = 7515189013650975882LL;
	HASH_KEYS[433] = -2596728675126050360LL;
	HASH_KEYS[434] = 370473495724196536LL;
	HASH_KEYS[435] = 2322717842004278210LL;
	HASH_KEYS[436] = 5918119706112217548LL;
	HASH_KEYS[437] = 7356696595480480613LL;
	HASH_KEYS[438] = 3557869760677032800LL;
	HASH_KEYS[439] = 3783647408256882807LL;
	HASH_KEYS[440] = -6885208031102691400LL;
	HASH_KEYS[441] = 3865540793722514636LL;
	HASH_KEYS[442] = 5043992645597460036LL;
	HASH_KEYS[443] = 8337460933001072719LL;
	HASH_KEYS[444] = 7375146585147506530LL;
	HASH_KEYS[445] = 1984430576762642307LL;
	HASH_KEYS[446] = -492116167192253533LL;
	HASH_KEYS[447] = -4860839141024396266LL;
	HASH_KEYS[448] = -5154711532641638136LL;
	HASH_KEYS[449] = -3219312089005796107LL;
	HASH_KEYS[450] = -8637095544541855332LL;
	HASH_KEYS[451] = 1481792055833453484LL;
	HASH_KEYS[452] = -8151036435914622137LL;
	HASH_KEYS[453] = -6631335320094646575LL;
	HASH_KEYS[454] = -7149900463104504013LL;
	HASH_KEYS[455] = -4403762733806330924LL;
	HASH_KEYS[456] = 5714246499247321955LL;
	HASH_KEYS[457] = -6660012305785306598LL;
	HASH_KEYS[458] = -7401379521736615511LL;
	HASH_KEYS[459] = -6330417429346677947LL;
	HASH_KEYS[460] = -1086797011597091564LL;
	HASH_KEYS[461] = -2668066436699558003LL;
	HASH_KEYS[462] = 3919254321495015178LL;
	HASH_KEYS[463] = 3825518704154290682LL;
	HASH_KEYS[464] = -6322541640243859086LL;
	HASH_KEYS[465] = -7055205237871630692LL;
	HASH_KEYS[466] = -4967314492808672750LL;
	HASH_KEYS[467] = -1818507041225046811LL;
	HASH_KEYS[468] = -2179305635407732762LL;
	HASH_KEYS[469] = -9133055594644537762LL;
	HASH_KEYS[470] = -8402709307577962920LL;
	HASH_KEYS[471] = -1077729471651095115LL;
	HASH_KEYS[472] = -8753298073422086874LL;
	HASH_KEYS[473] = -2051855728454609020LL;
	HASH_KEYS[474] = 6830668512206924682LL;
	HASH_KEYS[475] = -4776454754925990168LL;
	HASH_KEYS[476] = -7541351316261610729LL;
	HASH_KEYS[477] = 1266745375705347725LL;
	HASH_KEYS[478] = 2057743571307157944LL;
	HASH_KEYS[479] = 2307366792011864768LL;
	HASH_KEYS[480] = 1628171064868604021LL;
	HASH_KEYS[481] = 892385704947221641LL;
	HASH_KEYS[482] = -6338138686606992272LL;
	HASH_KEYS[483] = 1314495997336535163LL;
	HASH_KEYS[484] = 174343062149052661LL;
	HASH_KEYS[485] = 179787169425340930LL;
	HASH_KEYS[486] = 5535085086301503314LL;
	HASH_KEYS[487] = 316734590087117236LL;
	HASH_KEYS[488] = 4974236771521238170LL;
	HASH_KEYS[489] = 752814026009047061LL;
	HASH_KEYS[490] = 9034919941781895382LL;
	HASH_KEYS[491] = 3522999366895370520LL;
	HASH_KEYS[492] = -9095611506674028623LL;
	HASH_KEYS[493] = 2905842253144938924LL;
	HASH_KEYS[494] = 3884818506405009122LL;
	HASH_KEYS[495] = -7673046935170547873LL;
	HASH_KEYS[496] = -9074464758380139877LL;
	HASH_KEYS[497] = -6900734522292787531LL;
	HASH_KEYS[498] = -8148165134279011763LL;
	HASH_KEYS[499] = -7726634375560068811LL;
	HASH_KEYS[500] = 4473718394242332976LL;
	HASH_KEYS[501] = -8432035976667575145LL;
	HASH_KEYS[502] = 7105149649808362049LL;
	HASH_KEYS[503] = -1927954322688780964LL;
	HASH_KEYS[504] = -7956921146174237935LL;
	HASH_KEYS[505] = -7926042809316132805LL;
	HASH_KEYS[506] = 6483910239377172551LL;
	HASH_KEYS[507] = 5926544271684161952LL;
	HASH_KEYS[508] = -7069884718237824262LL;
	HASH_KEYS[509] = 2524531269260175456LL;
	HASH_KEYS[510] = 5029109706714082384LL;
	HASH_KEYS[511] = 7686307542034563558LL;
	HASH_KEYS[512] = -6421160300546519449LL;
	HASH_KEYS[513] = 6039107194915745445LL;
	HASH_KEYS[514] = -3344544488338825309LL;
	HASH_KEYS[515] = -8031661591444374478LL;
	HASH_KEYS[516] = -2226274487531450899LL;
	HASH_KEYS[517] = 1268249942140148190LL;
	HASH_KEYS[518] = 7361617914428404131LL;
	HASH_KEYS[519] = -1335570837815435279LL;
	HASH_KEYS[520] = -6055904960871150473LL;
	HASH_KEYS[521] = -7489188443386280214LL;
	HASH_KEYS[522] = -928750775708288265LL;
	HASH_KEYS[523] = -7081026457240372544LL;
	HASH_KEYS[524] = -7801863784727537890LL;
	HASH_KEYS[525] = 468304603627183044LL;
	HASH_KEYS[526] = 6180883158090623201LL;
	HASH_KEYS[527] = 7885447959932212988LL;
	HASH_KEYS[528] = 3701950119506289847LL;
	HASH_KEYS[529] = -4454435036557875159LL;
	HASH_KEYS[530] = 3365630497698583108LL;
	HASH_KEYS[531] = -3984259606419181456LL;
	HASH_KEYS[532] = -6409221875589996936LL;
	HASH_KEYS[533] = 2897358300939841539LL;
	HASH_KEYS[534] = -5969448530333388292LL;
	HASH_KEYS[535] = -9101529487141874909LL;
	HASH_KEYS[536] = 3800593356398153306LL;
	HASH_KEYS[537] = 6968865163168373933LL;
	HASH_KEYS[538] = 3092596699118275461LL;
	HASH_KEYS[539] = -3711990621296140176LL;
	HASH_KEYS[540] = 2381845253180864527LL;
	HASH_KEYS[541] = 8150350638775932678LL;
	HASH_KEYS[542] = 7014149737720690275LL;
	HASH_KEYS[543] = -3816035999225922257LL;
	HASH_KEYS[544] = -3249845784648132607LL;
	HASH_KEYS[545] = -968349281619523307LL;
	HASH_KEYS[546] = -8427860494861757944LL;
	HASH_KEYS[547] = 4069649262674454270LL;
	HASH_KEYS[548] = -1057076352648737891LL;
	HASH_KEYS[549] = -7673500943281289289LL;
	HASH_KEYS[550] = 7811894738163769718LL;
	HASH_KEYS[551] = 3818622936410813022LL;
	HASH_KEYS[552] = 827334736603586621LL;
	HASH_KEYS[553] = -2686282248308202339LL;
	HASH_KEYS[554] = 9202027419922796276LL;
	HASH_KEYS[555] = 7000880599601279120LL;
	HASH_KEYS[556] = -7265304894294258658LL;
	HASH_KEYS[557] = 2736050871117703047LL;
	HASH_KEYS[558] = -2305151901464471568LL;
	HASH_KEYS[559] = -2237220392258257718LL;
	HASH_KEYS[560] = -3504837972122743579LL;
	HASH_KEYS[561] = -8787864159639408403LL;
	HASH_KEYS[562] = 661952132621869273LL;
	HASH_KEYS[563] = 334537783153324685LL;
	HASH_KEYS[564] = -8918286463255777154LL;
	HASH_KEYS[565] = -4509662505372482864LL;
	HASH_KEYS[566] = 1824101795622858858LL;
	HASH_KEYS[567] = -5255065486477505793LL;
	HASH_KEYS[568] = -6671631003211709520LL;
	HASH_KEYS[569] = -1832645900472978696LL;
	HASH_KEYS[570] = -940694572921089005LL;
	HASH_KEYS[571] = -1246635654311238904LL;
	HASH_KEYS[572] = 6289138287467834163LL;
	HASH_KEYS[573] = -3853146417814502282LL;
	HASH_KEYS[574] = 7927013545391024884LL;
	HASH_KEYS[575] = 4054877443597297764LL;
	HASH_KEYS[576] = -8564236685391781401LL;
	HASH_KEYS[577] = -8657016658742023808LL;
	HASH_KEYS[578] = 3450189702568467048LL;
	HASH_KEYS[579] = -8672325048396812175LL;
	HASH_KEYS[580] = -8462144505741974951LL;
	HASH_KEYS[581] = -5481327661899862598LL;
	HASH_KEYS[582] = 5311951683223988566LL;
	HASH_KEYS[583] = -3680200742277925684LL;
	HASH_KEYS[584] = 1058405189338510491LL;
	HASH_KEYS[585] = 2300257963301668535LL;
	HASH_KEYS[586] = -2390361379347954767LL;
	HASH_KEYS[587] = -2760629999881291243LL;
	HASH_KEYS[588] = -5094745417946208394LL;
	HASH_KEYS[589] = 1634275216126054137LL;
	HASH_KEYS[590] = 6011067561673254601LL;
	HASH_KEYS[591] = -1462867775419360067LL;
	HASH_KEYS[592] = -8819638413675761694LL;
	HASH_KEYS[593] = -6908986903936899414LL;
	HASH_KEYS[594] = 6828542777933280463LL;
	HASH_KEYS[595] = -5177492514104424579LL;
	HASH_KEYS[596] = -6938712380089192390LL;
	HASH_KEYS[597] = 7386818093521288851LL;
	HASH_KEYS[598] = -3289938809854693268LL;
	HASH_KEYS[599] = 1445207508273910031LL;
	HASH_KEYS[600] = -9217225101058217938LL;
	HASH_KEYS[601] = -1610689802857133900LL;
	HASH_KEYS[602] = 8810275469371711662LL;
	HASH_KEYS[603] = 6061940838391218506LL;
	HASH_KEYS[604] = -6253325292980679377LL;
	HASH_KEYS[605] = 3992810634978693681LL;
	HASH_KEYS[606] = 3014046147125271767LL;
	HASH_KEYS[607] = -5192035831807639601LL;
	HASH_KEYS[608] = 6052142269547170095LL;
	HASH_KEYS[609] = 7570019038095885854LL;
	HASH_KEYS[610] = 5884566093750206426LL;
	HASH_KEYS[611] = 3031451226892511418LL;
	HASH_KEYS[612] = 2321775392907158307LL;
	HASH_KEYS[613] = 2878353728358929846LL;
	HASH_KEYS[614] = 7182609440744735781LL;
	HASH_KEYS[615] = -3449444063621510351LL;
	HASH_KEYS[616] = -8991396681765269928LL;
	HASH_KEYS[617] = 8955831193250958189LL;
	HASH_KEYS[618] = -3787046490765621236LL;
	HASH_KEYS[619] = -3188457863329984716LL;
	HASH_KEYS[620] = -5840556344493989283LL;
	HASH_KEYS[621] = 2587043087571321775LL;
	HASH_KEYS[622] = -2271422882862434237LL;
	HASH_KEYS[623] = -671222187173903777LL;
	HASH_KEYS[624] = -8577952916940408172LL;
	HASH_KEYS[625] = -6429279146250825879LL;
	HASH_KEYS[626] = -982268989106789072LL;
	HASH_KEYS[627] = -1573244303813619954LL;
	HASH_KEYS[628] = -9155834487221961103LL;
	HASH_KEYS[629] = 5960066791428128080LL;
	HASH_KEYS[630] = -1917636814518584092LL;
	HASH_KEYS[631] = -8994624899791349565LL;
	HASH_KEYS[632] = 3117247785614528657LL;
	HASH_KEYS[633] = 3968243481918983105LL;
	HASH_KEYS[634] = 5247607226671607904LL;
	HASH_KEYS[635] = 4816811149295420225LL;
	HASH_KEYS[636] = 8404228557006956292LL;
	HASH_KEYS[637] = 2677429832199750273LL;
	HASH_KEYS[638] = -2383412891117617462LL;
	HASH_KEYS[639] = 4897964316326420222LL;
	HASH_KEYS[640] = -8034454681170163022LL;
	HASH_KEYS[641] = -6809109340879981419LL;
	HASH_KEYS[642] = -5123061723947472688LL;
	HASH_KEYS[643] = -858990276200562821LL;
	HASH_KEYS[644] = 7821022394870513413LL;
	HASH_KEYS[645] = 1575775987635956140LL;
	HASH_KEYS[646] = 5779403480583638251LL;
	HASH_KEYS[647] = 1171800734048418170LL;
	HASH_KEYS[648] = -565775852343438713LL;
	HASH_KEYS[649] = -907680258953695131LL;
	HASH_KEYS[650] = -1679466407171986747LL;
	HASH_KEYS[651] = 3138062206569301951LL;
	HASH_KEYS[652] = -1187837177702558352LL;
	HASH_KEYS[653] = -3450062179471255565LL;
	HASH_KEYS[654] = -8777778429737775222LL;
	HASH_KEYS[655] = 1022410476343974393LL;
	HASH_KEYS[656] = -7607207002784531971LL;
	HASH_KEYS[657] = 7085360480539463960LL;
	HASH_KEYS[658] = -2715562619775883088LL;
	HASH_KEYS[659] = 208396376333912348LL;
	HASH_KEYS[660] = -1754264959702361048LL;
	HASH_KEYS[661] = 1182230715619064056LL;
	HASH_KEYS[662] = -4154452895923928919LL;
	HASH_KEYS[663] = -6291522284091672962LL;
	HASH_KEYS[664] = 3624933762701013461LL;
	HASH_KEYS[665] = -7287435558823458253LL;
	HASH_KEYS[666] = -2365485475888733649LL;
	HASH_KEYS[667] = 2137089346952843424LL;
	HASH_KEYS[668] = 8017408072729846159LL;
	HASH_KEYS[669] = 3624820834743367428LL;
	HASH_KEYS[670] = 5792868429587244020LL;
	HASH_KEYS[671] = -3425173131856215607LL;
	HASH_KEYS[672] = 4996077776219685640LL;
	HASH_KEYS[673] = 1329227344115149495LL;
	HASH_KEYS[674] = -8795420395406549884LL;
	HASH_KEYS[675] = 8949192936039793842LL;
	HASH_KEYS[676] = 4283937487009728234LL;
	HASH_KEYS[677] = 9160983552618918739LL;
	HASH_KEYS[678] = 3055215693415825016LL;
	HASH_KEYS[679] = -4348236068144078006LL;
	HASH_KEYS[680] = -226538747985213946LL;
	HASH_KEYS[681] = 4590831269109060529LL;
	HASH_KEYS[682] = -3836370598188344553LL;
	HASH_KEYS[683] = 1076699219765713916LL;
	HASH_KEYS[684] = -3183974265702401641LL;
	HASH_KEYS[685] = -1087846026570646190LL;
	HASH_KEYS[686] = -3534443644453204183LL;
	HASH_KEYS[687] = -5215022767100874980LL;
	HASH_KEYS[688] = 2021232521923533475LL;
	HASH_KEYS[689] = -4834293871620542127LL;
	HASH_KEYS[690] = 8156070624385865293LL;
	HASH_KEYS[691] = -5358235642878028437LL;
	HASH_KEYS[692] = 7401794026575129160LL;
	HASH_KEYS[693] = -4824985714555450618LL;
	HASH_KEYS[694] = -811258550923049295LL;
	HASH_KEYS[695] = -2272331908295008884LL;
	HASH_KEYS[696] = 6797521230257774795LL;
	HASH_KEYS[697] = -587065089672957488LL;
	HASH_KEYS[698] = 1105955046806320275LL;
	HASH_KEYS[699] = 8499218038228169068LL;
	HASH_KEYS[700] = -6217968705740159586LL;
	HASH_KEYS[701] = 1903602189379842493LL;
	HASH_KEYS[702] = 5128897155832528586LL;
	HASH_KEYS[703] = -1036527478268034014LL;
	HASH_KEYS[704] = 1845985225388748651LL;
	HASH_KEYS[705] = -5557352942601388402LL;
	HASH_KEYS[706] = -1021757165692468782LL;
	HASH_KEYS[707] = -3098593696321083299LL;
	HASH_KEYS[708] = -3064725769750320253LL;
	HASH_KEYS[709] = -3014372140565521261LL;
	HASH_KEYS[710] = 535262039769723794LL;
	HASH_KEYS[711] = -3229026751441449761LL;
	HASH_KEYS[712] = -6780376841695835LL;
	HASH_KEYS[713] = -5017153547934174423LL;
	HASH_KEYS[714] = 2288280111428773768LL;
	HASH_KEYS[715] = 6868450006923453267LL;
	HASH_KEYS[716] = -1373379688372983871LL;
	HASH_KEYS[717] = 7694085375529452980LL;
	HASH_KEYS[718] = -5946919558373082580LL;
	HASH_KEYS[719] = 7452954099287387583LL;
	HASH_KEYS[720] = -17720643294615090LL;
	HASH_KEYS[721] = -3289672591695948282LL;
	HASH_KEYS[722] = -1821615858110143264LL;
	HASH_KEYS[723] = 772728463171466271LL;
	HASH_KEYS[724] = -6989058493660730972LL;
	HASH_KEYS[725] = -1370196712855786012LL;
	HASH_KEYS[726] = 4655143259240184251LL;
	HASH_KEYS[727] = -2604208630964297134LL;
	HASH_KEYS[728] = -2084035382672043439LL;
	HASH_KEYS[729] = -35349322891456405LL;
	HASH_KEYS[730] = -738268009057958187LL;
	HASH_KEYS[731] = 4437351906217659759LL;
	HASH_KEYS[732] = -5599610512326640635LL;
	HASH_KEYS[733] = -4470670055784147860LL;
	HASH_KEYS[734] = -4687728989153074560LL;
	HASH_KEYS[735] = -6805018978503777771LL;
	HASH_KEYS[736] = 7768668195097848959LL;
	HASH_KEYS[737] = -1194767370302642737LL;
	HASH_KEYS[738] = -5824004501343854731LL;
	HASH_KEYS[739] = 7422375870262606356LL;
	HASH_KEYS[740] = 759825782839590354LL;
	HASH_KEYS[741] = 3621419942742372181LL;
	HASH_KEYS[742] = 821295007297513310LL;
	HASH_KEYS[743] = -4024022960504153705LL;
	HASH_KEYS[744] = -3608809198578815442LL;
	HASH_KEYS[745] = -2078491933922087794LL;
	HASH_KEYS[746] = 8689649533639638834LL;
	HASH_KEYS[747] = -5514327776548497982LL;
	HASH_KEYS[748] = -636735251920250666LL;
	HASH_KEYS[749] = -8279126700282666187LL;
	HASH_KEYS[750] = -461338352865021060LL;
	HASH_KEYS[751] = 1651514385395622354LL;
	HASH_KEYS[752] = 450285346660275262LL;
	HASH_KEYS[753] = 2308137823475631475LL;
	HASH_KEYS[754] = -1105616974742099127LL;
	HASH_KEYS[755] = 672499133823370475LL;
	HASH_KEYS[756] = 1893159869833149195LL;
	HASH_KEYS[757] = -5806599597001540833LL;
	HASH_KEYS[758] = 3410827551558821026LL;
	HASH_KEYS[759] = -2336050535174752165LL;
	HASH_KEYS[760] = -5925741250869912778LL;
	HASH_KEYS[761] = 710049655814657040LL;
	HASH_KEYS[762] = -7924213091872811150LL;
	HASH_KEYS[763] = -7440066048884282261LL;
	HASH_KEYS[764] = 6336494832721432043LL;
	HASH_KEYS[765] = -2150602827373485805LL;
	HASH_KEYS[766] = -5551765697908620770LL;
	HASH_KEYS[767] = -6465565512869202105LL;
	HASH_KEYS[768] = 619005638624933740LL;
	HASH_KEYS[769] = 3041430998998021780LL;
	HASH_KEYS[770] = 7716619313028668527LL;
	HASH_KEYS[771] = 7677470402051189211LL;
	HASH_KEYS[772] = -2524966243046625635LL;
	
	
	int turn, piece, pos, i = 0;
	for(turn = BLACK; turn <= WHITE; turn++)
	{
		for(piece = PAWN; piece <= KING; piece++)
		{
			for(pos = a8; pos <= h1; pos++)
			{
				HASH_KEY[turn][piece][pos] = HASH_KEYS[i++];
			}
		}
	}
	HASH_OO[BLACK] =  HASH_KEYS[i++];
	HASH_OO[WHITE] =  HASH_KEYS[i++];
	HASH_OOO[BLACK] = HASH_KEYS[i++];
	HASH_OOO[WHITE] = HASH_KEYS[i++];
	HASH_TURN = HASH_KEYS[i++];

}

////////////////////////////////////////////////////////////////////////////////
void ChessBoard::initMoveArrays()
{
	int pos, bits, tmp, bit;
	BITBOARD bbRank, bbFile, bb45, bb315, bbBoard; 
	
	for(pos = 0; pos < NUM_FIELDS; pos++)
	{
		//pos = 36;
		for(bits = 0; bits < 256; bits++)
		{
			//bits = 40;
			//co.pl(ChessBoard::bits8ToString(bits));
			//co.pl("============");
			bbRank = 0;
			bbFile=0;
			bb45=0;
			bb315=0;
			
			//RANKS
			bbBoard = ((BITBOARD)bits) << SHIFT_0[pos];
			
			tmp = pos;
			
			while(COL[tmp] > 0)
			{
				tmp--;
				if((BITS[tmp] & bbBoard) == 0)
					bbRank |= BITS[tmp];
				if((BITS[tmp] & bbBoard) == BITS[tmp])
				{
					bbRank |= BITS[tmp];
					break;
				}
			}
			
			tmp = pos;
			while(COL[tmp] < 7)
			{
				tmp++;
				if((BITS[tmp] & bbBoard) == 0)
					bbRank |= BITS[tmp];
				if((BITS[tmp] & bbBoard) == BITS[tmp])
				{
					bbRank |= BITS[tmp];
					break;
				}
			}
			RANK_MOVES[pos][bits] = bbRank;

			// FILES
			
			tmp = pos;
			bit = (int)pow(2, ROW[tmp]+1);
			
			while(ROW[tmp] < 7)
			{
				tmp+=8;
				//co.pl("ROW+" + ROW[tmp] + " r90 " + ROT_90_BITS[tmp]);
				//co.pl(bitbToString(ROT_90_BITS[tmp]));
				
				if((bits & bit) == bit)
				{
					bbFile |= BITS[tmp];
					break;
				}
				else
					bbFile |= BITS[tmp];
				
				bit*=2;
			}
			
			tmp = pos;
			bit = (int)pow(2, ROW[tmp]-1);
			while(ROW[tmp] > 0)
			{
				tmp-=8;
				//co.pl("ROW-" + ROW[tmp] + " - " + tmp + " - " + bit);
				//co.pl(bitbToString(ROT_90_BITS[tmp]));
				
				if((bits & bit) == bit)
				{
					bbFile |= BITS[tmp];
					break;
				}
				else
					bbFile |= BITS[tmp];
				
				bit/=2;
			}
			//co.pl("--");
			//co.pl(bitbToString(bbFile));
			FILE_MOVES[pos][bits] = bbFile;
			
			
			// 45
			tmp = pos;
			bit = (int)pow(2, MIN(ROW[tmp], 7-COL[tmp]) + 1);
			//co.pl("pos bit + 1 = " + bit);
			//co.pl(bitbToString(ROT_45_BITS[tmp]));
			
			while(COL[tmp] > 0 && ROW[tmp] < 7)
			{
				tmp+=7;
				//co.pl("bit+" + bit);
				
				if((bits & bit) == bit)
				{
					bb45 |= BITS[tmp];
					break;
				}
				else
					bb45 |= BITS[tmp];
				
				bit*=2;
			}
			
			tmp = pos;
			bit = (int)pow(2, MIN(ROW[tmp], 7-COL[tmp])-1);
			//co.pl("pos bit - 1 = " + bit);
			while(COL[tmp] < 7 && ROW[tmp] > 0)
			{
				tmp-=7;
				//co.pl("bit" + bit);
				
				if((bits & bit) == bit)
				{
					bb45 |= BITS[tmp];
					break;
				}
				else
					bb45 |= BITS[tmp];
				
				bit/=2;
			}
			
			//co.pl(bitbToString(bb45));
			DIAG_45_MOVES[pos][bits] = bb45;
			
			//315
			
			tmp = pos;
			bit = (int)pow(2, MIN(ROW[tmp], COL[tmp])+1);
			//co.pl(bit);
			//co.pl(bitbToString(ROT_45_BITS[tmp]));
			
			while(COL[tmp] < 7 && ROW[tmp] < 7)
			{
				tmp+=9;
				//co.pl("ROW+" + ROW[tmp] + " r90 " + ROT_90_BITS[tmp]);
				//co.pl(bitbToString(ROT_90_BITS[tmp]));
				
				if((bits & bit) == bit)
				{
					bb315 |= BITS[tmp];
					break;
				}
				else
					bb315 |= BITS[tmp];
				
				bit*=2;
			}
			
			tmp = pos;
			bit = (int)pow(2, MIN(ROW[tmp], COL[tmp])-1);
			while(COL[tmp] > 0 && ROW[tmp] > 0)
			{
				tmp-=9;
				//co.pl("ROW+" + ROW[tmp] + " r90 " + ROT_90_BITS[tmp]);
				//co.pl(bitbToString(ROT_90_BITS[tmp]));
				
				if((bits & bit) == bit)
				{
					bb315 |= BITS[tmp];
					break;
				}
				else
					bb315 |= BITS[tmp];
				
				bit/=2;
			}
			
			//co.pl(bitbToString(bb315));
			DIAG_315_MOVES[pos][bits] = bb315;
		}
	}
}

////////////////////////////////////////////////////////////////////////////////
void ChessBoard::initTrailingZeros()
{
	for(int i = 0; i < 65536; i++)
	{
		if((i & 0xFF) != 0)
			TRAILING_ZEROS_16_BITS[i] = TRAILING_ZEROS_8_BITS[i & 0xFF];
		else
			TRAILING_ZEROS_16_BITS[i] = TRAILING_ZEROS_8_BITS[i >> 8] + 8;
	}
}

////////////////////////////////////////////////////////////////////////////////
void ChessBoard::initPawnRange()
{
    int row, col; BITBOARD bb;
    for(int i = 0; i < 64; i++)
    {
        for(int t = 0; t < 2; t++)
        {
            row = ROW_TURN[t][i];
            bb = 0L;

            if(row < 7)
            {
                col = COL[i];
                if(col > 0)
                {
                    if(t == 0)
                        bb |= BITS[i+7];
                    else
                        bb |= BITS[i-9];
                }
                if(col < 7)
                {
                    if(t == 0)
                        bb |= BITS[i+9];
                    else
                        bb |= BITS[i-7];
                }

            }
            PAWN_RANGE[t][i] = bb;
        }
    }
}
void ChessBoard::initPassedPawnMask()
{
	// 
	int i, j;
	for (i = 55; i < 23; i--)
	{
		PASSED_PAWN_MASK[WHITE][i] |= BITS[i];
		j = 1;
		while(ROW[i-j*8] > 0)
		{
			PASSED_PAWN_MASK[WHITE][i] |= BITS[i-j*8];
			if(COL[i] > 1)
				PASSED_PAWN_MASK[WHITE][i] |= BITS[i-j*8-1];
			if(COL[i] < 7)
				PASSED_PAWN_MASK[WHITE][i] |= BITS[i-j*8+1];
			j++;
		}
	}
	for (i = 8; i < 48; i++)
	{
		PASSED_PAWN_MASK[BLACK][i] |= BITS[i];
		j = 1;
		while(ROW[i+j*8] < 7)
		{
			PASSED_PAWN_MASK[BLACK][i] |= BITS[i+j*8];
			if(COL[i] > 1)
				PASSED_PAWN_MASK[BLACK][i] |= BITS[i+j*8-1];
			if(COL[i] < 7)
				PASSED_PAWN_MASK[BLACK][i] |= BITS[i+j*8+1];
			j++;
		}
	}
}

////////////////////////////////////////////////////////////////////////////////
// some output functions
////////////////////////////////////////////////////////////////////////////////

////////////////////////////////////////////////////////////////////////////////
void ChessBoard::pieceToString(const int p, char* buf)
{
	switch(p)
	{
	case PAWN: strcpy(buf, ""); break;
	case KNIGHT: strcpy(buf, "N");break;
	case BISHOP: strcpy(buf, "B");break;
	case ROOK: strcpy(buf, "R");break;
	case QUEEN: strcpy(buf, "Q");break;
	case KING: strcpy(buf, "K");break;
	default: strcpy(buf, "@"); 
	}
}
////////////////////////////////////////////////////////////////////////////////
void ChessBoard::bitbToString(const BITBOARD bb, char* ret)
{
	strcpy(ret, "");
	BITBOARD bT = 1L; int i = 0;
	while(i < NUM_FIELDS)
	{
		if((bb & bT) == bT)
			strcat(ret, "1");
		else
			strcat(ret, "0");
		if(i % 8 == 7)
			strcat(ret, "\n");
		i++;
		bT <<= 1;
	}
}
////////////////////////////////////////////////////////////////////////////////
void ChessBoard::printB(char* s)
{
	char buf[1024];
	sprintf(s, "\n# %d. State %d qualities: %d, %d\nCastling %d %d\n\0", m_numBoard, m_state, m_quality, m_o_quality, m_castlings[m_turn], m_castlings[m_o_turn]);

	//s += "HashKey "+ "\n";
	//s += bitbToString(m_hashKey)+ "\n";
	/*
	strcat(s, "bitbPositions 0\n");
	bitbToString(m_bitbPositions[BLACK], buf);
	strcat(s, buf);
        strcat(s, "\nbitbPositions 1\n");
	bitbToString(m_bitbPositions[WHITE], buf);
	strcat(s, buf);
         */

        /*
	//s += "Pieces"+ "\n";
	//s += piecesToString()+ "\n";
	*/
	strcat(s, "\nBitb\n");
	bitbToString(m_bitb, buf);
	strcat(s, buf);
	/*
	strcat(s, "\nBitb45\n");
	bitbToString(m_bitb_45, buf);
	strcat(s, buf);
	strcat(s, "\nBitb_90\n");
	bitbToString(m_bitb_90, buf);
	strcat(s, buf);
	strcat(s, "\nBitb_315\n");
	bitbToString(m_bitb_315, buf);
	strcat(s, buf);
	*/
        //strcat(s, "\nBitb Attacksquares\n");
	//bitbToString(m_bitbAttackMoveSquares, buf);
	//strcat(s, buf);
           
	//strcat(s, "\nMoves:\n");
	//for(int i = 0; i < m_sizeMoves; i++){
	//	Move::toDbgString(m_arrMoves[i], buf);
//		strcat(s, buf);
//		strcat(s, ";");
//	}
	/*
	strcat(s, "\nHistory:\n");
	Move::toDbgString(m_myMove, buf);
	strcat(s, buf);
	
	ChessBoard *bP = m_parent;
	while(bP != NULL)
	{
		strcat(s, "\n");
		Move::toDbgString(bP->m_myMove, buf);
		strcat(s, buf);
		bP = bP->m_parent;
	}
	if(m_parent != NULL)
	{
		//s += ("Parent ep square: " + m_parent->m_ep + "\n");
		strcat(s, "\nParent moves:\n");
		
		for(int i = 0; i < m_parent->m_sizeMoves; i++){
			Move::toDbgString(m_parent->m_arrMoves[i], buf);
			strcat(s, buf);
			strcat(s, ";");
		}
			
		strcat(s, "\n");
	}
            */
}
