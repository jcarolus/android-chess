#pragma once

#include "common.h"
#include "ChessBoard.h"

class Game {
   public:
    Game(void);
    ~Game(void);

    void reset();
    boolean newGameFromFEN(char* sFEN);
    void commitBoard();
    ChessBoard* getBoard();
    boolean requestMove(int from, int to);
    boolean requestDuckMove(int duckPos);
    boolean move(int);
    void undo();
    void setPromo(int p);
    int getBestMove();
    int getBestDuckMove();
    int getBestMoveAt(int ply);

    void setSearchTime(int secs);
    void setSearchLimit(int depth);
    void search();
    boolean alphaBetaRoot(const int depth, int alpha, const int beta);
    int alphaBeta(ChessBoard* board, const int depth, int alpha, const int beta);
    inline int quiesce(ChessBoard* board, const int depth, int alpha, const int beta);
    int searchDB();
    int searchHouse();
    boolean putPieceHouse(const int pos, const int piece, const boolean allowAttack);
    void loadDB(const char* sFile, int depth);
    boolean usedTime();
    boolean timeUp();
    long timePassed();
    void startTime();

    boolean m_bInterrupted;
    boolean m_bSearching;
    int m_evalCount;
    int m_bestValue;
    int m_searchDepth;
    int m_searchLimit;

   protected:
    long findDBKey(BITBOARD bbKey);
    boolean readDBAt(int iPos, BITBOARD& bb);

    int m_bestMove, m_bestDuckMove;
    static const int MAX_DEPTH = 20;
    static const int QUIESCE_DEPTH = 5;  // makes effective max depth 15

    long m_millies, m_milliesGiven;

    static int DB_SIZE;
    static FILE* DB_FP;
    static int DB_DEPTH;

    static const BITBOARD DEFAULT_START_HASH = -8567268772865283918LL;
    ChessBoard* m_boardFactory[MAX_DEPTH];
    ChessBoard* m_boardRefurbish;
    ChessBoard* m_board;
    int m_promotionPiece;
    int m_arrBestMoves[MAX_DEPTH];
};
