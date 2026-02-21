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

void testSetupNewGame() {
    newGame();
    char buf[255];

    ChessBoard *board = Game::getInstance()->getBoard();
    board->toFEN(buf);

    SCOPED_TRACE("testSetupNewGame");
    EXPECT_STREQ("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", buf);
}

void testInCheck() {
    EXPECT_TRUE(ChessTest::expectStateForFEN(
        Game::getInstance(), "5K2/8/8/8/4kr2/8/8/8 w - - 0 1", ChessBoard::CHECK, "State should equal CHECK"));
}

void testSetupMate() {
    EXPECT_TRUE(ChessTest::expectStateForFEN(
        Game::getInstance(), "3r1K2/8/5k2/8/8/8/8/8 w - - 0 1", ChessBoard::MATE, "State should equal MATE"));
}

void testSetupPieces() {
    EXPECT_TRUE(ChessTest::expectInFENIsOutFEN(
        Game::getInstance(), "r1k5/8/8/8/8/8/8/5KR1 w KQkq - 0 1", "testSetupCastle"));
}

void testStates() {
    StateForFEN scenarios[] = {{.game = Game::getInstance(),
                                .sInFEN = "2k5/8/8/8/8/8/8/4KB2 w - - 0 1",
                                .expectedState = ChessBoard::DRAW_MATERIAL,
                                .message = "Test draw material"}};

    for (const StateForFEN &scenario : scenarios) {
        SCOPED_TRACE(scenario.message);
        EXPECT_TRUE(ChessTest::expectStateForFEN(scenario.game, scenario.sInFEN, scenario.expectedState, scenario.message));
    }
}

void testDuck() {
    char buf[512];
    ChessBoard *board;

    newGameDuck();

    board = Game::getInstance()->getBoard();
    board->toFEN(buf);
    SCOPED_TRACE("testDuck");
    ASSERT_STREQ("rnbqkbnr/pppppppp/8/7$/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", buf);

    ASSERT_TRUE(Game::getInstance()->requestMove(ChessBoard::e2, ChessBoard::e4)) << "no e2-e4";
    ASSERT_TRUE(Game::getInstance()->requestDuckMove(ChessBoard::e6)) << "no request duck move 1";
    EXPECT_FALSE(Game::getInstance()->requestMove(ChessBoard::e7, ChessBoard::e5)) << "move e7-e5 should not";
    EXPECT_FALSE(Game::getInstance()->requestMove(ChessBoard::d7, ChessBoard::e6)) << "move d7-e6 should not";

    board = Game::getInstance()->getBoard();
    board->toFEN(buf);
    ASSERT_STREQ("rnbqkbnr/pppppppp/4$3/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1", buf);
    const int expectedDuckPos = ChessBoard::e6;
    EXPECT_EQ(board->getDuckPos(), expectedDuckPos);

    ASSERT_TRUE(Game::getInstance()->requestMove(ChessBoard::d7, ChessBoard::d5)) << "no d7-d5";
    ASSERT_TRUE(Game::getInstance()->requestDuckMove(ChessBoard::e3)) << "no duck e3";
    ASSERT_TRUE(Game::getInstance()->requestMove(ChessBoard::f1, ChessBoard::b5)) << "no f1-b5";
    ASSERT_TRUE(Game::getInstance()->requestDuckMove(ChessBoard::f3)) << "no duck f3";
    ASSERT_TRUE(Game::getInstance()->requestMove(ChessBoard::f7, ChessBoard::f6)) << "no f7-f6";
    ASSERT_TRUE(Game::getInstance()->requestDuckMove(ChessBoard::f4)) << "no duck f4 x";
    ASSERT_TRUE(Game::getInstance()->requestMove(ChessBoard::b5, ChessBoard::e8)) << "no b5-e8";
    const int expectedMateState = ChessBoard::MATE;
    EXPECT_EQ(expectedMateState, Game::getInstance()->getBoard()->getState()) << "State mate";
}

void testEngine() {
    EngineInOutFEN scenarios[] = {{Game::getInstance(),
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

    for (const EngineInOutFEN &scenario : scenarios) {
        SCOPED_TRACE(scenario.message);
        EXPECT_TRUE(ChessTest::expectEngineMove(scenario));
    }
}

void testSequence() {
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
                                     "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                                     "r1bqkbnr/pppp1ppp/2n5/8/3pP3/5N2/PPP2PPP/RNBQKB1R w KQkq - 0 4",
                                     const_cast<int *>(openingMoves),
                                     6,
                                     "Opening"},
                                    {Game::getInstance(),
                                     "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                                     "rnb1kbnr/pppp1ppp/4p3/8/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3",
                                     const_cast<int *>(mateMoves),
                                     4,
                                     "To mate"}};

    for (const SequenceInOutFEN &scenario : scenarios) {
        SCOPED_TRACE(scenario.message);
        EXPECT_TRUE(ChessTest::expectSequence(scenario));
    }
}

void testEngineRunUntilState() {
    EngineInFENUntilState scenario = {.game = Game::getInstance(),
                                      .sInFEN = "8/4k3/8/8/8/4NN2/3K4/8 w - - 0 1",
                                      .expectedState = ChessBoard::DRAW_50,
                                      .depth = 3,
                                      .maxMoves = 101,
                                      .isDuck = false,
                                      .message = "Test 50 move rule"};

    EXPECT_TRUE(ChessTest::expectEndingStateWithinMaxMoves(scenario));
}

void testMoves() {
    MovesForFEN scenarios[] = {{.game = Game::getInstance(),
                                .sInFEN = "8/4k3/8/8/8/4NN2/3K4/8 w - - 0 1",
                                .expectedMoveCount = 3,
                                .expectedMoves = {"Nc4", "Ng4", "Ke1"},
                                .all = false}};

    for (const MovesForFEN &scenario : scenarios) {
        EXPECT_TRUE(ChessTest::expectMovesForFEN(scenario));
    }
}

class GameTest : public ::testing::Test {
   protected:
    void SetUp() override {
        Game::getInstance()->reset();
    }
};

TEST_F(GameTest, SetupNewGame) {
    testSetupNewGame();
}

TEST_F(GameTest, DetectCheck) {
    testInCheck();
}

TEST_F(GameTest, DetectMate) {
    testSetupMate();
}

TEST_F(GameTest, SetupPieces) {
    testSetupPieces();
}

TEST_F(GameTest, EvaluateStates) {
    testStates();
}

TEST_F(GameTest, DuckMode) {
    testDuck();
}

TEST_F(GameTest, EngineScenarios) {
    testEngine();
}

TEST_F(GameTest, SequenceScenarios) {
    testSequence();
}

TEST_F(GameTest, EngineUntilExpectedState) {
    testEngineRunUntilState();
}

TEST_F(GameTest, MovesForPosition) {
    testMoves();
}

}  // namespace
