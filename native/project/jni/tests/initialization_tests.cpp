#include <array>
#include <atomic>
#include <future>
#include <thread>

#include <gtest/gtest.h>

#include "../ChessBoard.h"
#include "../Game.h"

namespace {

constexpr const char* START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
constexpr BITBOARD START_HASH = -8567268772865283918LL;

// CTest runs each discovered test in a fresh process, including first initialization.
TEST(InitializationTest, StandaloneBoard) {
    ChessBoard board, workspace;
    ASSERT_TRUE(board.parseFEN(START_FEN));
    board.commitBoard();
    board.calcState(&workspace);
    EXPECT_EQ(20, board.getNumMoves());
    EXPECT_EQ(START_HASH, board.getHashKey());
}

TEST(InitializationTest, StandaloneGame) {
    Game game;
    ASSERT_TRUE(game.newGameFromFEN(START_FEN));
    EXPECT_EQ(20, game.getBoard()->getNumMoves());
    EXPECT_EQ(START_HASH, game.getBoard()->getHashKey());
    EXPECT_TRUE(game.requestMove(ChessBoard::e2, ChessBoard::e4));
}

TEST(InitializationTest, RepeatedInitializationPreservesBoard) {
    Game game;
    ASSERT_TRUE(game.newGameFromFEN(START_FEN));
    ASSERT_TRUE(game.requestMove(ChessBoard::e2, ChessBoard::e4));
    const BITBOARD hash = game.getBoard()->getHashKey();
    char before[256], after[256];
    game.getBoard()->toFEN(before);
    ChessBoard::initStatics();
    ChessBoard::initStatics();
    game.getBoard()->toFEN(after);
    EXPECT_EQ(hash, game.getBoard()->getHashKey());
    EXPECT_STREQ(before, after);
    EXPECT_TRUE(game.requestMove(ChessBoard::e7, ChessBoard::e5));
}

TEST(InitializationTest, ConcurrentFirstConstruction) {
    constexpr int count = 8;
    std::array<std::thread, count> workers;
    std::array<bool, count> valid{};
    std::atomic_int ready{0};
    std::promise<void> start;
    std::shared_future<void> go = start.get_future().share();
    for (int i = 0; i < count; ++i) {
        workers[i] = std::thread([&, i] {
            ++ready;
            go.wait();
            Game game;
            valid[i] = game.newGameFromFEN(START_FEN) &&
                       game.getBoard()->getNumMoves() == 20 &&
                       game.getBoard()->getHashKey() == START_HASH &&
                       game.requestMove(ChessBoard::e2, ChessBoard::e4);
        });
    }
    while (ready.load() != count) {
        std::this_thread::yield();
    }
    start.set_value();
    for (auto& worker : workers) {
        worker.join();
    }
    for (int i = 0; i < count; ++i) {
        EXPECT_TRUE(valid[i]) << "Worker " << i;
    }
}

}  // namespace
