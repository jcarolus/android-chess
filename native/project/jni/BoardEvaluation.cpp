#include "ChessBoard.h"

// boardValue will call this method when one of the players only has its king
// on the board and no pawns
int ChessBoard::loneKingValue(const int turn) {
    if (turn == m_turn) {
        return ChessBoard::VALUATION_LONE_KING_BONUS -
               ChessBoard::VALUATION_LONE_KING * HOOK_DISTANCE[m_kingPositions[m_turn]][m_kingPositions[m_o_turn]] -
               ChessBoard::VALUATION_KING_ENDINGS[m_kingPositions[m_o_turn]] + m_qualities[m_turn];
    }
    return ChessBoard::VALUATION_LONE_KING_BONUS -
           ChessBoard::VALUATION_LONE_KING * HOOK_DISTANCE[m_kingPositions[m_o_turn]][m_kingPositions[m_turn]] -
           ChessBoard::VALUATION_KING_ENDINGS[m_kingPositions[m_turn]] + m_qualities[m_o_turn];
}

// return "value" of king bishop knight against lone king
int ChessBoard::kbnkValue(const int turn) {
    int winnerKingPos, loserKingPos, value = 0;
    if (m_turn == turn) {
        winnerKingPos = m_kingPositions[m_turn];
        loserKingPos = m_kingPositions[m_o_turn];
        if ((m_bitbPieces[m_turn][KING] & ChessBoard::CENTER_4x4_SQUARES) != 0) {
            value = 20;
        }
    } else {
        winnerKingPos = m_kingPositions[m_o_turn];
        loserKingPos = m_kingPositions[m_turn];
        if ((m_bitbPieces[m_o_turn][KING] & ChessBoard::CENTER_4x4_SQUARES) != 0) {
            value = 20;
        }
    }
    value += (300 - 6 * HOOK_DISTANCE[winnerKingPos][loserKingPos]);
    value -= ((m_bitbPieces[turn][BISHOP] & WHITE_SQUARES) == 0) ? ChessBoard::VALUATION_KBNK_SCORE[0][loserKingPos]
                                                                 : ChessBoard::VALUATION_KBNK_SCORE[1][loserKingPos];
    value -= ChessBoard::VALUATION_KING_ENDINGS[loserKingPos];
    value -= HOOK_DISTANCE[trailingZeros(m_bitbPieces[turn][BISHOP])][loserKingPos];
    value -= HOOK_DISTANCE[trailingZeros(m_bitbPieces[turn][KNIGHT])][loserKingPos];

    return value;
}

// evaluation to promote promoting...
int ChessBoard::promotePawns(const int turn) {
    int value = 0;
    BITBOARD bb = m_bitbPieces[turn][PAWN];
    int pos;
    if (m_turn == turn) {
        value += (15 - HOOK_DISTANCE[m_kingPositions[m_turn]][m_kingPositions[m_o_turn]]);
        value += ROW_TURN[turn][m_kingPositions[m_turn]];
    } else {
        value += (15 - HOOK_DISTANCE[m_kingPositions[m_turn]][m_kingPositions[m_o_turn]]);
        value += ROW_TURN[turn][m_kingPositions[m_o_turn]];
    }

    while (bb != 0) {
        pos = ChessBoard::trailingZeros(bb);
        bb &= NOT_BITS[pos];
        value += 10 * ROW_TURN[turn][pos];
    }
    return value;
}

// Extended evaluation function
int ChessBoard::boardValue() {
    // one of the kings not on the board

    if (m_kingPositions[m_turn] < 0) {
        return -VALUATION_MATE;
    }

    if (m_kingPositions[m_o_turn] < 0) {
        return VALUATION_MATE;
    }

    // standard basic evaluation. sum of material quality
    int val = m_qualities[m_turn] - m_qualities[m_o_turn];

    // invalid state
    if (m_kingPositions[m_turn] > 63 || m_kingPositions[m_o_turn] > 63) {
        return val;
    }

    // lone king
    if (m_qualities[m_turn] == 0) {
        // no pawns to promote but enough mating material (m_state != DRAW_MATERIAL)
        if (m_bitbPieces[m_o_turn][PAWN] == 0) {
            // kbnk is special case
            if ((m_qualities[m_o_turn] == ChessBoard::PIECE_VALUES[KNIGHT] + ChessBoard::PIECE_VALUES[BISHOP]) &&
                ChessBoard::bitCount(m_bitbPieces[m_o_turn][KNIGHT]) == 1 &&
                ChessBoard::bitCount(m_bitbPieces[m_o_turn][BISHOP]) == 1) {
                return -kbnkValue(m_o_turn);
            }
            return -loneKingValue(m_o_turn);
        }
        // promote pawns
        return -promotePawns(m_o_turn);
    }  // opponent has lone king
    else if (m_qualities[m_o_turn] == 0) {
        if (m_bitbPieces[m_turn][PAWN] == 0) {
            if ((m_qualities[m_turn] == ChessBoard::PIECE_VALUES[KNIGHT] + ChessBoard::PIECE_VALUES[BISHOP]) &&
                ChessBoard::bitCount(m_bitbPieces[m_turn][KNIGHT]) == 1 &&
                ChessBoard::bitCount(m_bitbPieces[m_turn][BISHOP]) == 1) {
                return kbnkValue(m_turn);
            }
            return loneKingValue(m_turn);
        }
        return promotePawns(m_turn);
    }
    // TODO some more known end-game evaluations (kqkq, kqkr, krkn, krkb...)

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

// penalty for early queen move
int ChessBoard::queenValueExtension(const int turn) {
    BITBOARD bbPiece = m_bitbPieces[turn][QUEEN];
    if (bbPiece != 0) {
        // TODO this assumes a default game and other pieces
        if (m_numBoard < 11) {
            const BITBOARD bbRows = ROW_BITS[ROW_TURN[turn][0]];
            if ((bbRows & bbPiece) == 0) {  // Queen not on first row
                return VALUATION_EARLY_QUEEN;
            }
        } else {
            int iPos;
            int val = 0;
            while (bbPiece != 0) {
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

int ChessBoard::kingValueExtension(const int turn) {
    int val;
    if (m_castlings[turn] & MASK_CASTLED) {
        val = VALUATION_CASTLED;
    } else {
        if (m_castlings[turn] == 0) {
            val = VALUATION_CASTLING_POSSIBLE;
        } else {
            val = VALUATION_CASTLING_NOT_POSSIBLE;
        }
    }
    // m_bitbPieces[turn][KING]
    if (turn == m_turn) {
        return (ChessBoard::bitCount(KING_RANGE[m_kingPositions[m_turn]] & m_bitbPieces[turn][PAWN]) *
                VALUATION_PAWN_IN_KING_RANGE) +
               val;
    }
    return (ChessBoard::bitCount(KING_RANGE[m_kingPositions[m_o_turn]] & m_bitbPieces[turn][PAWN]) *
            VALUATION_PAWN_IN_KING_RANGE) +
           val;
}

int ChessBoard::knightValueExtension(const int turn) {
    BITBOARD bbPiece = m_bitbPieces[turn][KNIGHT];
    int iPos;
    int val = ChessBoard::bitCount(m_bitbPieces[turn][KNIGHT] & CENTER_4x4_SQUARES) * VALUATION_KNIGHT_CENTER;
    while (bbPiece != 0) {
        iPos = ChessBoard::trailingZeros(bbPiece);
        bbPiece &= NOT_BITS[iPos];

        val += ChessBoard::bitCount(knightMoves(turn, iPos)) * VALUATION_KNIGHT_MOBILITY;
    }
    return val;
}

int ChessBoard::rookValueExtension(const int turn) {
    const BITBOARD bbRooks = m_bitbPieces[turn][ROOK];
    BITBOARD bbPiece = bbRooks;
    int iPos;
    int val = 0, col = -1, row = -1;

    while (bbPiece != 0) {
        iPos = ChessBoard::trailingZeros(bbPiece);
        bbPiece &= NOT_BITS[iPos];

        val += ChessBoard::bitCount(rookMoves(turn, iPos)) * VALUATION_ROOK_MOBILITY;

        if (row == -1) {
            row = Pos::row(iPos);
            col = Pos::col(iPos);
        } else {
            if (row == Pos::row(iPos)) {
                val += VALUATION_ROOK_SAME_ROW_FILE;
            }
            if (col == Pos::col(iPos)) {
                val += VALUATION_ROOK_SAME_ROW_FILE;
            }

            // CONNECTED
            if (((~m_bitbPositions[turn]) | (bbRooks & NOT_BITS[iPos])) &
                ((RANK_MOVES[iPos][(int) (m_bitb >> SHIFT_0[iPos]) & 0xFF]) |
                 (FILE_MOVES[iPos][(int) (m_bitb_90 >> SHIFT_90[iPos]) & 0xFF]))) {
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
int ChessBoard::bishopValueExtension(const int turn) {
    BITBOARD bbPiece = m_bitbPieces[turn][BISHOP];
    int iPos;
    int val = 0;
    if (bbPiece > 0) {
        if (ChessBoard::bitCount(bbPiece) == 1) {
            iPos = ChessBoard::trailingZeros(bbPiece);

            val += ChessBoard::bitCount(bishopAttacks(turn, iPos)) * 5;
            if (iPos % 2 == 0) {
                val -= ChessBoard::bitCount(WHITE_SQUARES & m_bitbPieces[turn][PAWN]);
            } else {
                val -= ChessBoard::bitCount(BLACK_SQUARES & m_bitbPieces[turn][PAWN]);
            }
        } else {
            val += VALUATION_BISHOP_PAIR;  // bonus for pair or more bishops
            while (bbPiece != 0) {
                iPos = ChessBoard::trailingZeros(bbPiece);
                bbPiece &= NOT_BITS[iPos];

                val += ChessBoard::bitCount(bishopMoves(turn, iPos)) * VALUATION_BISHOP_MOBILITY;
            }
        }
    }
    return val;
}

int ChessBoard::pawnValueExtension(const int turn) {
    int val = 0;
    const BITBOARD bbPawn = m_bitbPieces[turn][PAWN];
    const BITBOARD bbPawnOpp = m_bitbPieces[turn ^ 1][PAWN];
    BITBOARD bbPiece = bbPawn;

    if (turn == WHITE) {
        if (bbPawn & (D2 | E2)) {
            val += VALUATION_PAWN_CENTRE_FIRST_ROW;
        }

    } else {
        if (bbPawn & (D7 | E7)) {
            val += VALUATION_PAWN_CENTRE_FIRST_ROW;
        }
    }

    int iPos;
    while (bbPiece != 0) {
        iPos = ChessBoard::trailingZeros(bbPiece);
        bbPiece &= NOT_BITS[iPos];

        val += ROW_TURN[turn][iPos] * VALUATION_PAWN_ROW;  // advance pawn

        if (PASSED_PAWN_MASK[turn][iPos] & bbPawn) {
            val += VALUATION_PAWN_FILE_NEIGHBOUR;  // neighbours on files
        }

        if (PAWN_RANGE[turn][iPos] & bbPawn) {
            val += VALUATION_PAWN_CONNECTED;  // covering neighbours
        }

        if (FILE_BITS[COL[iPos]] & (bbPawn & NOT_BITS[iPos])) {
            val += VALUATION_PAWN_DOUBLED;  // doubled pawn
        }

        if ((PASSED_PAWN_MASK[turn][iPos] & bbPawnOpp) == 0) {  // passed pawn
            val += VALUATION_PAWN_PASSED;
        }
    }
    return val;
}
