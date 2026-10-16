#include <chrono>
#include <thread>

#include <gtest/gtest.h>

#include "../SearchController.h"

namespace {
constexpr const char* START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

bool waitForProgress(SearchController& controller) {
    const auto deadline = std::chrono::steady_clock::now() + std::chrono::seconds(5);
    while (controller.result().depth == 0 && !controller.done() && std::chrono::steady_clock::now() < deadline) {
        std::this_thread::yield();
    }
    return controller.result().depth > 0;
}

TEST(SearchControllerTest, FreshAndResetGamesHaveNoMovesOrHistory) {
    Game game;
    EXPECT_EQ(nullptr, game.getBoard()->undoMove());
    EXPECT_EQ(0, game.getBoard()->getNumMoves());
    ASSERT_TRUE(game.newGameFromFEN(START));
    ASSERT_TRUE(game.requestMove(ChessBoard::e2, ChessBoard::e4));
    game.reset();
    EXPECT_EQ(nullptr, game.getBoard()->undoMove());
    EXPECT_EQ(0, game.getBoard()->getNumMoves());
}

TEST(SearchControllerTest, SearchWrapperUsesSuppliedInstance) {
    Game game;
    ASSERT_TRUE(game.newGameFromFEN(START));
    game.setSearchLimit(1);
    Game::search_wrapper(&game);
    EXPECT_NE(0, game.getBestMove());
}

TEST(SearchControllerTest, CloneOwnsHistoryAndPreservesRepetition) {
    Game clone;
    {
        Game live;
        ASSERT_TRUE(live.newGameFromFEN(START));
        const int moves[] = {
            Move_makeMove(ChessBoard::g1, ChessBoard::f3),
            Move_makeMove(ChessBoard::g8, ChessBoard::f6),
            Move_makeMove(ChessBoard::f3, ChessBoard::g1),
            Move_makeMove(ChessBoard::f6, ChessBoard::g8),
            Move_makeMove(ChessBoard::g1, ChessBoard::f3),
            Move_makeMove(ChessBoard::g8, ChessBoard::f6),
            Move_makeMove(ChessBoard::f3, ChessBoard::g1)
        };
        for (int move : moves) ASSERT_TRUE(live.move(move));
        clone.copyPositionFrom(live);
        EXPECT_NE(clone.getBoard(), live.getBoard());
        EXPECT_NE(clone.getBoard()->undoMove(), live.getBoard()->undoMove());
        live.undo();
        live.reset();
    }
    ASSERT_TRUE(clone.move(Move_makeMove(ChessBoard::f6, ChessBoard::g8)));
    EXPECT_EQ(static_cast<int>(ChessBoard::DRAW_REPEAT), clone.getBoard()->getState());
    for (int i = 0; i < 8; ++i) clone.undo();
    char fen[256];
    clone.getBoard()->toFEN(fen);
    EXPECT_STREQ(START, fen);
}

TEST(SearchControllerTest, PreparedSearchSurvivesSourceDestruction) {
    SearchController controller;
    uint64_t id;
    {
        Game live;
        ASSERT_TRUE(live.newGameFromFEN(START));
        id = controller.prepare(live, 1, 2, 0, true);
    }
    ASSERT_NE(0u, id);
    controller.run(id);
    const auto result = controller.result(id);
    EXPECT_TRUE(controller.done());
    EXPECT_NE(0, result.move);
    Game validator;
    ASSERT_TRUE(validator.newGameFromFEN(START));
    EXPECT_TRUE(validator.move(result.move));
}

TEST(SearchControllerTest, LiveNavigationDuringSearchDoesNotChangeSnapshot) {
    Game live;
    ASSERT_TRUE(live.newGameFromFEN(START));
    ASSERT_TRUE(live.requestMove(ChessBoard::e2, ChessBoard::e4));
    Game validator;
    validator.copyPositionFrom(live);
    SearchController controller;
    const auto id = controller.prepare(live, 2, 0, 30000, true);
    ASSERT_NE(0u, id);
    std::thread search([&] { controller.run(id); });
    const bool progressed = waitForProgress(controller);
    const bool running = !controller.done();
    const auto rejected = controller.prepare(live, 2, 1, 0, true);
    live.undo();
    live.reset();
    EXPECT_TRUE(live.newGameFromFEN("4k3/8/8/8/8/8/8/1R2K1R1 w KQ - 0 1"));
    controller.interrupt();
    search.join();
    EXPECT_TRUE(progressed);
    EXPECT_TRUE(running);
    EXPECT_EQ(0u, rejected);
    EXPECT_TRUE(controller.done());
    const auto result = controller.result(id);
    ASSERT_NE(0, result.move);
    EXPECT_TRUE(validator.move(result.move));
    char fen[256];
    live.getBoard()->toFEN(fen);
    EXPECT_STREQ("4k3/8/8/8/8/8/8/1R2K1R1 w KQ - 0 1", fen);
}

TEST(SearchControllerTest, InterruptBeforeRunAndRejectStaleResults) {
    Game live;
    ASSERT_TRUE(live.newGameFromFEN(START));
    SearchController controller;
    const auto first = controller.prepare(live, 10, 3, 0, true);
    ASSERT_NE(0u, first);
    EXPECT_EQ(0u, controller.prepare(live, 10, 1, 0, true));
    EXPECT_TRUE(controller.matches(first, 10));
    EXPECT_FALSE(controller.matches(first, 11));
    controller.interrupt();
    controller.run(first);
    EXPECT_TRUE(controller.done());
    EXPECT_EQ(0, controller.result(first).evaluations);
    const auto second = controller.prepare(live, 11, 1, 0, true);
    ASSERT_NE(0u, second);
    EXPECT_FALSE(controller.matches(first, 11));
    controller.run(first);
    EXPECT_FALSE(controller.done());
    controller.interrupt(first);
    controller.run(second);
    EXPECT_NE(0, controller.result(second).move);
    EXPECT_EQ(0, controller.result(first).move);
}

TEST(SearchControllerTest, CancelWaitsForWorkerAndAllowsReuse) {
    Game live;
    ASSERT_TRUE(live.newGameFromFEN(START));
    SearchController controller;
    auto id = controller.prepare(live, 1, 0, 30000, true);
    std::thread search([&] { controller.run(id); });
    const bool progressed = waitForProgress(controller);
    controller.cancelAndWait();
    search.join();
    EXPECT_TRUE(progressed);
    EXPECT_TRUE(controller.done());
    EXPECT_FALSE(controller.matches(id, 1));
    id = controller.prepare(live, 1, 1, 0, true);
    ASSERT_NE(0u, id);
    controller.run(id);
    EXPECT_NE(0, controller.result(id).move);
}

TEST(SearchControllerTest, CloneRetainsDuckPositionAndCastlingColumns) {
    Game live, clone;
    ASSERT_TRUE(live.newGameFromFEN("4k3/8/8/3$4/8/8/8/1R2K1R1 w KQ - 0 1"));
    clone.copyPositionFrom(live);
    live.reset();
    EXPECT_EQ(static_cast<int>(ChessBoard::d5), clone.getBoard()->getDuckPos());
    EXPECT_TRUE(clone.move(Move_makeMoveOO(ChessBoard::e1, ChessBoard::g1)));
    EXPECT_EQ(static_cast<int>(ChessBoard::ROOK), clone.getBoard()->pieceAt(ChessBoard::WHITE, ChessBoard::f1));
}

TEST(SearchControllerTest, HistorySearchPreservesRepetitionWithoutChangingLiveGame) {
    Game live;
    ASSERT_TRUE(live.newGameFromFEN(START));
    ASSERT_TRUE(live.requestMove(ChessBoard::e2, ChessBoard::e4));
    char before[256], after[256];
    live.getBoard()->toFEN(before);
    std::vector<int> moves;
    for (int i = 0; i < 2; ++i) {
        moves.push_back(Move_makeMove(ChessBoard::g1, ChessBoard::f3));
        moves.push_back(Move_makeMove(ChessBoard::g8, ChessBoard::f6));
        moves.push_back(Move_makeMove(ChessBoard::f3, ChessBoard::g1));
        moves.push_back(Move_makeMove(ChessBoard::f6, ChessBoard::g8));
    }
    SearchController controller;
    auto id = controller.prepareHistory(START, moves, 100, true);
    ASSERT_NE(0u, id);
    EXPECT_FALSE(controller.matches(id, 0));
    controller.run(id);
    auto result = controller.result(id);
    EXPECT_TRUE(result.hasEvaluation);
    EXPECT_EQ(0, result.value);
    EXPECT_EQ(0, result.move);
    live.getBoard()->toFEN(after);
    EXPECT_STREQ(before, after);
    EXPECT_TRUE(live.getBoard()->undoMove() != nullptr);

    // The same placement without the repeated path is still playable.
    id = controller.prepareHistory(START, {}, 100, true);
    ASSERT_NE(0u, id);
    controller.run(id);
    EXPECT_NE(0, controller.result(id).move);
    EXPECT_TRUE(controller.result(id).hasEvaluation);
    EXPECT_GT(controller.result(id).completedDepth, 0);
}

TEST(SearchControllerTest, HistoryReservationRejectsReplacementAndSupportsCancellation) {
    SearchController controller;
    auto id = controller.prepareHistory(START, {}, 100, true);
    ASSERT_NE(0u, id);
    EXPECT_EQ(0u, controller.prepareHistory(START, {}, 100, true));
    controller.interrupt(id);
    controller.run(id);
    EXPECT_TRUE(controller.done());
    EXPECT_FALSE(controller.result(id).hasEvaluation);
    EXPECT_EQ(0u, controller.prepareHistory(START,
        {Move_makeMove(ChessBoard::e2, ChessBoard::e5)}, 100, true));
    id = controller.prepareHistory(START, {}, 100, true);
    ASSERT_NE(0u, id);
    controller.run(id);
    EXPECT_TRUE(controller.result(id).hasEvaluation);
}

TEST(SearchControllerTest, TerminalSearchPublishesMateRatherThanAnUncomputedZero) {
    SearchController controller;
    auto id = controller.prepareHistory("7k/6Q1/5K2/8/8/8/8/8 b - - 0 1", {}, 100, true);
    ASSERT_NE(0u, id);
    controller.run(id);
    EXPECT_TRUE(controller.result(id).hasEvaluation);
    EXPECT_EQ(-ChessBoard::VALUATION_MATE, controller.result(id).value);
    EXPECT_EQ(0, controller.result(id).move);
}
}  // namespace
