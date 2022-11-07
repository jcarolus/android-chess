#include "common.h"

#include <pthread.h>
#include <unistd.h>
#include <functional>

#include "ChessBoard.h"
#include "Game.h"
#include "Move.h"

typedef bool (*TestFunction)();

typedef struct {
    char *sInFEN;
    char *sOutFEN;
    int depth;
    char *message;
} EngineInOutFEN;

typedef struct {
    char *sInFEN;
    char *sOutFEN;
    int *moves;
    int moveNum;
    char *message;
} SequenceInOutFEN;

void miniTest();
void startThread();
bool testGame();
void speedTest();
bool testDB();
bool testSetupNewGame();
bool testSetupMate();
bool testInCheck();
bool testInSelfCheck();
bool testSetupCastle();
bool testSetupQuiesce();
bool testHouse();
bool testMoves();
bool testGenmoves();
bool testDuck();
bool testDuckGame();
bool testEngine();
bool testSequence();
void newGame();
void newGameDuck();
void printFENAndState(ChessBoard *board);
void printMove(int move);
bool expectEqualInt(int a, int b, char *message);
bool expectEqualString(char *a, char *b, char *message);
bool expectEngineMove(EngineInOutFEN scenario);
bool expectSequence(SequenceInOutFEN scenario);

using std::function;

static Game *g;

void *search_thread(void *arg) {
    g->search();
}

int main(int argc, char **argv) {
    DEBUG_PRINT("\n\n=== START TESTS == \n", 0);

    ChessBoard::initStatics();

    // TestFunction tests[] = {testSetupNewGame,
    //                         testSetupMate,
    //                         testInCheck,
    //                         testInSelfCheck,
    //                         testSetupCastle,
    //                         testSetupQuiesce,
    //                         testMoves,
    //                         testDB,
    //                         testDuck,
    //                         testEngine,
    //                         testSequence};

    TestFunction tests[] = {
        // testGame,
        testDuckGame,
    };

    int testFail = 0, testSuccess = 0;
    for (int i = 0; i < sizeof(tests) / sizeof(TestFunction); i++) {
        g = new Game();
        if (tests[i]()) {
            testSuccess++;
        } else {
            testFail++;
        }
        delete g;
    }

    DEBUG_PRINT("\n\n=== DONE === SUCCESS: [%d] FAIL: [%d]\n", testSuccess, testFail);
}

void startThread() {
    pthread_t tid;
    pthread_create(&tid, NULL, search_thread, NULL);
}

bool testSetupNewGame() {
    newGame();
    char buf[255];

    ChessBoard *board = g->getBoard();
    board->toFEN(buf);

    return expectEqualString("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", buf, "testSetupNewGame");
}

bool testInCheck() {
    ChessBoard *board = g->getBoard();

    board->reset();
    board->put(ChessBoard::f8, ChessBoard::KING, ChessBoard::BLACK);
    board->put(ChessBoard::b1, ChessBoard::KING, ChessBoard::WHITE);
    board->put(ChessBoard::a8, ChessBoard::ROOK, ChessBoard::WHITE);
    board->setCastlingsEPAnd50(0, 0, 0, 0, -1, 0);
    board->setTurn(0);
    g->commitBoard();

    // bool b = board->checkInCheck();
    // if (b) {
    //     DEBUG_PRINT("in check!\n", 0);
    // }

    return expectEqualInt(g->getBoard()->getState(), ChessBoard::CHECK, "State should equal CHECK");
}

bool testInSelfCheck() {
    return true;
}

bool testSetupMate() {
    ChessBoard *board = g->getBoard();

    board->reset();
    board->put(ChessBoard::f8, ChessBoard::KING, ChessBoard::BLACK);
    board->put(ChessBoard::f6, ChessBoard::KING, ChessBoard::WHITE);
    board->put(ChessBoard::a8, ChessBoard::ROOK, ChessBoard::WHITE);
    board->setCastlingsEPAnd50(0, 0, 0, 0, -1, 0);
    board->setTurn(0);
    g->commitBoard();

    return expectEqualInt(g->getBoard()->getState(), ChessBoard::MATE, "State should equal MATE");
}

bool testGame() {
    ChessBoard *board;

    newGame();
    board = g->getBoard();

    int m, i = 0;
    boolean bMoved;
    while (!board->isEnded()) {
        g->setSearchTime(3);
        // g->setSearchLimit(2);
        startThread();
        while (g->m_bSearching) {
            sleep(1);
        }

        m = g->getBestMove();

        bMoved = g->move(m);
        board = g->getBoard();

        char buf[20];
        board->myMoveToString(buf);
        DEBUG_PRINT("\n=====> %d, %d, %s\n", board->getNumBoard(), board->getState(), buf);

        if (!bMoved) {
            DEBUG_PRINT("\nBAILING OUT - not moved %d\n", m);
            break;
        }

        if (i++ > 5) {
            break;
        }
    }

    printFENAndState(board);
    char buf[512];
    board->printB(buf);

    DEBUG_PRINT("\n%s\n", buf);

    return true;
}

bool testDB() {
    g->loadDB("db.bin", 3);

    newGame();

    int move = g->searchDB();
    printMove(move);

    g->move(move);

    move = g->searchDB();
    printMove(move);

    return true;
}

bool testSetupCastle() {
    ChessBoard *board = g->getBoard();

    board->put(ChessBoard::c8, ChessBoard::KING, ChessBoard::BLACK);
    board->put(ChessBoard::a8, ChessBoard::ROOK, ChessBoard::BLACK);
    // board->put(ChessBoard::b8, ChessBoard::ROOK, ChessBoard::BLACK);

    board->put(ChessBoard::g1, ChessBoard::ROOK, ChessBoard::WHITE);
    board->put(ChessBoard::f1, ChessBoard::KING, ChessBoard::WHITE);

    board->setCastlingsEPAnd50(1, 1, 1, 1, -1, 0);
    // board->setTurn(0);
    g->commitBoard();

    char buf[512];
    board->toFEN(buf);

    bool ret = expectEqualString("r1k5/8/8/8/8/8/8/5KR1 w KQkq - 0 1", buf, "testSetupCastle");

    return ret;
}

bool testSetupQuiesce() {
    ChessBoard *board = g->getBoard();

    board->put(ChessBoard::d7, ChessBoard::PAWN, ChessBoard::BLACK);
    board->put(ChessBoard::f8, ChessBoard::KING, ChessBoard::BLACK);
    board->put(ChessBoard::c6, ChessBoard::PAWN, ChessBoard::BLACK);
    board->put(ChessBoard::e7, ChessBoard::QUEEN, ChessBoard::BLACK);

    board->put(ChessBoard::b4, ChessBoard::BISHOP, ChessBoard::WHITE);
    board->put(ChessBoard::c3, ChessBoard::PAWN, ChessBoard::WHITE);
    board->put(ChessBoard::d4, ChessBoard::PAWN, ChessBoard::WHITE);
    board->put(ChessBoard::e5, ChessBoard::PAWN, ChessBoard::WHITE);
    board->put(ChessBoard::d3, ChessBoard::KING, ChessBoard::WHITE);

    board->setCastlingsEPAnd50(0, 0, 0, 0, -1, 0);
    board->setTurn(0);
    g->commitBoard();

    // printFENAndState(board);
    return true;
}

bool testHouse() {
    ChessBoard *board = g->getBoard();

    printFENAndState(board);

    if (g->putPieceHouse(ChessBoard::e2, ChessBoard::KNIGHT, false)) {
        DEBUG_PRINT("PUT HOUSE\n", 0);
    }

    // printFENAndState(board);
    return true;
}

bool testMoves() {
    newGame();

    bool ret = g->requestMove(ChessBoard::e2, ChessBoard::e4) && g->requestMove(ChessBoard::e7, ChessBoard::e5) &&
               g->requestMove(ChessBoard::g1, ChessBoard::f3) && g->requestMove(ChessBoard::b8, ChessBoard::c6) &&
               g->requestMove(ChessBoard::d1, ChessBoard::e2) && g->requestMove(ChessBoard::f8, ChessBoard::e7);

    if (!ret) {
        DEBUG_PRINT("testMoves failed\n", 0);
    }

    return ret;
}

bool testGenmoves() {
    ChessBoard *board = g->getBoard();
    board->hasMoreMoves();
    board->getNextMove();
    board->getNumMoves();

    return true;
}

bool testDuck() {
    char buf[512];
    ChessBoard *board;
    bool ret;

    newGameDuck();

    board = g->getBoard();
    board->toFEN(buf);
    ret = expectEqualString("rnbqkbnr/pppppppp/8/7$/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", buf, "testDuck");
    if (!ret) {
        return false;
    }

    ret = g->requestMove(ChessBoard::e2, ChessBoard::e4);
    ret = g->requestDuckMove(ChessBoard::e6);
    if (!ret) {
        DEBUG_PRINT("no request duck move 1", 0);
        return false;
    }
    ret = g->requestMove(ChessBoard::e7, ChessBoard::e5);
    if (ret) {
        DEBUG_PRINT("move e7-e5 should not", 0);
        return false;
    }

    ret = g->requestMove(ChessBoard::d7, ChessBoard::e6);
    if (ret) {
        DEBUG_PRINT("move d7-e6 should not", 0);
        return false;
    }

    board = g->getBoard();
    board->toFEN(buf);
    ret = expectEqualString("rnbqkbnr/pppppppp/4$3/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1", buf, "testDuck");
    if (!ret) {
        return false;
    }

    ret = expectEqualInt(board->getDuckPos(), ChessBoard::e6, "testDuck");

    // board->printB(buf);
    // DEBUG_PRINT("\n%s\n", buf);
    ret = g->requestMove(ChessBoard::d7, ChessBoard::d5);
    if (!ret) {
        DEBUG_PRINT("no d7-d5", 0);
        return false;
    }

    ret = g->requestDuckMove(ChessBoard::e3);
    if (!ret) {
        DEBUG_PRINT("no duck e3", 0);
        return false;
    }

    ret = g->requestMove(ChessBoard::f1, ChessBoard::b5);
    if (!ret) {
        DEBUG_PRINT("no f1-b5", 0);
        return false;
    }

    ret = g->requestDuckMove(ChessBoard::f3);
    if (!ret) {
        DEBUG_PRINT("no duck f3", 0);
        return false;
    }

    ret = g->requestMove(ChessBoard::f7, ChessBoard::f6);
    if (!ret) {
        DEBUG_PRINT("no f7-f6", 0);
        return false;
    }

    ret = g->requestDuckMove(ChessBoard::f4);
    if (!ret) {
        DEBUG_PRINT("no duck f4 x", 0);
        return false;
    }

    ret = g->requestMove(ChessBoard::b5, ChessBoard::e8);
    if (!ret) {
        DEBUG_PRINT("no b5-e8", 0);
        return false;
    }

    ret = expectEqualInt(ChessBoard::MATE, g->getBoard()->getState(), "State mate");

    return ret;
}

bool testDuckGame() {
    ChessBoard *board;

    newGameDuck();
    board = g->getBoard();

    int m, d, i = 0;
    boolean bMoved, bDuckMoved;
    while (!board->isEnded()) {
        // g->setSearchTime(1);
        g->setSearchLimit(2);
        startThread();
        while (g->m_bSearching) {
            sleep(1);
        }

        m = g->getBestMove();
        d = g->getBestDuckMove();

        bMoved = g->move(m);
        bDuckMoved = g->requestDuckMove(d);

        board = g->getBoard();

        if (!bMoved) {
            DEBUG_PRINT("\nBAILING OUT - not moved\n", 0);
            break;
        }

        if (!bDuckMoved) {
            DEBUG_PRINT("\nBAILING OUT - duck not moved\n", 0);
            break;
        }

        char buf[20];
        board->myMoveToString(buf);
        DEBUG_PRINT("\n=====> %d, %d, %s\n", board->getNumBoard(), board->getState(), buf);

        if (i++ > 5) {
            break;
        }
    }

    printFENAndState(board);
    char buf[512];
    board->printB(buf);

    DEBUG_PRINT("\n%s\n", buf);

    return true;
}

bool testEngine() {
    EngineInOutFEN scenarios[3] = {
        {"8/8/8/8/8/r2k4/8/3K4 b - - 0 1", "8/8/8/8/8/3k4/8/r2K4 w - - 1 1", 1, "Mate in one"},
        {"r6k/6pp/8/8/8/8/1R6/1R1K4 w - - 0 1", "rR5k/6pp/8/8/8/8/8/1R1K4 b - - 1 1", 2, "Mate in two"},
        {"2Q5/5pk1/8/8/1b6/1b6/r3n1P1/2K5 w - - 0 1", "2Q5/5pk1/8/8/1b6/1b6/r3n1P1/1K6 b - - 1 1", 2, "In check"}};

    bool bRet = true;

    for (int i = 0; i < 3; i++) {
        if (!expectEngineMove(scenarios[i])) {
            bRet = false;
        }
    }

    return bRet;
}

bool testSequence() {
    SequenceInOutFEN scenarios[2] = {
        {"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
         "r1bqkbnr/pppp1ppp/2n5/8/3pP3/5N2/PPP2PPP/RNBQKB1R w KQkq - 0 4",
         (int[]){Move_makeMoveFirstPawn(ChessBoard::e2, ChessBoard::e4),
                 Move_makeMoveFirstPawn(ChessBoard::e7, ChessBoard::e5),
                 Move_makeMove(ChessBoard::g1, ChessBoard::f3),
                 Move_makeMove(ChessBoard::b8, ChessBoard::c6),
                 Move_makeMoveFirstPawn(ChessBoard::d2, ChessBoard::d4),
                 Move_makeMoveHit(ChessBoard::e5, ChessBoard::d4)},
         6,
         "Opening"},
        {"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
         "rnb1kbnr/pppp1ppp/4p3/8/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3",
         (int[]){Move_makeMove(ChessBoard::f2, ChessBoard::f3),
                 Move_makeMove(ChessBoard::e7, ChessBoard::e6),
                 Move_makeMoveFirstPawn(ChessBoard::g2, ChessBoard::g4),
                 Move_setCheck(Move_makeMove(ChessBoard::d8, ChessBoard::h4))},
         4,
         "To mate"},
    };

    bool bRet = true;

    for (int i = 0; i < 2; i++) {
        if (!expectSequence(scenarios[i])) {
            bRet = false;
        }
    }

    return bRet;
}

void speedTest() {
    ChessBoard *board = g->getBoard();

    newGame();

    printFENAndState(board);

    // sprintf(s, "State %d = %d = %d\n", g->getBoard()->getState(), g->getBoard()->isEnded(),
    // g->getBoard()->getNumMoves()); DEBUG_PRINT(s);

    g->setSearchTime(10);
    g->search();
}

void newGame() {
    g->newGameFromFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
}

void newGameDuck() {
    g->newGameFromFEN("rnbqkbnr/pppppppp/8/7$/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
}

void printMove(int move) {
    char buf[10];
    Move::toDbgString(move, buf);
    DEBUG_PRINT("Move %s\n", buf);
}

void printFENAndState(ChessBoard *board) {
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

bool expectEqualInt(int a, int b, char *message) {
    if (a != b) {
        DEBUG_PRINT("FAILED: %s => Expected [%d] but got [%d]\n", message, a, b);
        return false;
    }
    return true;
}

bool expectEqualString(char *a, char *b, char *message) {
    if (strcmp(a, b) != 0) {
        DEBUG_PRINT("FAILED: %s => Expected [%s] but got [%s]\n", message, a, b);
        return false;
    }
    return true;
}

bool expectEngineMove(EngineInOutFEN scenario) {
    g->newGameFromFEN(scenario.sInFEN);

    g->setSearchLimit(scenario.depth);
    startThread();
    while (g->m_bSearching) {
        sleep(1);
    }

    int m = g->getBestMove();

    boolean bMoved = g->move(m);
    if (!bMoved) {
        printMove(m);
        DEBUG_PRINT("Not moved for [%s]\n", scenario.message);
        return false;
    }
    ChessBoard *board = g->getBoard();

    char buf[512];
    board->toFEN(buf);

    return expectEqualString(scenario.sOutFEN, buf, scenario.message);
}

bool expectSequence(SequenceInOutFEN scenario) {
    g->newGameFromFEN(scenario.sInFEN);

    for (int i = 0; i < scenario.moveNum; i++) {
        boolean bMoved = g->move(scenario.moves[i]);
        if (!bMoved) {
            DEBUG_PRINT("Not moved for [%s] - [%d]\n", scenario.message, i);
            return false;
        }
    }

    ChessBoard *board = g->getBoard();
    char buf[512];
    board->toFEN(buf);

    return expectEqualString(scenario.sOutFEN, buf, scenario.message);
}