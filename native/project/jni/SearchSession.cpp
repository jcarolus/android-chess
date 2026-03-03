#include "SearchSession.h"

SearchSession::SearchSession()
    : m_interrupted(false), m_searchDepth(0), m_searchLimit(0), m_startMillis(0), m_millisGiven(0) {
}

void SearchSession::configureTime(long millisGiven) {
    m_millisGiven = millisGiven;
    m_searchLimit = 0;
}

void SearchSession::configureDepthLimit(int depth, int maxDepth) {
    m_millisGiven = 0;
    if (depth <= maxDepth) {
        m_searchLimit = depth;
    } else {
        m_searchLimit = maxDepth;
    }
}

void SearchSession::begin() {
    m_interrupted.store(false);
    m_searchDepth.store(0);
    m_startMillis = nowMillis();
}

void SearchSession::interrupt() {
    m_interrupted.store(true);
}

boolean SearchSession::interrupted() const {
    return m_interrupted.load();
}

int SearchSession::searchDepth() const {
    return m_searchDepth.load();
}

void SearchSession::setSearchDepth(int depth) {
    m_searchDepth.store(depth);
}

int SearchSession::searchLimit() const {
    return m_searchLimit;
}

long SearchSession::timeBudgetMillis() const {
    return m_millisGiven;
}

boolean SearchSession::timeUp() const {
    if (m_millisGiven == 0) {
        return false;
    }
    return m_millisGiven < timePassed();
}

boolean SearchSession::usedTime() const {
    return (m_millisGiven / 3) < timePassed();
}

long SearchSession::timePassed() const {
    return nowMillis() - m_startMillis;
}

long SearchSession::nowMillis() {
    timeval time;
    gettimeofday(&time, nullptr);
    return (time.tv_sec * 1000) + (time.tv_usec / 1000);
}
