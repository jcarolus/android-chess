#include "SearchWorkspace.h"

SearchWorkspace::SearchWorkspace(int depth) : m_depth(depth), m_factory(new ChessBoard*[depth]), m_refurbish(new ChessBoard()) {
    for (int i = 0; i < m_depth; i++) {
        m_factory[i] = new ChessBoard();
    }
}

SearchWorkspace::~SearchWorkspace() {
    delete m_refurbish;
    m_refurbish = nullptr;
    for (int i = 0; i < m_depth; i++) {
        m_factory[i]->reset();
        delete m_factory[i];
        m_factory[i] = nullptr;
    }
    delete[] m_factory;
    m_factory = nullptr;
}

void SearchWorkspace::reset() {
    for (int i = 0; i < m_depth; i++) {
        m_factory[i]->reset();
    }
}

ChessBoard* SearchWorkspace::boardAt(int index) const {
    return m_factory[index];
}

ChessBoard* SearchWorkspace::refurbish() const {
    return m_refurbish;
}
