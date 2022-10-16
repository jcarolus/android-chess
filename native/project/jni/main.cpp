#include "common.h"

#include <pthread.h>
#include <unistd.h>
#include <functional>

#include "ChessBoard.h"
#include "Game.h"

typedef bool (*TestFunction)();

void miniTest();
void unitTest();
void startThread();
bool testSpecial();
bool testGame();
bool testSpeed();
bool testDB();
bool testSetupMate();
bool testSetupCastle();
bool testSetupQuiesce();
bool testHouse();
void newGame();
void printFENAndState(ChessBoard *board);
bool expectEqualInt(int a, int b, char *message);

using std::function;

static Game *g;

void *search_thread(void *arg) {
    g->search();
}

int main(int argc, char **argv) {
    ChessBoard::initStatics();

    TestFunction tests[] = {testSpecial, testSetupCastle, testSetupQuiesce};

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

    // miniTest();
    // unitTest();
    // testSpeed();
    // testGame();
    // sleep(5);
    DEBUG_PRINT("\n\n=== DONE === SUCCESS: [%d] FAIL: [%d]\n", testSuccess, testFail);
}

void miniTest() {
    g = new Game();
    testSetupQuiesce();
    g->searchLimited(2);
    delete g;

    g = new Game();
    testSetupMate();
    g->searchLimited(4);

    delete g;
}
void unitTest() {
    g = new Game();
    testSpecial();
    delete g;
    g = new Game();
    testSetupQuiesce();
    delete g;
    g = new Game();
    // testSpeed();
    delete g;

    // testSetupCastle();
}

void startThread() {
    pthread_t tid;

    DEBUG_PRINT("Creating thread\n", 0);
    pthread_create(&tid, NULL, search_thread, NULL);
    DEBUG_PRINT("Done creatingthread\n", 0);
}

bool testSpecial() {
    ChessBoard *board = g->getBoard();

    board->reset();
    board->put(ChessBoard::f8, ChessBoard::KING, ChessBoard::BLACK);
    board->put(ChessBoard::f6, ChessBoard::KING, ChessBoard::WHITE);
    board->put(ChessBoard::a8, ChessBoard::ROOK, ChessBoard::WHITE);
    board->setCastlingsEPAnd50(0, 0, 0, 0, -1, 0);
    board->setTurn(0);
    g->commitBoard();

    return expectEqualInt(g->getBoard()->getState(),
                          ChessBoard::MATE,
                          "State should equal MATE %d but was %d\n");

    // printFENAndState(board);

    /*
            g->setSearchTime(2);
            g->search();


            while(g->m_bSearching){
                DEBUG_PRINT("Main thread sleeping\n", 0);
                sleep(1);
            }

            int m = g->getBestMove();
            g->move(m);
    */

    // g->setSearchTime(1);
    // g->search();

    // 5r1k/4Qpq1/4p3/1p1p2P1/2p2P2/1p2P3/3P4/BK6 b - -
}

bool testGame() {
    ChessBoard *board, *tmp = new ChessBoard();

    newGame();
    board = g->getBoard();

    int m, i = 0;
    boolean bMoved;
    while (!board->isEnded()) {
        DEBUG_PRINT("\nEntering loop\n", 0);

        g->setSearchTime(3);
        startThread();
        while (g->m_bSearching) {
            DEBUG_PRINT("Main thread sleeping\n", 0);
            sleep(1);
        }

        m = g->getBestMove();

        bMoved = g->move(m);
        board = g->getBoard();

        if (!bMoved) {
            DEBUG_PRINT("\nBAILING OUT - not moved\n", 0);
            break;
        }
#if DEBUG_LEVEL == 1
        char buf[512];
        board->toFEN(buf);
        DEBUG_PRINT("\nFEN\n%s\n", buf);

        if (board->getNumBoard() >= 119) {
            char buf[2048];
            board->printB(buf);
            DEBUG_PRINT(buf, 0);
        }
#endif
        DEBUG_PRINT("\n=====> %d, %d\n", board->getNumBoard(), board->getState());

        // if(i++ > 70)
        //     break;
    }

    printFENAndState(board);

    return true;
}

bool testDB() {
    g->loadDB("/home/jeroen/db.bin", 3);

    newGame();

    g->searchDB();

    return true;
}

bool testSpeed() {
    ChessBoard *board = g->getBoard();

    newGame();

    printFENAndState(board);

    // sprintf(s, "State %d = %d = %d\n", g->getBoard()->getState(), g->getBoard()->isEnded(),
    // g->getBoard()->getNumMoves()); DEBUG_PRINT(s);

    g->setSearchTime(10);
    g->search();

    return true;
}

bool testSetupMate() {
    ChessBoard *board = g->getBoard();

    board->put(ChessBoard::h7, ChessBoard::PAWN, ChessBoard::BLACK);
    board->put(ChessBoard::h8, ChessBoard::KING, ChessBoard::BLACK);
    board->put(ChessBoard::g7, ChessBoard::PAWN, ChessBoard::BLACK);
    board->put(ChessBoard::a8, ChessBoard::ROOK, ChessBoard::BLACK);
    // board->put(ChessBoard::b8, ChessBoard::ROOK, ChessBoard::BLACK);

    board->put(ChessBoard::b1, ChessBoard::ROOK, ChessBoard::WHITE);
    board->put(ChessBoard::b2, ChessBoard::ROOK, ChessBoard::WHITE);
    board->put(ChessBoard::c1, ChessBoard::KING, ChessBoard::WHITE);

    board->setCastlingsEPAnd50(0, 0, 0, 0, -1, 0);
    // board->setTurn(0);
    g->commitBoard();

    // printFENAndState(board);

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

    // DEBUG_PRINT("COL HROOK after commit = %d\n", ChessBoard::COL_HROOK);

    // printFENAndState(board);
    return true;
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

    g->commitBoard();
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
        DEBUG_PRINT(message, a, b);
        return false;
    }
    return true;
}
