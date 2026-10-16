#include "BoardStack.h"

#include <vector>

BoardStack::BoardStack() : m_current(new ChessBoard()) {
    m_current->reset();
}

BoardStack::~BoardStack() {
    if (m_current == nullptr) {
        return;
    }
    clearHistory();
    delete m_current;
    m_current = nullptr;
}

ChessBoard* BoardStack::current() const {
    return m_current;
}

void BoardStack::clearHistory() {
    if (m_current == nullptr) {
        return;
    }

    ChessBoard* previous = nullptr;
    while ((previous = m_current->undoMove()) != nullptr) {
        ChessBoard* obsolete = m_current;
        m_current = previous;
        delete obsolete;
    }
}

boolean BoardStack::undo() {
    if (m_current == nullptr) {
        return false;
    }

    ChessBoard* previous = m_current->undoMove();
    if (previous == nullptr) {
        return false;
    }
    ChessBoard* obsolete = m_current;
    m_current = previous;
    delete obsolete;
    return true;
}

boolean BoardStack::promoteOrDiscard(std::unique_ptr<ChessBoard> nextBoard, boolean success) {
    if (!nextBoard) {
        return false;
    }

    if (!success) {
        return false;
    }

    m_current = nextBoard.release();
    return true;
}

void BoardStack::cloneFrom(const BoardStack& source) {
    std::vector<const ChessBoard*> history;
    for (const ChessBoard* board = source.m_current; board != nullptr; board = board->m_parent) {
        history.push_back(board);
    }
    std::vector<std::unique_ptr<ChessBoard>> copies;
    ChessBoard* parent = nullptr;
    for (auto it = history.rbegin(); it != history.rend(); ++it) {
        std::unique_ptr<ChessBoard> copy(new ChessBoard(**it));
        copy->m_parent = parent;
        parent = copy.get();
        copies.push_back(std::move(copy));
    }
    clearHistory();
    delete m_current;
    m_current = parent;
    for (auto& copy : copies) {
        copy.release();
    }
}
