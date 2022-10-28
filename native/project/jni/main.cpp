#include "common.h"

#include <pthread.h>
#include <unistd.h>
#include <functional>

#include "ChessBoard.h"
#include "Game.h"
#include "Move.h"

typedef bool (*TestFunction)();

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
void newGame();
void printFENAndState(ChessBoard *board);
void printMove(int move);
bool expectEqualInt(int a, int b, char *message);
bool expectEqualString(char *a, char *b, char *message);

using std::function;

static Game *g;

void *search_thread(void *arg) {
    g->search();
}

int main(int argc, char **argv) {
    DEBUG_PRINT("\n\n=== START TESTS == \n", 0);

    ChessBoard::initStatics();

    TestFunction tests[] = {testSetupNewGame,
                            testSetupMate,
                            testInCheck,
                            testInSelfCheck,
                            testSetupCastle,
                            testSetupQuiesce,
                            testMoves,
                            testDB,
                            testGame};

    // TestFunction tests[] = {
    //     testInCheck,
    // };

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

    return expectEqualString("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                             buf,
                             "testSetupNewGame");
}

bool testInCheck() {
    ChessBoard *board = g->getBoard();

    board->reset();
    board->put(ChessBoard::f8, ChessBoard::KING, ChessBoard::BLACK);
    board->put(ChessBoard::b1, ChessBoard::KING, ChessBoard::WHITE);
    board->put(ChessBoard::a8, ChessBoard::ROOK, ChessBoard::WHITE);
    board->setCastlingsEPAnd50(0, 0, 0, 0, -1, 0);
    board->setTurn(0);
    g->commitBoard(ChessBoard::VARIANT_DEFAULT);

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
    g->commitBoard(ChessBoard::VARIANT_DEFAULT);

    return expectEqualInt(g->getBoard()->getState(), ChessBoard::MATE, "State should equal MATE");
}

bool testGame() {
    ChessBoard *board, *tmp = new ChessBoard();

    newGame();
    board = g->getBoard();

    int m, i = 0;
    boolean bMoved;
    while (!board->isEnded()) {
        g->setSearchTime(1);
        startThread();
        while (g->m_bSearching) {
            sleep(1);
        }

        m = g->getBestMove();

        bMoved = g->move(m);
        board = g->getBoard();

        if (!bMoved) {
            DEBUG_PRINT("\nBAILING OUT - not moved\n", 0);
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
    g->commitBoard(ChessBoard::VARIANT_DEFAULT);

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
    g->commitBoard(ChessBoard::VARIANT_DEFAULT);

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

    bool ret = g->requestMove(ChessBoard::e2, ChessBoard::e4) &&
               g->requestMove(ChessBoard::e7, ChessBoard::e5) &&
               g->requestMove(ChessBoard::g1, ChessBoard::f3) &&
               g->requestMove(ChessBoard::b8, ChessBoard::c6) &&
               g->requestMove(ChessBoard::d1, ChessBoard::e2) &&
               g->requestMove(ChessBoard::f8, ChessBoard::e7);

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
    ChessBoard *board = g->getBoard();

    board->put(ChessBoard::a8, ChessBoard::ROOK, ChessBoard::BLACK);
    board->put(ChessBoard::b8, ChessBoard::KNIGHT, ChessBoard::BLACK);
    board->put(ChessBoard::c8, ChessBoard::BISHOP, ChessBoard::BLACK);
    board->put(ChessBoard::d8, ChessBoard::QUEEN, ChessBoard::BLACK);
    board->put(ChessBoard::e8, ChessBoard::KING, ChessBoard::BLACK);
    board->put(ChessBoard::f8, ChessBoard::BISHOP, ChessBoard::BLACK);
    board->put(ChessBoard::g8, ChessBoard::KNIGHT, ChessBoard::BLACK);
    board->put(ChessBoard::h8, ChessBoard::ROOK, ChessBoard::BLACK);
    board->put(ChessBoard::a7, ChessBoard::PAWN, ChessBoard::BLACK);
    board->put(ChessBoard::b7, ChessBoard::PAWN, ChessBoard::BLACK);
    board->put(ChessBoard::c7, ChessBoard::PAWN, ChessBoard::BLACK);
    board->put(ChessBoard::d7, ChessBoard::PAWN, ChessBoard::BLACK);
    board->put(ChessBoard::e7, ChessBoard::PAWN, ChessBoard::BLACK);
    board->put(ChessBoard::f7, ChessBoard::PAWN, ChessBoard::BLACK);
    board->put(ChessBoard::g7, ChessBoard::PAWN, ChessBoard::BLACK);
    board->put(ChessBoard::h7, ChessBoard::PAWN, ChessBoard::BLACK);

    board->put(ChessBoard::a1, ChessBoard::ROOK, ChessBoard::WHITE);
    board->put(ChessBoard::b1, ChessBoard::KNIGHT, ChessBoard::WHITE);
    board->put(ChessBoard::c1, ChessBoard::BISHOP, ChessBoard::WHITE);
    board->put(ChessBoard::d1, ChessBoard::QUEEN, ChessBoard::WHITE);
    board->put(ChessBoard::e1, ChessBoard::KING, ChessBoard::WHITE);
    board->put(ChessBoard::f1, ChessBoard::BISHOP, ChessBoard::WHITE);
    board->put(ChessBoard::g1, ChessBoard::KNIGHT, ChessBoard::WHITE);
    board->put(ChessBoard::h1, ChessBoard::ROOK, ChessBoard::WHITE);
    board->put(ChessBoard::a2, ChessBoard::PAWN, ChessBoard::WHITE);
    board->put(ChessBoard::b2, ChessBoard::PAWN, ChessBoard::WHITE);
    board->put(ChessBoard::c2, ChessBoard::PAWN, ChessBoard::WHITE);
    board->put(ChessBoard::d2, ChessBoard::PAWN, ChessBoard::WHITE);
    board->put(ChessBoard::e2, ChessBoard::PAWN, ChessBoard::WHITE);
    board->put(ChessBoard::f2, ChessBoard::PAWN, ChessBoard::WHITE);
    board->put(ChessBoard::g2, ChessBoard::PAWN, ChessBoard::WHITE);
    board->put(ChessBoard::h2, ChessBoard::PAWN, ChessBoard::WHITE);

    board->setCastlingsEPAnd50(1, 1, 1, 1, -1, 0);

    g->commitBoard(ChessBoard::VARIANT_DEFAULT);
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
