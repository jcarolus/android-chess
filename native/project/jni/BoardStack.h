#pragma once

#include <memory>

#include "ChessBoard.h"

class BoardStack {
   public:
    BoardStack();
    ~BoardStack();

    BoardStack(const BoardStack&) = delete;
    BoardStack& operator=(const BoardStack&) = delete;

    ChessBoard* current() const;
    void clearHistory();
    boolean undo();
    boolean promoteOrDiscard(std::unique_ptr<ChessBoard> nextBoard, boolean success);

   private:
    ChessBoard* m_current;
};
