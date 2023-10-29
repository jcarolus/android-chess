#ifndef TEST_METHODS_H
#define TEST_METHODS_H

#include <unistd.h>
#include <pthread.h>
#include "../common.h"
#include "../Game.h"

typedef bool (*TestFunction)();

typedef struct {
    Game *game;
    char *sInFEN;
    char *sOutFEN;
    int depth;
    int numMoves;
    boolean isDuck;
    char *message;
} EngineInOutFEN;

typedef struct {
    Game *game;
    char *sInFEN;
    char *sOutFEN;
    int *moves;
    int moveNum;
    char *message;
} SequenceInOutFEN;

typedef struct {
    Game *game;
    char *sInFEN;
    int *moves;
    int moveNum;
    char *message;
} NonSequenceInFEN;

typedef struct {
    Game *game;
    char *sInFEN;
    int expectedState;
    int depth;
    int maxMoves;
    boolean isDuck;
    char *message;
} EngineInFENUntilState;

typedef struct {
    Game *game;
    char *sInFEN;
    int expectedMoveCount;
    char *expectedMoves[20];
    bool all;
} MovesForFEN;

class ChessTest {
   public:
    ChessTest(void);
    ~ChessTest(void);

    static void startSearchThread();
    static bool expectEqualInt(int a, int b, char *message);
    static bool expectEqualString(char *a, char *b, char *message);
    static bool expectEngineMove(EngineInOutFEN scenario);
    static bool expectSequence(SequenceInOutFEN scenario);
    static bool expectNonSequence(NonSequenceInFEN scenario);
    static bool expectStateForFEN(Game *game, char *sFEN, int state, char *message);
    static bool expectInFENIsOutFEN(Game *game, char *sFEN, char *message);
    static bool expectEndingStateWithinMaxMoves(EngineInFENUntilState scenario);
    static bool expectMovesForFEN(MovesForFEN scenario);
    static void printMove(int move);
    static void printFENAndState(ChessBoard *board);
};

#endif