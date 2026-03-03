#pragma once

#include "ChessBoard.h"

class SearchWorkspace {
   public:
    explicit SearchWorkspace(int depth);
    ~SearchWorkspace();

    SearchWorkspace(const SearchWorkspace&) = delete;
    SearchWorkspace& operator=(const SearchWorkspace&) = delete;

    void reset();
    ChessBoard* boardAt(int index) const;
    ChessBoard* refurbish() const;

   private:
    int m_depth;
    ChessBoard** m_factory;
    ChessBoard* m_refurbish;
};
