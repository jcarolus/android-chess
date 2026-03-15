#include "BoardStack.h"

BoardStack::BoardStack() : m_current(new ChessBoard()) {
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
