#include "Game.h"

void *Game::search_wrapper(void *arg) {
    Game::getInstance()->search();
    return 0;
}

#pragma region Search methods

void Game::setQuiescentOn(boolean on) {
    if (m_bSearching.load()) {
        return;
    }
    m_quiescentSearchOn = on;
}

void Game::setSearchTime(int secs) {
    if (m_bSearching.load()) {
        return;
    }
    m_milliesGiven = (long) secs;
    m_searchLimit = 0;
}

void Game::setSearchLimit(int depth) {
    if (m_bSearching.load()) {
        return;
    }
    m_milliesGiven = 0;
    if (depth <= MAX_DEPTH - QUIESCE_DEPTH) {
        m_searchLimit = depth;
    } else {
        m_searchLimit = MAX_DEPTH - QUIESCE_DEPTH;
    }
}

void Game::search() {
    bool expected = false;
    if (!m_bSearching.compare_exchange_strong(expected, true)) {
        DEBUG_PRINT("Already searching!");
        return;
    }
    struct SearchGuard {
        std::atomic_bool *flag;
        ~SearchGuard() { flag->store(false); }
    } searchGuard{&m_bSearching};

    m_bestMoveAndValue = (MoveAndValue){.value = 0, .move = 0, .duckMove = -1};
    m_bInterrupted = false;
    m_evalCount = 0;

    ChessBoard *board = getBoard();
    board->calcState(m_searchWorkspace.refurbish());

    if (board->isEnded()) {
        return;
    }

    if (board->getNumMoves() == 0) {
        DEBUG_PRINT("NO moves!");
        return;
    }

    int variant = board->getVariant();
    m_evalCount++;

    startTime();

    char moveBuf[20], duckMoveBuf[4];
    for (int i = 0; i < MAX_DEPTH; i++) {
        m_arrBestMoves[i] = (MoveAndValue){.value = 0, .move = 0, .duckMove = -1};
    }

    ChessBoard *searchBoard = m_searchWorkspace.boardAt(0);
    board->duplicate(searchBoard);
    if (m_milliesGiven > 0) {
        DEBUG_PRINT("Search with millies given %ld", m_milliesGiven);

        for (m_searchDepth = 1; m_searchDepth < (MAX_DEPTH - QUIESCE_DEPTH); m_searchDepth++) {
            if (variant == ChessBoard::VARIANT_DEFAULT) {
                alphaBeta(searchBoard, m_searchDepth, -ChessBoard::VALUATION_MATE, ChessBoard::VALUATION_MATE);
            } else {
                alphaBetaDuck(searchBoard, m_searchDepth, -ChessBoard::VALUATION_MATE, ChessBoard::VALUATION_MATE);
            }

            DEBUG_PRINT("Searched at depth %d - value: %d - move: %d - num moves: %d\n",
                        m_searchDepth,
                        m_arrBestMoves[0].value,
                        getBestMoveAt(m_searchDepth),
                        board->getNumMoves());

            if (m_bInterrupted) {
                break;
            }

            m_bestMoveAndValue = m_arrBestMoves[0];
            if (m_bestMoveAndValue.value == ChessBoard::VALUATION_MATE) {
                for (int i = 0; i < MAX_DEPTH; i++) {
                    int moveAt = getBestMoveAt(i);
                    if (moveAt != 0) {
                        Move::toDbgString(moveAt, moveBuf);
                        DEBUG_PRINT("%s,", moveBuf);
                    }
                }
                DEBUG_PRINT("Found checkmate, stopping search\n");
                break;
            }

            if (usedTime()) {
                DEBUG_PRINT("Time over 50 pct - no further deepening\n");
                break;
            }
        }
    } else {
        DEBUG_PRINT("Search with limit given: %d\n", m_searchLimit);
        m_searchDepth = m_searchLimit;
        if (variant == ChessBoard::VARIANT_DEFAULT) {
            alphaBeta(searchBoard, m_searchDepth, -ChessBoard::VALUATION_MATE, ChessBoard::VALUATION_MATE);
        } else {
            alphaBetaDuck(searchBoard, m_searchDepth, -ChessBoard::VALUATION_MATE, ChessBoard::VALUATION_MATE);
        }
        m_bestMoveAndValue = m_arrBestMoves[0];
    }

    if (m_bInterrupted) {
        DEBUG_PRINT("Interrupted search\n");
    }

    Move::toDbgString(m_bestMoveAndValue.move, moveBuf);
    Pos::toString(m_bestMoveAndValue.duckMove, duckMoveBuf);
    DEBUG_PRINT("\n=====\nSearch\nvalue\t%d\nevalCnt\t%d\nMove\t%s\nDuck\t%s\ndepth\t%d\nTime\t%ld ms\nNps\t%.2f\n",
                m_bestMoveAndValue.value,
                m_evalCount,
                moveBuf,
                duckMoveBuf,
                m_searchDepth,
                timePassed(),
                (double) m_evalCount / timePassed());
}

int Game::alphaBeta(ChessBoard *board, const int depth, int alpha, const int beta) {
    if (m_evalCount % 1000 == 0) {
        if (timeUp()) {
            m_bInterrupted = true;
        }
    }
    if (m_bInterrupted || depth >= MAX_DEPTH) {
        return alpha;
    }

    if (board->checkEnded()) {
        return ChessBoard::VALUATION_DRAW;
    }

    board->scoreMovesPV(m_arrBestMoves[m_searchDepth - depth].move);

    MoveAndValue best = {.value = (-ChessBoard::VALUATION_MATE) - 1, .move = 0, .duckMove = -1};
    MoveAndValue current;
    ChessBoard *nextBoard = m_searchWorkspace.boardAt(depth);

    while (board->hasMoreMoves()) {
        current = (MoveAndValue){.value = 0, .move = board->getNextScoredMove(), .duckMove = -1};
        board->makeMove(current.move, nextBoard);

        if (nextBoard->checkInSelfCheck()) {
            board->removeMoveElementAt();
            continue;
        }

        nextBoard->genMoves();
        if (nextBoard->checkInCheck()) {
            nextBoard->setMyMoveCheck();
            current.move = Move_setCheck(current.move);
        }

        if (depth == 1) {
            current.value = -quiesce(nextBoard, QUIESCE_DEPTH, -beta, -alpha);
        } else {
            current.value = -alphaBeta(nextBoard, depth - 1, -beta, -alpha);
        }

        if (current.value > best.value && !m_bInterrupted) {
            best = current;
            m_arrBestMoves[m_searchDepth - depth] = current;
        }

        if (best.value > alpha) {
            alpha = best.value;
        }
        if (best.value >= beta || m_bInterrupted) {
            break;
        }
    }

    if (board->getNumMoves() == 0) {
        m_evalCount++;
        if (Move_isCheck(board->getMyMove())) {
            return -ChessBoard::VALUATION_MATE;
        }
        return ChessBoard::VALUATION_DRAW;
    }

    return best.value;
}

int Game::quiesce(ChessBoard *board, const int depth, int alpha, const int beta) {
    if (m_evalCount % 1000 == 0) {
        if (timeUp()) {
            m_bInterrupted = true;
        }
    }

    if (m_bInterrupted || depth >= MAX_DEPTH) {
        return alpha;
    }

    m_evalCount++;
    const int boardValue = board->boardValue();

    if (!Move_isCheck(board->getMyMove()) && boardValue >= beta) {
        return beta;
    }

    if (depth == 0 || !m_quiescentSearchOn) {
        return boardValue;
    }

    if (boardValue >= beta) {
        return beta;
    }

    if (boardValue > alpha) {
        alpha = boardValue;
    }

    ChessBoard *nextBoard = m_searchWorkspace.boardAt(MAX_DEPTH - depth);
    board->scoreMoves();
    while (board->hasMoreMoves()) {
        int move = board->getNextScoredMove();
        board->makeMove(move, nextBoard);

        if (nextBoard->checkInSelfCheck()) {
            board->removeMoveElementAt();
            continue;
        }

        if (nextBoard->checkInCheck()) {
            nextBoard->setMyMoveCheck();
            move = Move_setCheck(move);
        }

        if (Move_isHIT(move) || Move_isCheck(move) || Move_isPromotionMove(move)) {
            nextBoard->genMoves();
            int value = -quiesce(nextBoard, depth - 1, -beta, -alpha);
            if (value > alpha) {
                alpha = value;
                if (value >= beta) {
                    return beta;
                }
            }
        }
    }

    if (board->getNumMoves() == 0) {
        if (Move_isCheck(board->getMyMove())) {
            return -ChessBoard::VALUATION_MATE;
        }
        return ChessBoard::VALUATION_DRAW;
    }

    return alpha;
}

int Game::alphaBetaDuck(ChessBoard *board, const int depth, int alpha, const int beta) {
    if (m_evalCount % 1000 == 0) {
        if (timeUp()) {
            m_bInterrupted = true;
        }
    }
    if (m_bInterrupted || depth >= MAX_DEPTH) {
        return alpha;
    }

    if (board->checkEnded()) {
        if (board->getState() == ChessBoard::MATE) {
            m_evalCount++;
            return board->boardValue();
        }
        return ChessBoard::VALUATION_DRAW;
    }

    if (depth == 0) {
        m_evalCount++;
        return board->boardValue();
    }

    board->scoreMovesPV(m_arrBestMoves[m_searchDepth - depth].move);

    MoveAndValue best = {.value = (-ChessBoard::VALUATION_MATE) - 1, .move = 0, .duckMove = -1};
    ChessBoard *nextBoard = m_searchWorkspace.boardAt(depth);

    while (board->hasMoreMoves()) {
        MoveAndValue current = {.value = 0, .move = board->getNextScoredMove(), .duckMove = -1};
        board->makeMove(current.move, nextBoard);
        nextBoard->genMoves();

        if (nextBoard->areKingsOnTheBoard()) {
            MoveAndValue bestDuck = {.value = (-ChessBoard::VALUATION_MATE) - 1, .move = 0, .duckMove = -1};
            int numMoves = nextBoard->getNumMoves();
            ChessBoard *duckBoard = new ChessBoard();
            for (int i = 0; i < numMoves; i++) {
                int tmpDuckMove = nextBoard->getMoveAt(i);
                if (Move_isHIT(tmpDuckMove)) {
                    continue;
                }

                MoveAndValue currentDuck = {.value = 0, .move = 0, .duckMove = Move_getTo(tmpDuckMove)};
                nextBoard->duplicate(duckBoard);
                if (!duckBoard->requestDuckMove(currentDuck.duckMove)) {
                    DEBUG_PRINT("Could not make duckMove %d, %d\n", currentDuck.duckMove, Move_getFrom(tmpDuckMove));
                    continue;
                }

                duckBoard->calcState(m_searchWorkspace.refurbish());
                duckBoard->getMoves();
                currentDuck.value = -alphaBetaDuck(duckBoard, depth - 1, -beta, -alpha);

                if (currentDuck.value > bestDuck.value) {
                    bestDuck = currentDuck;
                }
                if (bestDuck.value > alpha) {
                    alpha = bestDuck.value;
                }
                if (bestDuck.value >= beta || m_bInterrupted) {
                    break;
                }
            }
            delete duckBoard;
            current.value = bestDuck.value;
            current.duckMove = bestDuck.duckMove;
        } else {
            current.value = -nextBoard->boardValue();
        }

        if (current.value > best.value && !m_bInterrupted) {
            best = current;
            m_arrBestMoves[m_searchDepth - depth] = current;
        }

        if (best.value > alpha) {
            alpha = best.value;
        }
        if (best.value >= beta || m_bInterrupted) {
            break;
        }
    }

    if (board->getNumMoves() == 0) {
        m_evalCount++;
        return -ChessBoard::VALUATION_MATE;
    }

    return best.value;
}

#pragma endregion

#pragma region Search timing functions

void Game::startTime() {
    timeval time;
    gettimeofday(&time, nullptr);
    m_millies = (time.tv_sec * 1000) + (time.tv_usec / 1000);
}

boolean Game::timeUp() {
    if (m_milliesGiven == 0) {
        return false;
    }
    timeval time;
    gettimeofday(&time, nullptr);
    return m_milliesGiven < ((time.tv_sec * 1000) + (time.tv_usec / 1000) - m_millies);
}

boolean Game::usedTime() {
    timeval time;
    gettimeofday(&time, nullptr);
    return (m_milliesGiven / 3) < ((time.tv_sec * 1000) + (time.tv_usec / 1000) - m_millies);
}

long Game::timePassed() {
    timeval time;
    gettimeofday(&time, nullptr);
    return ((time.tv_sec * 1000) + (time.tv_usec / 1000) - m_millies);
}

#pragma endregion
