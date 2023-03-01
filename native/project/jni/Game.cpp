#include "Game.h"

int Game::DB_SIZE = 0;
FILE *Game::DB_FP = NULL;
int Game::DB_DEPTH = 5;
Game *Game::game = NULL;

Game::Game(void) {
    m_board = new ChessBoard();
    m_promotionPiece = ChessBoard::QUEEN;
    for (int i = 0; i < MAX_DEPTH; i++) {
        m_boardFactory[i] = new ChessBoard();
    }
    m_boardRefurbish = new ChessBoard();

    DB_SIZE = 0;

    m_bSearching = false;
    reset();
}

Game::~Game(void) {
    if (DB_FP) {
        fclose(DB_FP);
        DB_FP = NULL;
    }
    DB_SIZE = 0;

    // clean up history
    ChessBoard *cb, *tmp;
    while ((cb = m_board->undoMove()) != NULL) {
        tmp = m_board;
        m_board = cb;
        delete tmp;
    }

    delete m_board;

    delete m_boardRefurbish;

    // somehow boardFactory seems to cause segmentation
    //    for (int i = 0; i < MAX_DEPTH; i++) {
    //        delete m_boardFactory[i];
    //    }
}

// the non thread safe solution; assumption is that getInsance is called before any threads are created
Game *Game::getInstance() {
    if (Game::game == NULL) {
        ChessBoard::initStatics();
        Game::game = new Game();
    }
    return Game::game;
}

void Game::deleteInstance() {
    if (Game::game != NULL) {
        delete Game::game;
        Game::game = NULL;
    }
}

void *Game::search_wrapper(void *arg) {
    Game::getInstance()->search();
    return 0;
}

void Game::reset() {
    // clean up history
    ChessBoard *cb, *tmp;
    while ((cb = m_board->undoMove()) != NULL) {
        tmp = m_board;
        m_board = cb;
        delete tmp;
    }

    m_board->reset();
    m_board->calcState(m_boardRefurbish);

    m_bestMoveAndValue = (MoveAndValue){.value = 0, .move = 0, .duckMove = -1};
    int i;
    for (i = 0; i < MAX_DEPTH; i++) {
        m_arrBestMoves[i] = (MoveAndValue){.value = 0, .move = 0, .duckMove = -1};
    }
}

boolean Game::newGameFromFEN(char *sFEN) {
    reset();
    ChessBoard *board = getBoard();
    int ret = board->parseFEN(sFEN);
    if (ret) {
        commitBoard();
    } else {
        reset();
    }
    return ret;
}

void Game::commitBoard() {
    m_board->commitBoard();
    m_board->calcState(m_boardRefurbish);
}

ChessBoard *Game::getBoard() {
    return m_board;
}

void Game::setPromo(int p) {
    m_promotionPiece = p;
}

int Game::getBestMove() {
    return m_bestMoveAndValue.move;
}
int Game::getBestDuckMove() {
    return m_bestMoveAndValue.duckMove;
}
int Game::getBestValue() {
    return m_bestMoveAndValue.value;
}

int Game::getBestMoveAt(int ply) {
    if (ply >= 0 && ply < MAX_DEPTH) {
        return m_arrBestMoves[ply].move;
    }
    return 0;
}

int Game::getBestDuckMoveAt(int ply) {
    if (ply >= 0 && ply < MAX_DEPTH) {
        return m_arrBestMoves[ply].duckMove;
    }
    return -1;
}

boolean Game::requestMove(int from, int to) {
    ChessBoard *nb = new ChessBoard();
    m_board->calcState(m_boardRefurbish);
    if (m_board->requestMove(from, to, nb, m_boardRefurbish, m_promotionPiece)) {
        m_board = nb;
        return true;
    } else {
        delete nb;
        // DEBUG_PRINT("%d-%d not moved...(request)\n", from, to);
        return false;
    }
}

boolean Game::requestDuckMove(int duckPos) {
    ChessBoard *board = getBoard();
    board->calcState(m_boardRefurbish);
    if (board->requestDuckMove(duckPos)) {
        board->genMoves();
        return true;
    }
    return false;
}

boolean Game::move(int move) {
    ChessBoard *nb = new ChessBoard();

    m_board->calcState(m_boardRefurbish);
    if (m_board->requestMove(move, nb, m_boardRefurbish)) {
        m_board = nb;
        return true;
    } else {
        delete nb;
        return false;
    }
}

void Game::undo() {
    ChessBoard *tmp = m_board;
    ChessBoard *cb = m_board->undoMove();
    if (cb != NULL) {
        m_board = cb;
        delete tmp;
    }
}

#pragma region Search methods

void Game::setQuiescentOn(boolean on) {
    m_quiescentSearchOn = on;
}

// returns the move found
void Game::setSearchTime(int secs) {
    m_milliesGiven = (long) secs;
    m_searchLimit = 0;
}

void Game::setSearchLimit(int depth) {
    m_milliesGiven = 0;
    if (depth <= MAX_DEPTH - QUIESCE_DEPTH) {
        m_searchLimit = depth;
    } else {
        m_searchLimit = MAX_DEPTH - QUIESCE_DEPTH;
    }
}

void Game::search() {
    if (m_bSearching) {
        DEBUG_PRINT("Already searching!", 0);
        return;
    }
    m_bSearching = true;
    m_bestMoveAndValue = (MoveAndValue){.value = 0, .move = 0, .duckMove = -1};
    m_bInterrupted = false;
    m_evalCount = 0;

    m_board->calcState(m_boardRefurbish);

    // no need to search if the game has allready ended
    if (m_board->isEnded()) {
        m_bSearching = false;
        return;
    }

    if (m_board->getNumMoves() == 0) {
        DEBUG_PRINT("NO moves!", 0);
        m_bSearching = false;
        return;
    }

    int variant = m_board->getVariant();
    // DB search only makes sens for the default chess variant
    if (variant == ChessBoard::VARIANT_DEFAULT) {
        int move = searchDB();
        if (move != 0) {
            m_bestMoveAndValue = (MoveAndValue){.value = 0, .move = move, .duckMove = -1};
            m_bSearching = false;
            return;
        }
    }

    m_evalCount++;  // at least one so Nps is valid for non DB move

    startTime();

    char moveBuf[20], duckMoveBuf[4];
    // reset best moves for this search
    int i;
    for (i = 0; i < MAX_DEPTH; i++) {
        m_arrBestMoves[i] = (MoveAndValue){.value = 0, .move = 0, .duckMove = -1};
    }

    if (m_milliesGiven > 0) {
        DEBUG_PRINT("Search with millies given %ld", m_milliesGiven);

        for (m_searchDepth = 1; m_searchDepth < (MAX_DEPTH - QUIESCE_DEPTH); m_searchDepth++) {
            if (variant == ChessBoard::VARIANT_DEFAULT) {
                alphaBeta(m_board, m_searchDepth, -ChessBoard::VALUATION_MATE, ChessBoard::VALUATION_MATE);
            } else {
                alphaBetaDuck(m_board, m_searchDepth, -ChessBoard::VALUATION_MATE, ChessBoard::VALUATION_MATE);
            }

            DEBUG_PRINT("Searched at depth %d - value: %d - move: %d - num moves: %d\n",
                        m_searchDepth,
                        m_arrBestMoves[0].value,
                        getBestMoveAt(m_searchDepth),
                        m_board->getNumMoves());

            if (m_bInterrupted) {
                break;
            } else {
                m_bestMoveAndValue = m_arrBestMoves[0];

                if (m_bestMoveAndValue.value == ChessBoard::VALUATION_MATE) {
                    for (i = 0; i < MAX_DEPTH; i++) {
                        int moveAt = getBestMoveAt(i);
                        if (moveAt != 0) {
                            Move::toDbgString(moveAt, moveBuf);
                            DEBUG_PRINT("%s,", moveBuf);
                        }
                    }
                    DEBUG_PRINT("Found checkmate, stopping search\n", 0);
                    break;
                }

                // bail out if we're over 50% of time, next depth will take more than sum of previous
                if (usedTime()) {
                    DEBUG_PRINT("Time over 50 pct - no further deepening\n", 0);
                    break;
                }
            }
        }
    } else {
        DEBUG_PRINT("Search with limit given: %d\n", m_searchLimit);
        m_searchDepth = m_searchLimit;
        if (variant == ChessBoard::VARIANT_DEFAULT) {
            alphaBeta(m_board, m_searchDepth, -ChessBoard::VALUATION_MATE, ChessBoard::VALUATION_MATE);
        } else {
            alphaBetaDuck(m_board, m_searchDepth, -ChessBoard::VALUATION_MATE, ChessBoard::VALUATION_MATE);
        }

        m_bestMoveAndValue = m_arrBestMoves[0];
    }

    if (m_bInterrupted) {
        DEBUG_PRINT("Interrupted search\n", 0);
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

    m_bSearching = false;
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

    // 50 move rule and repetition check
    if (board->checkEnded()) {
        return ChessBoard::VALUATION_DRAW;
    }

    board->scoreMovesPV(m_arrBestMoves[m_searchDepth - depth].move);

    MoveAndValue best = {.value = (-ChessBoard::VALUATION_MATE) - 1, .move = 0, .duckMove = -1};
    MoveAndValue current, next;
    ChessBoard *nextBoard = m_boardFactory[depth];

    while (board->hasMoreMoves()) {
        current = (MoveAndValue){.value = 0, .move = board->getNextScoredMove(), .duckMove = -1};
        board->makeMove(current.move, nextBoard);

        if (nextBoard->checkInSelfCheck()) {
            // not valid, remove this move and continue
            board->removeMoveElementAt();
            continue;
        }

        // generate the moves for this next board in order to validate the board
        nextBoard->genMoves();

        if (nextBoard->checkInCheck()) {
            nextBoard->setMyMoveCheck();
            current.move = Move_setCheck(current.move);
        }

        // at depth one is at the leaves, so call quiescent search
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

    // no valid moves, so mate or stalemate
    if (board->getNumMoves() == 0) {
        m_evalCount++;
        if (Move_isCheck(board->getMyMove())) {
            return -ChessBoard::VALUATION_MATE;
        }
        return ChessBoard::VALUATION_DRAW;
    }

    return best.value;
}

//
int Game::quiesce(ChessBoard *board, const int depth, int alpha, const int beta) {
    // before any evaluation, first check if time is up
    if (m_evalCount % 1000 == 0) {
        if (timeUp()) {
            m_bInterrupted = true;
        }
    }

    if (m_bInterrupted || depth >= MAX_DEPTH) {
        return alpha;  //
    }

    // administer evaluation count and get the board value
    m_evalCount++;
    const int boardValue = board->boardValue();

    // at this point there can be a beta cutt-off unless we're in check

    if (!Move_isCheck(board->getMyMove())) {
        if (boardValue >= beta) {
            return beta;
        }
    }

    if (depth == 0 || !m_quiescentSearchOn) {
        // max quiesce depth is reached; return this value
        return boardValue;
    }

    if (boardValue >= beta) {
        return beta;
    }

    // update lower bound
    if (boardValue > alpha) {
        alpha = boardValue;
    }
    int value, move;

    ChessBoard *nextBoard = m_boardFactory[MAX_DEPTH - depth];
    board->scoreMoves();
    while (board->hasMoreMoves()) {
        move = board->getNextScoredMove();

        board->makeMove(move, nextBoard);

        // self check is illegal!
        if (nextBoard->checkInSelfCheck()) {
            // not valid, remove this move and continue
            board->removeMoveElementAt();
            continue;
        }

        if (nextBoard->checkInCheck()) {
            nextBoard->setMyMoveCheck();
            move = Move_setCheck(move);
        }

        // quiescent search
        if (Move_isHIT(move) || Move_isCheck(move) || Move_isPromotionMove(move)) {
            // a valid and active move, so continue quiescent search
            nextBoard->genMoves();

            value = -quiesce(nextBoard, depth - 1, -beta, -alpha);

            if (value > alpha) {
                alpha = value;

                if (value >= beta) {
                    return beta;
                }
            }
        }
    }

    // no valid moves, so mate or stalemate
    if (board->getNumMoves() == 0) {
        if (Move_isCheck(board->getMyMove())) {
            return (-ChessBoard::VALUATION_MATE);
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

    // 50 move rule and repetition check, mate
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
    MoveAndValue current, next;
    ChessBoard *nextBoard = m_boardFactory[depth];

    while (board->hasMoreMoves()) {
        current = (MoveAndValue){.value = 0, .move = board->getNextScoredMove(), .duckMove = -1};

        board->makeMove(current.move, nextBoard);

        // generate the moves for this next board in order to validate the board
        nextBoard->genMoves();

        if (nextBoard->areKingsOnTheBoard()) {
            MoveAndValue currentDuck;
            MoveAndValue bestDuck = {.value = (-ChessBoard::VALUATION_MATE) - 1, .move = 0, .duckMove = -1};
            int tmpDuckMove = -1, numMoves = nextBoard->getNumMoves(), i;
            ChessBoard *duckBoard = new ChessBoard();
            for (i = 0; i < numMoves; i++) {
                tmpDuckMove = nextBoard->getMoveAt(i);
                if (Move_isHIT(tmpDuckMove)) {
                    continue;
                }
                currentDuck = (MoveAndValue){.value = 0,
                                             .move = 0,
                                             .duckMove = Move_getTo(tmpDuckMove)};  // actual duckMove is a position

                nextBoard->duplicate(duckBoard);

                if (!duckBoard->requestDuckMove(currentDuck.duckMove)) {
                    DEBUG_PRINT("Could not make duckMove %d, %d\n", currentDuck.duckMove, Move_getFrom(tmpDuckMove));
                    continue;
                }

                duckBoard->calcState(m_boardRefurbish);
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

    // no valid moves => MATE
    if (board->getNumMoves() == 0) {
        m_evalCount++;

        return -ChessBoard::VALUATION_MATE;
    }

    return best.value;
}

// search the hashkey database, randomly choose a move
int Game::searchDB() {
    if (DB_SIZE == 0 || DB_FP == NULL) {
        return 0;
    }
    if (m_board->getNumBoard() > Game::DB_DEPTH) {
        DEBUG_PRINT("Too many plies for database search\n", 0);
        return 0;
    }
    if (m_board->getFirstBoard()->getHashKey() != DEFAULT_START_HASH) {
        DEBUG_PRINT("Game not from default starting position (database search)\n", 0);
        return 0;
    }
    ChessBoard *tmpBoard = new ChessBoard();

    int moveArr[100], iCnt = 0, move;
    //
    m_board->getMoves();
    while (m_board->hasMoreMoves() && iCnt < 100) {
        move = m_board->getNextMove();
        m_board->makeMove(move, tmpBoard);
        const BITBOARD bb = tmpBoard->getHashKey();

        if (findDBKey(bb) < DB_SIZE) {
            moveArr[iCnt++] = move;
        }
    }

    delete tmpBoard;

    if (iCnt == 0) {
        // DEBUG_PRINT("No move found in openingsdatabase\n", 0);
        return 0;
    }

    // DEBUG_PRINT("Choosing from %d moves\n", iCnt);

    timeval time;
    gettimeofday(&time, NULL);
    srand((unsigned int) time.tv_usec);
    int i = rand() % iCnt;
    return moveArr[i];
}

// House variant search
// allowAttack as for putPieceHouse
/*
int Game::searchHouse(boolean allowAttack)
{
        return 0;
}
*/

// allowAttack, if a new piece on the board is allowed to attack immediatly,
// for crazyhouse that's ok, for parachute not
boolean Game::putPieceHouse(const int pos, const int piece, const boolean allowAttack) {
    ChessBoard *nextBoard = new ChessBoard();
    if (m_board->putHouse(pos, piece, nextBoard, m_boardRefurbish, allowAttack)) {
        m_board = nextBoard;
        return true;
    }
    delete nextBoard;
    return false;
}

#pragma endregion

#pragma region Database methods

void Game::loadDB(const char *sFile, int depth) {
    if (DB_FP != NULL) {
        fclose(DB_FP);
        DB_FP = NULL;
        DEBUG_PRINT("Closing database...\n", 0);
    }
    DB_DEPTH = depth;
    DB_SIZE = 0;
    DB_FP = fopen(sFile, "rb");
    if (DB_FP != NULL) {
        fseek(DB_FP, 0, SEEK_END);
        DB_SIZE = ftell(DB_FP) / 8;
        rewind(DB_FP);

        /*
        BITBOARD bb, bbPrev = 0;
        for(int i = 0; i < DB_SIZE; i++){

            if(readDBAt(i, bb)){
                if(bb > bbPrev)
                    DEBUG_PRINT("DB %d = %lld bigger\n", i, bb);
                else
                    DEBUG_PRINT("DB %d = %lld SMALLER\n", i, bb);

                bbPrev = bb;
            }
        }

        long pos = findDBKey(bb);
        if(pos){

            DEBUG_PRINT("FOUND KEY %lld...%ld\n", bb, pos);
        }
        */
        // DEBUG_PRINT("Set filepointer ok, filesize %ld...\n", DB_SIZE);

    } else {
        DEBUG_PRINT("Could not open file %s\n", sFile);
    }
}

// Binary search returns leftmost entry or size of db
long Game::findDBKey(BITBOARD bbKey) {
    int left, right, mid;
    BITBOARD bb;

    left = 0;
    right = DB_SIZE - 1;

    while (left < right) {
        mid = (left + right) / 2;

        if (readDBAt(mid, bb)) {
            // DEBUG_PRINT("Binary search %d-%d = %lld\n", left, right, bb);

            if (bbKey <= bb) {
                right = mid;
            } else {
                left = mid + 1;
            }
        } else {
            return DB_SIZE;
        }
    }

    if (readDBAt(left, bb)) {
        return (bb == bbKey) ? left : DB_SIZE;
    } else {
        return DB_SIZE;
    }
}

boolean Game::readDBAt(int iPos, BITBOARD &bb) {
    if (iPos < DB_SIZE && fseek(DB_FP, iPos * 8, SEEK_SET) == 0) {
        static char buf[8];
        int cnt = fread(buf, 1, 8, DB_FP);

        if (cnt != 8) {
            DEBUG_PRINT("Could not read database at %d\n", iPos);
            return false;
        }

        // memcpy(&bb, buf, 8);
        bb = 0;
        bb |= ((BITBOARD) buf[0] & 0xFF) << 56;
        bb |= ((BITBOARD) buf[1] & 0xFF) << 48;
        bb |= ((BITBOARD) buf[2] & 0xFF) << 40;
        bb |= ((BITBOARD) buf[3] & 0xFF) << 32;
        bb |= ((BITBOARD) buf[4] & 0xFF) << 24;
        bb |= ((BITBOARD) buf[5] & 0xFF) << 16;
        bb |= ((BITBOARD) buf[6] & 0xFF) << 8;
        bb |= ((BITBOARD) buf[7] & 0xFF);

        return true;
        // sprintf(s, "===\n %lld {%d %d %d %d %d %d %d %d}\n", bb, buf[0], buf[1], buf[2], buf[3],
        // buf[4], buf[5], buf[6], buf[7]); DEBUG_PRINT(s);
    }
    DEBUG_PRINT("Position not found\n", 0);
    return false;
}

#pragma endregion

#pragma region Search timing functions

void Game::startTime() {
    timeval time;
    gettimeofday(&time, NULL);
    m_millies = (time.tv_sec * 1000) + (time.tv_usec / 1000);
}

boolean Game::timeUp() {
    if (m_milliesGiven == 0) {
        return false;
    }
    timeval time;
    gettimeofday(&time, NULL);
    return (m_milliesGiven < ((time.tv_sec * 1000) + (time.tv_usec / 1000) - m_millies));
}

// return true if we consumed more than x'd of tme
boolean Game::usedTime() {
    timeval time;
    gettimeofday(&time, NULL);
    return ((m_milliesGiven / 3) < ((time.tv_sec * 1000) + (time.tv_usec / 1000) - m_millies));
}

long Game::timePassed() {
    timeval time;
    gettimeofday(&time, NULL);
    return ((time.tv_sec * 1000) + (time.tv_usec / 1000) - m_millies);
}

#pragma endregion