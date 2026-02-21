#ifndef TEST_METHODS_H
#define TEST_METHODS_H

#include <unistd.h>
#include <pthread.h>
#include "../common.h"
#include "../Game.h"

typedef bool (*TestFunction)();

typedef struct {
    Game *game;
    const char *sInFEN;
    const char *sOutFEN;
    int depth;
    int numMoves;
    boolean isDuck;
    const char *message;
} EngineInOutFEN;

typedef struct {
    Game *game;
    const char *sInFEN;
    const char *sOutFEN;
    int *moves;
    int moveNum;
    const char *message;
} SequenceInOutFEN;

typedef struct {
    Game *game;
    const char *sInFEN;
    int *moves;
    int moveNum;
    const char *message;
} NonSequenceInFEN;

typedef struct {
    Game *game;
    const char *sInFEN;
    int expectedState;
    int depth;
    int maxMoves;
    boolean isDuck;
    const char *message;
} EngineInFENUntilState;

typedef struct {
    Game *game;
    const char *sInFEN;
    int expectedMoveCount;
    const char *expectedMoves[20];
    bool all;
} MovesForFEN;

typedef struct {
    Game *game;
    const char *sInFEN;
    int expectedState;
    const char *message;
} StateForFEN;

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
    static void printMove(int move);
    static void printFENAndState(ChessBoard *board);
};

#endif
