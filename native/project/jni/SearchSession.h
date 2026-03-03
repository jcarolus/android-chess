#pragma once

#include <atomic>

#include "common.h"

class SearchSession {
   public:
    SearchSession();

    void configureTime(long millisGiven);
    void configureDepthLimit(int depth, int maxDepth);
    void begin();

    void interrupt();
    boolean interrupted() const;

    int searchDepth() const;
    void setSearchDepth(int depth);
    int searchLimit() const;
    long timeBudgetMillis() const;

    boolean timeUp() const;
    boolean usedTime() const;
    long timePassed() const;

   private:
    static long nowMillis();

    std::atomic_bool m_interrupted;
    std::atomic_int m_searchDepth;
    int m_searchLimit;
    long m_startMillis;
    long m_millisGiven;
};
