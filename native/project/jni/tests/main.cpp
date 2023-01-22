#include "../common.h"

#include <unistd.h>
#include <functional>

#include "../ChessBoard.h"
#include "../Game.h"
#include "../Move.h"
#include "chess-test.h"

void miniTest();
void startThread();
bool testGame();
void speedTest();
bool testDB();
bool testSetupNewGame();
bool testSetupMate();
bool testInCheck();
bool testInSelfCheck();
bool testSetupPieces();
bool testGenmoves();
bool testDuck();
bool testEngine();
bool testSequence();
bool testNonSequence();
bool testEngineRunUntilState();
void newGame();
void newGameDuck();

int main(int argc, char **argv) {
    DEBUG_PRINT("\n\n=== START TESTS == \n", 0);

    TestFunction tests[] = {testSetupNewGame,
                            testSetupMate,
                            testInCheck,
                            testInSelfCheck,
                            testSetupPieces,
                            testDB,
                            testDuck,
                            testEngine,
                            testSequence,
                            testEngineRunUntilState/*,
                            testNonSequence*/};

    
    // EngineInOutFEN testScenario = {Game::getInstance(),
    //                                //    "8/7Q/7k/8/8/6$1/8/7K b - - 0 1",
    //                                //    "8/7k/8/8/8/5$2/8/7K w - - 0 1",
    //                                "8/7Q/7k/6$p/4Bn2/6q1/6P1/7K b - - 0 1",
    //                                "8/7p/7Q/7p/4Bn2/5$2/6q1/7K w - - 0 1",
    //                                2,
    //                                1,
    //                                true,
    //                                "TEST SCENARIO"};

    // ChessTest::expectEngineMove(testScenario);
    // return 0;

    int testFail = 0, testSuccess = 0;
    for (int i = 0; i < sizeof(tests) / sizeof(TestFunction); i++) {
        //Game::deleteInstance(); // guaranteed clean
        Game::getInstance()->reset(); // reuse instance as in the app

        DEBUG_PRINT("\n* Test %d\n", i);
        if (tests[i]()) {
            testSuccess++;
        } else {
            DEBUG_PRINT("\n=====> Test %d FAILED\n", i);
            testFail++;
        }
    }

    DEBUG_PRINT("\n\n=== DONE === SUCCESS: [%d] FAIL: [%d]\n", testSuccess, testFail);
    return 0;
}

bool testSetupNewGame() {
    newGame();
    char buf[255];

    ChessBoard *board = Game::getInstance()->getBoard();
    board->toFEN(buf);

    return ChessTest::expectEqualString("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                                        buf,
                                        "testSetupNewGame");
}

bool testInCheck() {
    return ChessTest::expectStateForFEN(Game::getInstance(),
                                        "5K2/8/8/8/4kr2/8/8/8 w - - 0 1",
                                        ChessBoard::CHECK,
                                        "State should equal CHECK");
}

bool testInSelfCheck() {
    return true;
}

bool testSetupMate() {
    return ChessTest::expectStateForFEN(Game::getInstance(),
                                        "3r1K2/8/5k2/8/8/8/8/8 w - - 0 1",
                                        ChessBoard::MATE,
                                        "State should equal MATE");
}

bool testDB() {
    Game::getInstance()->loadDB("db.bin", 3);

    newGame();

    int move = Game::getInstance()->searchDB();
    if (move == 0) {
        return false;
    }
    // ChessTest::printMove(move);

    Game::getInstance()->move(move);

    move = Game::getInstance()->searchDB();

    if (move == 0) {
        return false;
    }

    return true;
}

bool testSetupPieces() {
    return ChessTest::expectInFENIsOutFEN(Game::getInstance(), "r1k5/8/8/8/8/8/8/5KR1 w KQkq - 0 1", "testSetupCastle");
}

bool testGenmoves() {
    ChessBoard *board = Game::getInstance()->getBoard();
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

    board = Game::getInstance()->getBoard();
    board->toFEN(buf);
    ret = ChessTest::expectEqualString("rnbqkbnr/pppppppp/8/7$/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", buf, "testDuck");
    if (!ret) {
        return false;
    }

    ret = Game::getInstance()->requestMove(ChessBoard::e2, ChessBoard::e4);
    ret = Game::getInstance()->requestDuckMove(ChessBoard::e6);
    if (!ret) {
        DEBUG_PRINT("no request duck move 1", 0);
        return false;
    }
    ret = Game::getInstance()->requestMove(ChessBoard::e7, ChessBoard::e5);
    if (ret) {
        DEBUG_PRINT("move e7-e5 should not", 0);
        return false;
    }

    ret = Game::getInstance()->requestMove(ChessBoard::d7, ChessBoard::e6);
    if (ret) {
        DEBUG_PRINT("move d7-e6 should not", 0);
        return false;
    }

    board = Game::getInstance()->getBoard();
    board->toFEN(buf);
    ret =
        ChessTest::expectEqualString("rnbqkbnr/pppppppp/4$3/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1", buf, "testDuck");
    if (!ret) {
        return false;
    }

    ret = ChessTest::expectEqualInt(board->getDuckPos(), ChessBoard::e6, "testDuck");

    // board->printB(buf);
    // DEBUG_PRINT("\n%s\n", buf);
    ret = Game::getInstance()->requestMove(ChessBoard::d7, ChessBoard::d5);
    if (!ret) {
        DEBUG_PRINT("no d7-d5", 0);
        return false;
    }

    ret = Game::getInstance()->requestDuckMove(ChessBoard::e3);
    if (!ret) {
        DEBUG_PRINT("no duck e3", 0);
        return false;
    }

    ret = Game::getInstance()->requestMove(ChessBoard::f1, ChessBoard::b5);
    if (!ret) {
        DEBUG_PRINT("no f1-b5", 0);
        return false;
    }

    ret = Game::getInstance()->requestDuckMove(ChessBoard::f3);
    if (!ret) {
        DEBUG_PRINT("no duck f3", 0);
        return false;
    }

    ret = Game::getInstance()->requestMove(ChessBoard::f7, ChessBoard::f6);
    if (!ret) {
        DEBUG_PRINT("no f7-f6", 0);
        return false;
    }

    ret = Game::getInstance()->requestDuckMove(ChessBoard::f4);
    if (!ret) {
        DEBUG_PRINT("no duck f4 x", 0);
        return false;
    }

    ret = Game::getInstance()->requestMove(ChessBoard::b5, ChessBoard::e8);
    if (!ret) {
        DEBUG_PRINT("no b5-e8", 0);
        return false;
    }

    ret = ChessTest::expectEqualInt(ChessBoard::MATE, Game::getInstance()->getBoard()->getState(), "State mate");

    return ret;
}

bool testEngine() {
    EngineInOutFEN scenarios[7] = {{Game::getInstance(),
                                    "8/8/8/8/8/r2k4/8/3K4 b - - 0 1",
                                    "8/8/8/8/8/3k4/8/r2K4 w - - 1 1",
                                    1,
                                    1,
                                    false,
                                    "Mate in one"},
                                   {Game::getInstance(),
                                    "r6k/6pp/8/8/8/8/1R6/1R1K4 w - - 0 1",
                                    "rR5k/6pp/8/8/8/8/8/1R1K4 b - - 1 1",
                                    2,
                                    1,
                                    false,
                                    "Mate in two"},
                                   {Game::getInstance(),
                                    "2Q5/5pk1/8/8/1b6/1b6/r3n1P1/2K5 w - - 0 1",
                                    "2Q5/5pk1/8/8/1b6/1b6/r3n1P1/1K6 b - - 1 1",
                                    2,
                                    1,
                                    false,
                                    "In check"},
                                   {Game::getInstance(),
                                    "5r1k/1p2Qpq1/2p1p3/3p2P1/5P2/2B1P3/2K5/8 b - - 0 1",
                                    "5r1k/1p2Q1q1/2p1pp2/3p2P1/5P2/2B1P3/2K5/8 w - - 0 1",
                                    2,
                                    1,
                                    false,
                                    "Quiescent"},
                                   {Game::getInstance(),
                                    "5Q1k/6pp/8/$7/8/8/7K/8 b - - 0 1",
                                    "5Q$k/7p/8/6p1/8/8/7K/8 w - g6 0 1",
                                    2,
                                    1,
                                    true,
                                    "Duck prevent"},
                                   {Game::getInstance(),
                                    "7k/6Q1/8/8/8/8/7$/4K3 w - - 0 1",
                                    "7Q/8/8/8/8/8/7$/4K3 b - - 0 1",
                                    1,
                                    1,
                                    true,
                                    "Duck capture king in one"},
                                   {Game::getInstance(),
                                    "8/5Q1p/7k/5$1p/4Bn2/6q1/6P1/7K w - - 0 1",
                                    "8/7p/7Q/7p/4B3/3$2q1/6n1/7K b - - 0 2",
                                    3,
                                    3,
                                    true,
                                    "Duck capture king in 3"}};

    bool bRet = true;

    for (int i = 0; i < 7; i++) {
        if (!ChessTest::expectEngineMove(scenarios[i])) {
            bRet = false;
        }
    }

    return bRet;
}

bool testSequence() {
    SequenceInOutFEN scenarios[2] = {
        {Game::getInstance(),
         "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
         "r1bqkbnr/pppp1ppp/2n5/8/3pP3/5N2/PPP2PPP/RNBQKB1R w KQkq - 0 4",
         (int[]){Move_makeMoveFirstPawn(ChessBoard::e2, ChessBoard::e4),
                 Move_makeMoveFirstPawn(ChessBoard::e7, ChessBoard::e5),
                 Move_makeMove(ChessBoard::g1, ChessBoard::f3),
                 Move_makeMove(ChessBoard::b8, ChessBoard::c6),
                 Move_makeMoveFirstPawn(ChessBoard::d2, ChessBoard::d4),
                 Move_makeMoveHit(ChessBoard::e5, ChessBoard::d4)},
         6,
         "Opening"},
        {Game::getInstance(),
         "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
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
        if (!ChessTest::expectSequence(scenarios[i])) {
            bRet = false;
        }
    }

    return bRet;
}

bool testNonSequence() {
    NonSequenceInFEN scenarios[1] = {{Game::getInstance(),
                                      "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                                      (int[]){Move_makeMoveFirstPawn(ChessBoard::e2, ChessBoard::e4),
                                              Move_makeMoveFirstPawn(ChessBoard::e7, ChessBoard::e5),
                                              Move_makeMove(ChessBoard::g1, ChessBoard::f3),
                                              Move_makeMove(ChessBoard::b8, ChessBoard::c6),
                                              Move_makeMoveFirstPawn(ChessBoard::d2, ChessBoard::d4),
                                              Move_makeMoveHit(ChessBoard::e5, ChessBoard::d4)},
                                      6,
                                      "Opening"}};

    bool bRet = true;

    for (int i = 0; i < 1; i++) {
        if (!ChessTest::expectNonSequence(scenarios[i])) {
            bRet = false;
        }
    }

    return bRet;
}

bool testEngineRunUntilState() {
    EngineInFENUntilState scenario = {
        .game = Game::getInstance(),
        .sInFEN = "8/4k3/8/8/8/4NN2/3K4/8 w - - 0 1",
        .expectedState = ChessBoard::DRAW_50,
        .depth = 3,
        .maxMoves = 101,
        .isDuck = false,
        .message = "Test 50 move rule"
    };
    
    return ChessTest::expectEndingStateWithinMaxMoves(scenario);

    //DEBUG_PRINT("Performed moves %d :: %d\n", scenario.game->getBoard()->getNoHitCount(),  scenario.game->getBoard()->getState());
}

void speedTest() {
    newGame();
    Game::getInstance()->setSearchTime(10);
    Game::getInstance()->search();
}

void newGame() {
    Game::getInstance()->newGameFromFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
}

void newGameDuck() {
    Game::getInstance()->newGameFromFEN("rnbqkbnr/pppppppp/8/7$/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
}
