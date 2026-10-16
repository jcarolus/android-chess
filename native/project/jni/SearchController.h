#pragma once

#include <array>
#include <condition_variable>
#include <cstdint>
#include <mutex>

#include "Game.h"

struct SearchResult {
    int move = 0;
    int duckMove = -1;
    int value = 0;
    int depth = 0;
    int evaluations = 0;
    std::array<int, 20> moves{};
    std::array<int, 20> duckMoves;

    SearchResult() { duckMoves.fill(-1); }
};

// The caller serializes live-board access while preparing a snapshot. Running,
// interruption and result reads are independent of the live board.
class SearchController {
   public:
    ~SearchController();
    uint64_t prepare(const Game& source, uint64_t revision, int depth, int millis, bool quiescent);
    void run(uint64_t id);
    void interrupt(uint64_t id = 0);
    void cancelAndWait();
    bool done() const;
    bool matches(uint64_t id, uint64_t revision) const;
    SearchResult result(uint64_t id = 0) const;

   private:
    enum class State { Idle, Prepared, Running, Finished };
    void publish();

    mutable std::mutex m_mutex;
    std::condition_variable m_finished;
    Game m_worker;
    State m_state = State::Idle;
    uint64_t m_id = 0;
    uint64_t m_revision = 0;
    bool m_interrupted = false;
    SearchResult m_result;
};
