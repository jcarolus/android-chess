#include "chess-test.h"

void ChessTest::startSearchThread() {
    pthread_t tid;
    pthread_create(&tid, NULL, &Game::search_wrapper, NULL);
}

bool ChessTest::expectEqualInt(int a, int b, char *message) {
    if (a != b) {
        DEBUG_PRINT("FAILED: %s => Expected [%d] but got [%d]\n", message, a, b);
        return false;
    }
    return true;
}

bool ChessTest::expectEqualString(char *a, char *b, char *message) {
    if (strcmp(a, b) != 0) {
        DEBUG_PRINT("FAILED: %s => Expected [%s] but got [%s]\n", message, a, b);
        return false;
    }
    return true;
}

bool ChessTest::expectEngineMove(EngineInOutFEN scenario) {
    scenario.game->newGameFromFEN(scenario.sInFEN);

    scenario.game->setSearchLimit(scenario.depth);

    char buf[1024];
    ChessBoard *board;
    int movesPerformed = 0;
    while (movesPerformed < scenario.numMoves) {
        ChessTest::startSearchThread();
        sleep(1);
        while (scenario.game->m_bSearching) {
            sleep(1);
        }

        int m = scenario.game->getBestMove();
        int d = scenario.game->getBestDuckMove();

        boolean bMoved = scenario.game->move(m);
        if (!bMoved) {
            board = scenario.game->getBoard();
            printMove(m);
            printFENAndState(board);
            board->printB(buf);
            DEBUG_PRINT("DBG: %s\n", buf);
            DEBUG_PRINT("Not moved for [%s] on move number %d\n", scenario.message, movesPerformed);
            return false;
        }

        if (d != -1) {
            bMoved = scenario.game->requestDuckMove(d);
            if (!bMoved) {
                // printMove(m);
                DEBUG_PRINT("Not duck moved for [%s] on move number %d\n", scenario.message, movesPerformed);
                return false;
            }
        } else if (scenario.isDuck && !scenario.game->getBoard()->isEnded()) {
            DEBUG_PRINT("Expected a duck move for [%s] on  move number %d\n", scenario.message, movesPerformed);
            return false;
        }

        movesPerformed++;
    }
    board = scenario.game->getBoard();
    board->toFEN(buf);

    return expectEqualString(scenario.sOutFEN, buf, scenario.message);
}

bool ChessTest::expectSequence(SequenceInOutFEN scenario) {
    scenario.game->newGameFromFEN(scenario.sInFEN);

    for (int i = 0; i < scenario.moveNum; i++) {
        boolean bMoved = scenario.game->move(scenario.moves[i]);
        if (!bMoved) {
            DEBUG_PRINT("Not moved for [%s] - [%d]\n", scenario.message, i);
            return false;
        }
    }

    ChessBoard *board = scenario.game->getBoard();
    char buf[512];
    board->toFEN(buf);

    return expectEqualString(scenario.sOutFEN, buf, scenario.message);
}

bool ChessTest::expectNonSequence(NonSequenceInFEN scenario) {
    scenario.game->newGameFromFEN(scenario.sInFEN);

    for (int i = 0; i < scenario.moveNum; i++) {
        boolean bMoved = scenario.game->move(scenario.moves[i]);
        if (bMoved) {
            DEBUG_PRINT("Moved for [%s] - [%d]\n", scenario.message, i);
            return false;
        }
    }

    return true;
}

bool ChessTest::expectStateForFEN(Game *game, char *sFEN, int state, char *message) {
    game->newGameFromFEN(sFEN);

    return ChessTest::expectEqualInt(game->getBoard()->getState(), state, message);
}

bool ChessTest::expectInFENIsOutFEN(Game *game, char *sFEN, char *message) {
    game->newGameFromFEN(sFEN);

    ChessBoard *board = game->getBoard();
    char buf[512];
    board->toFEN(buf);

    return expectEqualString(sFEN, buf, message);
}

bool ChessTest::expectEndingStateWithinMaxMoves(EngineInFENUntilState scenario) {
    scenario.game->setSearchLimit(scenario.depth);

    scenario.game->newGameFromFEN(scenario.sInFEN);
    int movesPerformed = 0;
    int gameState = scenario.game->getBoard()->getState();
    ChessBoard *board;
    char buf[1024];

    while (movesPerformed < scenario.maxMoves) {
        scenario.game->search();

        int m = scenario.game->getBestMove();
        int d = scenario.game->getBestDuckMove();

        boolean bMoved = scenario.game->move(m);
        if (!bMoved) {
            board = scenario.game->getBoard();
            printMove(m);
            printFENAndState(board);
            board->printB(buf);
            DEBUG_PRINT("DBG: %s\n", buf);
            DEBUG_PRINT("Not moved for [%s] on move number %d\n", scenario.message, movesPerformed);
            return false;
        }

        if (d != -1) {
            bMoved = scenario.game->requestDuckMove(d);
            if (!bMoved) {
                // printMove(m);
                DEBUG_PRINT("Not duck moved for [%s] on move number %d\n", scenario.message, movesPerformed);
                return false;
            }
        } else if (scenario.isDuck && !scenario.game->getBoard()->isEnded()) {
            DEBUG_PRINT("Expected a duck move for [%s] on  move number %d\n", scenario.message, movesPerformed);
            return false;
        }

        gameState = scenario.game->getBoard()->getState();

        if (gameState == scenario.expectedState) {
            break;
        }

        movesPerformed++;
    }

    return expectEqualInt(scenario.expectedState, gameState, scenario.message);
}

bool ChessTest::expectMovesForFEN(MovesForFEN scenario) {
    scenario.game->newGameFromFEN(scenario.sInFEN);

    ChessBoard *board = scenario.game->getBoard();
    int moveCount = board->getNumMoves();
    if (moveCount != scenario.expectedMoveCount && scenario.all ||
        moveCount < scenario.expectedMoveCount && !scenario.all) {
        DEBUG_PRINT("Expected movecount [%d], but got [%d]\n", scenario.expectedMoveCount, moveCount);
        return false;
    }
    int i;
    char moveArray[moveCount][20];

    ChessBoard *newBoard = new ChessBoard();
    for (int i = 0; i < moveCount; i++) {
        int move = board->getMoveAt(i);
        board->makeMove(move, newBoard);

        newBoard->myMoveToString(moveArray[i]);
    }

    delete newBoard;

    for (i = 0; i < scenario.expectedMoveCount; i++) {
        bool contains = false;
        for (int j = 0; j < moveCount; j++) {
            if (strcmp(scenario.expectedMoves[i], moveArray[j]) == 0) {
                contains = true;
                break;
            }
        }
        if (!contains) {
            return false;
        }
    }

    return true;
}

void ChessTest::printMove(int move) {
    char buf[10];
    Move::toDbgString(move, buf);
    DEBUG_PRINT("Move %s\n", buf);
}

void ChessTest::printFENAndState(ChessBoard *board) {
    char buf[512];
    board->toFEN(buf);
    DEBUG_PRINT("\nFEN\t%s\n", buf);

    int state = board->getState();

    switch (state) {
        case ChessBoard::PLAY:
            DEBUG_PRINT("Play\n", 0);
            break;

        case ChessBoard::CHECK:
            DEBUG_PRINT("Check\n", 0);
            break;

        case ChessBoard::INVALID:
            DEBUG_PRINT("Invalid\n", 0);
            break;

        case ChessBoard::DRAW_MATERIAL:
            DEBUG_PRINT("Draw material\n", 0);
            break;

        case ChessBoard::DRAW_50:
            DEBUG_PRINT("Draw 50 move\n", 0);
            break;

        case ChessBoard::MATE:
            DEBUG_PRINT("Mate\n", 0);
            break;

        case ChessBoard::STALEMATE:
            DEBUG_PRINT("Stalemate\n", 0);
            break;

        case ChessBoard::DRAW_REPEAT:
            DEBUG_PRINT("Draw repetition\n", 0);
            break;

        default:
            break;
    }
}
