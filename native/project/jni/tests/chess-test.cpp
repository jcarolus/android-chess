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

    int movesPerformed = 0;
    while (movesPerformed < scenario.numMoves) {

        ChessTest::startSearchThread();
        while (scenario.game->m_bSearching) {
            sleep(1);
        }

        int m = scenario.game->getBestMove();
        int d = scenario.game->getBestDuckMove();

        boolean bMoved = scenario.game->move(m);
        if (!bMoved) {
            // printMove(m);
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
        }

        movesPerformed++;
    }
    ChessBoard *board = scenario.game->getBoard();

    char buf[512];
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
