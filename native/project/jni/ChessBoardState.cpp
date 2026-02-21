#include "ChessBoard.h"

// calculate the state of the board
// generate all moves - filter the moves that lead to illegal board situations
// like (self check)
// when a move generates a board in which the opponent king is attacked, set
// the move to a "checking" move
void ChessBoard::calcState(ChessBoard* board) {
    if (isEnded()) {
        return;
    }

    genMoves();

    if (m_variant == VARIANT_DUCK) {
        // no need to calculate anything else, duck chess allows king capture
        return;
    }

    int move;
    m_indexMoves = 0;

    boolean isIncheck = this->isSquareAttacked(m_turn, m_kingPositions[m_turn]);
    if (isIncheck) {
        m_state = CHECK;
    }

    while (hasMoreMoves()) {
        move = getNextMove();

        // char buf[20];
        // Move::toDbgString(move, buf);
        // DEBUG_PRINT("%s\n", buf);

        makeMove(move, board);

        // check if king is attacked - since a move is done, this is in from m_o_turn
        if (board->isSquareAttacked(board->m_o_turn, board->m_kingPositions[board->m_o_turn])) {
            removeMoveElementAt();
        } else {
            // check if opponent king is checked
            if (board->isSquareAttacked(board->m_turn, board->m_kingPositions[board->m_turn])) {
                //  set checked flag in move - so in makeMove m_state can be set to check
                m_arrMoves[m_indexMoves - 1] = Move_setCheck(move);
            }
        }
    }

    // game over when no moves left
    if (m_sizeMoves == 0) {
        // state set to check in makeMove
        if (m_state == CHECK) {
            m_state = MATE;
        } else {
            m_state = STALEMATE;
        }
    }
}

// this is called from search, so check for king of turn
boolean ChessBoard::checkInCheck() {
    if (isSquareAttacked(m_turn, m_kingPositions[m_turn])) {
        m_state = CHECK;
        return true;
    }
    return false;
}

// this is called from search, so check for opponent king!
// only valid result if called after genmoves
boolean ChessBoard::checkInSelfCheck() {
    return isSquareAttacked(m_o_turn, m_kingPositions[m_o_turn]);
}

int ChessBoard::getState() {
    return m_state;
}

int ChessBoard::getVariant() {
    return m_variant;
}

void ChessBoard::setVariant(int variant) {
    m_variant = variant;
    if (variant == VARIANT_DEFAULT) {
        unsetDuckPos();
    }
}

boolean ChessBoard::isLegalPosition() {
    if ((m_bitbPieces[WHITE][PAWN] & (ROW_BITS[0] | ROW_BITS[7])) ||
        (m_bitbPieces[BLACK][PAWN] & (ROW_BITS[0] | ROW_BITS[7]))) {
        return false;
    }
    if (ChessBoard::bitCount(m_bitbPieces[WHITE][PAWN]) > 8 || ChessBoard::bitCount(m_bitbPieces[BLACK][PAWN]) > 8) {
        return false;
    }
    if (!areKingsOnTheBoard()) {
        return false;
    }
    if (isSquareAttacked(m_o_turn, m_kingPositions[m_o_turn])) {
        return false;
    }
    return true;
}

boolean ChessBoard::areKingsOnTheBoard() {
    // one of the kings not on the board, return just the default value
    if (m_kingPositions[m_turn] < 0 || m_kingPositions[m_turn] > 63 || m_kingPositions[m_o_turn] < 0 ||
        m_kingPositions[m_o_turn] > 63) {
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

// returns true when the game is ended
// TODO repeat check can be made more efficient with repeat index of hashkey
boolean ChessBoard::isEnded() {
    if (m_variant == VARIANT_DUCK) {
        // for duck chess, a captured king indicates end of game
        if (m_kingPositions[m_turn] < 0 || m_kingPositions[m_turn] > 63 || m_kingPositions[m_o_turn] < 0 ||
            m_kingPositions[m_o_turn] > 63) {
            m_state = MATE;
        }
    }
    if (m_state == MATE || m_state == STALEMATE) {
        return true;
    }
    // TODO skip this when m_state == CHECK?
    if (m_50RuleCount == 100) {
        m_state = DRAW_50;
        return true;
    }
    // check DRAW by material
    //  first check for no pawns, rooks and queens on either side.
    //  ASSUME: value of knight or bishop is never twice as big as the other
    if (m_bitbPieces[m_turn][PAWN] == 0 && m_bitbPieces[m_o_turn][PAWN] == 0 && m_bitbPieces[m_turn][ROOK] == 0 &&
        m_bitbPieces[m_o_turn][ROOK] == 0 && m_bitbPieces[m_turn][QUEEN] == 0 && m_bitbPieces[m_o_turn][QUEEN] == 0) {
        // check for min material
        if (m_qualities[m_o_turn] == 0 && m_qualities[m_turn] <= ChessBoard::MIN_MATERIAL_VALUE) {
            m_state = ChessBoard::DRAW_MATERIAL;
            return true;
        }
        if (m_qualities[m_turn] == 0 && m_qualities[m_o_turn] <= ChessBoard::MIN_MATERIAL_VALUE) {
            m_state = ChessBoard::DRAW_MATERIAL;
            return true;
        }
        // also KNkn and KBkb, KBkn are almost always draw; theoretical mates only with king in
        // corner (and own piece next to it)
        if (m_qualities[m_o_turn] <= ChessBoard::MIN_MATERIAL_VALUE &&
            m_qualities[m_turn] <= ChessBoard::MIN_MATERIAL_VALUE) {
            // test for either king NOT in a corner
            if (!(m_kingPositions[m_o_turn] == a8 || m_kingPositions[m_o_turn] == h8 ||
                  m_kingPositions[m_o_turn] == a1 || m_kingPositions[m_o_turn] == h1 || m_kingPositions[m_turn] == a8 ||
                  m_kingPositions[m_turn] == h8 || m_kingPositions[m_turn] == a1 || m_kingPositions[m_turn] == h1)) {
                m_state = ChessBoard::DRAW_MATERIAL;
                return true;
            }
        }
    }

    // DRAW by repetition, no need to check with noHitcount < 4, because repetition needs at least 4
    // sequential moves
    //  that can lead to the same position
    if (m_50RuleCount > 3 && m_parent != nullptr) {
        // start at parent
        ChessBoard* tmpBoard = m_parent;
        int repeatCount = 0;
        while (tmpBoard != nullptr) {
            if (tmpBoard->m_hashKey == m_hashKey) {
                repeatCount++;
            }
            if (repeatCount == 2) {
                m_state = ChessBoard::DRAW_REPEAT;
                return true;
            }
            // after hit or pawn move never the same
            if (tmpBoard->m_50RuleCount == 0) {
                break;
            }

            tmpBoard = tmpBoard->m_parent;
        }
    }
    return false;
}

// called from search, a little different than isEnded (no MATE and STALEMATE)
boolean ChessBoard::checkEnded() {
    if (m_50RuleCount == 100) {
        m_state = DRAW_50;
        return true;
    }
    if (m_50RuleCount > 3 && m_parent != nullptr) {
        // start at parent
        ChessBoard* tmpBoard = m_parent->m_parent;
        int repeatCount = 0;
        while (tmpBoard != nullptr) {
            if (tmpBoard->m_hashKey == m_hashKey) {
                repeatCount++;
            }
            if (repeatCount == 2) {
                m_state = ChessBoard::DRAW_REPEAT;
                return true;
            }
            // after hit or pawn move never the same
            if (tmpBoard->m_50RuleCount == 0) {
                break;
            }
            tmpBoard = tmpBoard->m_parent;
        }
    }
    return false;
}
