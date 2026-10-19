#include <chrono>
#include <cstdio>
#include <cstdlib>

#include "Game.h"

// Runs a timed search from a FEN and prints the evaluation count and N/s, in
// the same format as the Rust core's bench_search example.
int main(int argc, char** argv) {
    const char* fen = argc > 1 ? argv[1] : "r1bq1rk1/ppp2ppp/2np1n2/2b1p3/2B1P3/2NP1N2/PPP2PPP/R1BQ1RK1 w - - 0 1";
    // The limit is milliseconds ("10000") or a fixed depth ("d5").
    const char* limit = argc > 2 ? argv[2] : "10000";
    bool fixedDepth = limit[0] == 'd';
    int amount = std::atoi(fixedDepth ? limit + 1 : limit);
    int quiescent = argc > 3 ? std::atoi(argv[3]) : 1;

    Game game;
    if (!game.newGameFromFEN(fen)) {
        std::fprintf(stderr, "invalid FEN\n");
        return 2;
    }
    game.setQuiescentOn(quiescent != 0);
    if (fixedDepth) {
        game.setSearchLimit(amount);
    } else {
        game.setSearchTime(amount);
    }

    auto start = std::chrono::steady_clock::now();
    game.search();
    double seconds = std::chrono::duration<double>(std::chrono::steady_clock::now() - start).count();

    long evals = game.getEvalCount();
    std::printf("engine=cpp depth=%d value=%d nodes=%ld seconds=%.3f N/s=%.0f\n", game.completedDepth(),
                game.getBestValue(), evals, seconds, seconds > 0 ? evals / seconds : 0.0);
    return 0;
}
