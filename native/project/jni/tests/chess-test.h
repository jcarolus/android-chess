#ifndef TEST_METHODS_H
#define TEST_METHODS_H

#include <unistd.h>
#include <pthread.h>
#include "../common.h"
#include "../Game.h"

using TestFunction = bool (*)();

struct EngineInOutFEN {
    Game *game;
    const char *sInFEN;
    const char *sOutFEN;
    int depth;
    int numMoves;
    boolean isDuck;
    const char *message;
};

struct SequenceInOutFEN {
    Game *game;
    const char *sInFEN;
    const char *sOutFEN;
    int *moves;
    int moveNum;
    const char *message;
};

struct NonSequenceInFEN {
    Game *game;
    const char *sInFEN;
    int *moves;
    int moveNum;
    const char *message;
};

struct EngineInFENUntilState {
    Game *game;
    const char *sInFEN;
    int expectedState;
    int depth;
    int maxMoves;
    boolean isDuck;
    const char *message;
};

struct MovesForFEN {
    Game *game;
    const char *sInFEN;
    int expectedMoveCount;
    const char *expectedMoves[20];
    bool all;
};

struct RequestMove {
    Game *game;
    const char *sInFEN;
    int from;
    int to;
    bool expectedSuccess;
    const char *message;
};

struct StateForFEN {
    Game *game;
    const char *sInFEN;
    int expectedState;
    const char *message;
};

class ChessTest {
   public:
    ChessTest(void);
    ~ChessTest(void);

    static void startSearchThread();
    static bool expectEngineMove(EngineInOutFEN scenario);
    static bool expectSequence(SequenceInOutFEN scenario);
    static bool expectNonSequence(NonSequenceInFEN scenario);
    static bool expectStateForFEN(Game *game, const char *sFEN, int state, const char *message);
    static bool expectInFENIsOutFEN(Game *game, const char *sFEN, const char *message);
    static bool expectEndingStateWithinMaxMoves(EngineInFENUntilState scenario);
    static bool expectMovesForFEN(MovesForFEN scenario);
    static bool expectRequestMove(RequestMove scenario);
    static void printMove(int move);
    static void printFENAndState(ChessBoard *board);
};

#endif
