#include "../common.h"

#include <gtest/gtest.h>

#include "../ChessBoard.h"
#include "../Game.h"
#include "../Move.h"
#include "chess-test.h"

namespace {

void newGame() {
    Game::getInstance()->newGameFromFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
}

void newGameDuck() {
    Game::getInstance()->newGameFromFEN("rnbqkbnr/pppppppp/8/7$/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
}

bool testSetupNewGame() {
    newGame();
    char buf[255];

    ChessBoard *board = Game::getInstance()->getBoard();
    board->toFEN(buf);

    return ChessTest::expectEqualString(
        (char *)"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", buf, (char *)"testSetupNewGame");
}

bool testInCheck() {
    return ChessTest::expectStateForFEN(
        Game::getInstance(), (char *)"5K2/8/8/8/4kr2/8/8/8 w - - 0 1", ChessBoard::CHECK, (char *)"State should equal CHECK");
}

bool testSetupMate() {
    return ChessTest::expectStateForFEN(
        Game::getInstance(), (char *)"3r1K2/8/5k2/8/8/8/8/8 w - - 0 1", ChessBoard::MATE, (char *)"State should equal MATE");
}

bool testSetupPieces() {
    return ChessTest::expectInFENIsOutFEN(
        Game::getInstance(), (char *)"r1k5/8/8/8/8/8/8/5KR1 w KQkq - 0 1", (char *)"testSetupCastle");
}

bool testStates() {
    StateForFEN scenarios[] = {{.game = Game::getInstance(),
                                .sInFEN = (char *)"2k5/8/8/8/8/8/8/4KB2 w - - 0 1",
                                .expectedState = ChessBoard::DRAW_MATERIAL,
                                .message = (char *)"Test draw material"}};
    bool bRet = true;

    for (const StateForFEN &scenario : scenarios) {
        if (!ChessTest::expectStateForFEN(scenario.game, scenario.sInFEN, scenario.expectedState, scenario.message)) {
            bRet = false;
        }
    }
    return bRet;
}

bool testDuck() {
    char buf[512];
    ChessBoard *board;
    bool ret;

    newGameDuck();

    board = Game::getInstance()->getBoard();
    board->toFEN(buf);
    ret = ChessTest::expectEqualString(
        (char *)"rnbqkbnr/pppppppp/8/7$/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", buf, (char *)"testDuck");
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
    ret = ChessTest::expectEqualString(
        (char *)"rnbqkbnr/pppppppp/4$3/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1", buf, (char *)"testDuck");
    if (!ret) {
        return false;
    }

    ret = ChessTest::expectEqualInt(board->getDuckPos(), ChessBoard::e6, (char *)"testDuck");

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

    ret = ChessTest::expectEqualInt(ChessBoard::MATE, Game::getInstance()->getBoard()->getState(), (char *)"State mate");

    return ret;
}

bool testEngine() {
    EngineInOutFEN scenarios[] = {{Game::getInstance(),
                                   (char *)"8/8/8/8/8/r2k4/8/3K4 b - - 0 1",
                                   (char *)"8/8/8/8/8/3k4/8/r2K4 w - - 1 1",
                                   1,
                                   1,
                                   false,
                                   (char *)"Mate in one"},
                                  {Game::getInstance(),
                                   (char *)"r6k/6pp/8/8/8/8/1R6/1R1K4 w - - 0 1",
                                   (char *)"rR5k/6pp/8/8/8/8/8/1R1K4 b - - 1 1",
                                   2,
                                   1,
                                   false,
                                   (char *)"Mate in two"},
                                  {Game::getInstance(),
                                   (char *)"2Q5/5pk1/8/8/1b6/1b6/r3n1P1/2K5 w - - 0 1",
                                   (char *)"2Q5/5pk1/8/8/1b6/1b6/r3n1P1/1K6 b - - 1 1",
                                   2,
                                   1,
                                   false,
                                   (char *)"In check"},
                                  {Game::getInstance(),
                                   (char *)"5r1k/1p2Qpq1/2p1p3/3p2P1/5P2/2B1P3/2K5/8 b - - 0 1",
                                   (char *)"5r1k/1p2Q1q1/2p1pp2/3p2P1/5P2/2B1P3/2K5/8 w - - 0 1",
                                   2,
                                   1,
                                   false,
                                   (char *)"Quiescent"},
                                  {Game::getInstance(),
                                   (char *)"5Q1k/6pp/8/$7/8/8/7K/8 b - - 0 1",
                                   (char *)"5Q$k/7p/8/6p1/8/8/7K/8 w - g6 0 1",
                                   2,
                                   1,
                                   true,
                                   (char *)"Duck prevent"},
                                  {Game::getInstance(),
                                   (char *)"7k/6Q1/8/8/8/8/7$/4K3 w - - 0 1",
                                   (char *)"7Q/8/8/8/8/8/7$/4K3 b - - 0 1",
                                   1,
                                   1,
                                   true,
                                   (char *)"Duck capture king in one"},
                                  {Game::getInstance(),
                                   (char *)"8/5Q1p/7k/5$1p/4Bn2/6q1/6P1/7K w - - 0 1",
                                   (char *)"8/7p/7Q/7p/4B3/3$2q1/6n1/7K b - - 0 2",
                                   3,
                                   3,
                                   true,
                                   (char *)"Duck capture king in 3"}};

    bool bRet = true;

    for (const EngineInOutFEN &scenario : scenarios) {
        if (!ChessTest::expectEngineMove(scenario)) {
            bRet = false;
        }
    }

    return bRet;
}

bool testSequence() {
    const int openingMoves[] = {Move_makeMoveFirstPawn(ChessBoard::e2, ChessBoard::e4),
                                Move_makeMoveFirstPawn(ChessBoard::e7, ChessBoard::e5),
                                Move_makeMove(ChessBoard::g1, ChessBoard::f3),
                                Move_makeMove(ChessBoard::b8, ChessBoard::c6),
                                Move_makeMoveFirstPawn(ChessBoard::d2, ChessBoard::d4),
                                Move_makeMoveHit(ChessBoard::e5, ChessBoard::d4)};
    const int mateMoves[] = {Move_makeMove(ChessBoard::f2, ChessBoard::f3),
                             Move_makeMove(ChessBoard::e7, ChessBoard::e6),
                             Move_makeMoveFirstPawn(ChessBoard::g2, ChessBoard::g4),
                             Move_setCheck(Move_makeMove(ChessBoard::d8, ChessBoard::h4))};

    SequenceInOutFEN scenarios[] = {{Game::getInstance(),
                                     (char *)"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                                     (char *)"r1bqkbnr/pppp1ppp/2n5/8/3pP3/5N2/PPP2PPP/RNBQKB1R w KQkq - 0 4",
                                     const_cast<int *>(openingMoves),
                                     6,
                                     (char *)"Opening"},
                                    {Game::getInstance(),
                                     (char *)"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                                     (char *)"rnb1kbnr/pppp1ppp/4p3/8/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3",
                                     const_cast<int *>(mateMoves),
                                     4,
                                     (char *)"To mate"}};

    bool bRet = true;

    for (const SequenceInOutFEN &scenario : scenarios) {
        if (!ChessTest::expectSequence(scenario)) {
            bRet = false;
        }
    }

    return bRet;
}

bool testEngineRunUntilState() {
    EngineInFENUntilState scenario = {.game = Game::getInstance(),
                                      .sInFEN = (char *)"8/4k3/8/8/8/4NN2/3K4/8 w - - 0 1",
                                      .expectedState = ChessBoard::DRAW_50,
                                      .depth = 3,
                                      .maxMoves = 101,
                                      .isDuck = false,
                                      .message = (char *)"Test 50 move rule"};

    return ChessTest::expectEndingStateWithinMaxMoves(scenario);
}

bool testMoves() {
    MovesForFEN scenarios[] = {{.game = Game::getInstance(),
                                .sInFEN = (char *)"8/4k3/8/8/8/4NN2/3K4/8 w - - 0 1",
                                .expectedMoveCount = 3,
                                .expectedMoves = {(char *)"Nc4", (char *)"Ng4", (char *)"Ke1"},
                                .all = false}};

    bool bRet = true;

    for (const MovesForFEN &scenario : scenarios) {
        if (!ChessTest::expectMovesForFEN(scenario)) {
            bRet = false;
        }
    }

    return bRet;
}

class GameTest : public ::testing::Test {
   protected:
    void SetUp() override {
        Game::getInstance()->reset();
    }
};

TEST_F(GameTest, SetupNewGame) {
    EXPECT_TRUE(testSetupNewGame());
}

TEST_F(GameTest, DetectCheck) {
    EXPECT_TRUE(testInCheck());
}

TEST_F(GameTest, DetectMate) {
    EXPECT_TRUE(testSetupMate());
}

TEST_F(GameTest, SetupPieces) {
    EXPECT_TRUE(testSetupPieces());
}

TEST_F(GameTest, EvaluateStates) {
    EXPECT_TRUE(testStates());
}

TEST_F(GameTest, DuckMode) {
    EXPECT_TRUE(testDuck());
}

TEST_F(GameTest, EngineScenarios) {
    EXPECT_TRUE(testEngine());
}

TEST_F(GameTest, SequenceScenarios) {
    EXPECT_TRUE(testSequence());
}

TEST_F(GameTest, EngineUntilExpectedState) {
    EXPECT_TRUE(testEngineRunUntilState());
}

TEST_F(GameTest, MovesForPosition) {
    EXPECT_TRUE(testMoves());
}

}  // namespace
