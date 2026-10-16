#include "SearchController.h"

SearchController::~SearchController() {
    cancelAndWait();
}

uint64_t SearchController::prepare(const Game& source, uint64_t revision, int depth, int millis, bool quiescent) {
    std::lock_guard<std::mutex> lock(m_mutex);
    if (m_state == State::Prepared || m_state == State::Running || (depth <= 0 && millis <= 0)) {
        return 0;
    }
    m_worker.copyPositionFrom(source);
    m_worker.setQuiescentOn(quiescent);
    if (depth > 0) {
        m_worker.setSearchLimit(depth);
    } else {
        m_worker.setSearchTime(millis);
    }
    m_result = SearchResult();
    m_liveSource = true;
    m_revision = revision;
    m_interrupted = false;
    m_state = State::Prepared;
    return ++m_id;
}

uint64_t SearchController::prepareHistory(const char* initialFen, const std::vector<int>& moves,
                                          int millis, bool quiescent) {
    std::lock_guard<std::mutex> lock(m_mutex);
    if (m_state == State::Prepared || m_state == State::Running) return 0;
    if (millis <= 0) return 0;
    // Failed replay must not leave a previous token associated with a changed worker.
    m_state = State::Idle;
    m_result = SearchResult();
    if (!m_worker.newGameFromFEN(initialFen)) return 0;
    for (int move : moves) {
        if (!m_worker.move(move)) return 0;
    }
    m_worker.setQuiescentOn(quiescent);
    m_worker.setSearchTime(millis);
    m_liveSource = false;
    m_interrupted = false;
    m_state = State::Prepared;
    return ++m_id;
}

void SearchController::run(uint64_t id) {
    {
        std::lock_guard<std::mutex> lock(m_mutex);
        if (id != m_id || m_state != State::Prepared) return;
        if (m_interrupted) {
            m_state = State::Finished;
            return;
        }
        m_state = State::Running;
    }
    struct Completion {
        SearchController& controller;
        ~Completion() {
            std::lock_guard<std::mutex> lock(controller.m_mutex);
            controller.m_state = State::Finished;
            controller.m_finished.notify_all();
        }
    } completion{*this};
    m_worker.search([this] { publish(); });
    publish();
}

void SearchController::publish() {
    std::lock_guard<std::mutex> lock(m_mutex);
    // begin() clears the worker's interrupt flag. Reapply an interruption that
    // arrived between reservation and search startup before exploring any moves.
    if (m_interrupted) m_worker.interruptSearch();
    m_result.hasEvaluation = m_worker.hasEvaluation();
    m_result.completedDepth = m_worker.completedDepth();
    m_result.move = m_worker.getBestMove();
    m_result.duckMove = m_worker.getBestDuckMove();
    m_result.value = m_worker.getBestValue();
    m_result.depth = m_worker.getSearchDepth();
    m_result.evaluations = m_worker.getEvalCount();
    for (int i = 0; i < static_cast<int>(m_result.moves.size()); ++i) {
        m_result.moves[i] = m_worker.getBestMoveAt(i);
        m_result.duckMoves[i] = m_worker.getBestDuckMoveAt(i);
    }
}

void SearchController::interrupt(uint64_t id) {
    std::lock_guard<std::mutex> lock(m_mutex);
    if (id != 0 && id != m_id) return;
    m_interrupted = true;
    m_worker.interruptSearch();
}

void SearchController::cancelAndWait() {
    std::unique_lock<std::mutex> lock(m_mutex);
    m_interrupted = true;
    m_worker.interruptSearch();
    if (m_state == State::Prepared) m_state = State::Idle;
    m_finished.wait(lock, [this] { return m_state != State::Running; });
    m_state = State::Idle;
    m_result = SearchResult();
}

bool SearchController::done() const {
    std::lock_guard<std::mutex> lock(m_mutex);
    return m_state != State::Prepared && m_state != State::Running;
}

bool SearchController::matches(uint64_t id, uint64_t revision) const {
    std::lock_guard<std::mutex> lock(m_mutex);
    return m_liveSource && id != 0 && id == m_id && revision == m_revision && m_state != State::Idle;
}

SearchResult SearchController::result(uint64_t id) const {
    std::lock_guard<std::mutex> lock(m_mutex);
    return id == 0 || id == m_id ? m_result : SearchResult();
}
