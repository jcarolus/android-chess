#include "BoardStack.h"

BoardStack::BoardStack() : m_current(new ChessBoard()) {
}

BoardStack::~BoardStack() {
    clearHistory();
    delete m_current;
    m_current = nullptr;
}

ChessBoard* BoardStack::current() const {
    return m_current;
}

void BoardStack::clearHistory() {
    ChessBoard* previous = nullptr;
    while ((previous = m_current->undoMove()) != nullptr) {
        ChessBoard* obsolete = m_current;
        m_current = previous;
        delete obsolete;
    }
}

boolean BoardStack::undo() {
    ChessBoard* previous = m_current->undoMove();
    if (previous == nullptr) {
        return false;
    }
    ChessBoard* obsolete = m_current;
    m_current = previous;
    delete obsolete;
    return true;
}

boolean BoardStack::promoteOrDiscard(ChessBoard* nextBoard, boolean success) {
    if (success) {
        m_current = nextBoard;
        return true;
    }
    delete nextBoard;
    return false;
}
