#include "Game.h"

Game *Game::game = nullptr;

Game::Game(void) {
    m_board = new ChessBoard();
    m_promotionPiece = ChessBoard::QUEEN;
    for (int i = 0; i < MAX_DEPTH; i++) {
        m_boardFactory[i] = new ChessBoard();
    }
    m_boardRefurbish = new ChessBoard();
    m_bSearching = false;
    reset();
}

void Game::clearBoardHistory() {
    ChessBoard *cb, *tmp;
    while ((cb = m_board->undoMove()) != nullptr) {
        tmp = m_board;
        m_board = cb;
        delete tmp;
    }
}

Game::~Game(void) {
    clearBoardHistory();

    delete m_board;

    delete m_boardRefurbish;

    // boardFactory boards are scratch buffers; reset severs any parent links before deletion.
    for (int i = 0; i < MAX_DEPTH; i++) {
        m_boardFactory[i]->reset();
        delete m_boardFactory[i];
        m_boardFactory[i] = nullptr;
    }
}

// the non thread safe solution; assumption is that getInsance is called before any threads are created
Game *Game::getInstance() {
    if (Game::game == nullptr) {
        ChessBoard::initStatics();
        Game::game = new Game();
    }
    return Game::game;
}

void Game::deleteInstance() {
    if (Game::game != nullptr) {
        if (Game::game->m_bSearching.load()) {
            return;
        }
        delete Game::game;
        Game::game = nullptr;
    }
}

void Game::reset() {
    if (m_bSearching.load()) {
        return;
    }

    clearBoardHistory();

    m_board->reset();
    m_board->calcState(m_boardRefurbish);
    for (int i = 0; i < MAX_DEPTH; i++) {
        m_boardFactory[i]->reset();
    }

    m_bestMoveAndValue = (MoveAndValue){.value = 0, .move = 0, .duckMove = -1};
    for (int i = 0; i < MAX_DEPTH; i++) {
        m_arrBestMoves[i] = (MoveAndValue){.value = 0, .move = 0, .duckMove = -1};
    }
}

boolean Game::newGameFromFEN(const char *sFEN) {
    if (m_bSearching.load()) {
        return false;
    }

    reset();
    ChessBoard *board = getBoard();
    int ret = board->parseFEN(sFEN);
    if (ret) {
        commitBoard();
    } else {
        reset();
    }
    return ret;
}

void Game::commitBoard() {
    if (m_bSearching.load()) {
        return;
    }
    m_board->commitBoard();
    m_board->calcState(m_boardRefurbish);
}

ChessBoard *Game::getBoard() {
    return m_board;
}

void Game::setPromo(int p) {
    m_promotionPiece = p;
}

int Game::getBestMove() {
    return m_bestMoveAndValue.move;
}
int Game::getBestDuckMove() {
    return m_bestMoveAndValue.duckMove;
}
int Game::getBestValue() {
    return m_bestMoveAndValue.value;
}

int Game::getBestMoveAt(int ply) {
    if (ply >= 0 && ply < MAX_DEPTH) {
        return m_arrBestMoves[ply].move;
    }
    return 0;
}

int Game::getBestDuckMoveAt(int ply) {
    if (ply >= 0 && ply < MAX_DEPTH) {
        return m_arrBestMoves[ply].duckMove;
    }
    return -1;
}

boolean Game::requestMove(int from, int to) {
    if (m_bSearching.load()) {
        return false;
    }

    ChessBoard *nb = new ChessBoard();
    m_board->calcState(m_boardRefurbish);
    if (m_board->requestMove(from, to, nb, m_boardRefurbish, m_promotionPiece)) {
        m_board = nb;
        return true;
    } else {
        delete nb;
        // DEBUG_PRINT("%d-%d not moved...(request)\n", from, to);
        return false;
    }
}

boolean Game::requestDuckMove(int duckPos) {
    if (m_bSearching.load()) {
        return false;
    }

    ChessBoard *board = getBoard();
    board->calcState(m_boardRefurbish);
    if (board->requestDuckMove(duckPos)) {
        board->genMoves();
        return true;
    }
    return false;
}

boolean Game::move(int move) {
    if (m_bSearching.load()) {
        return false;
    }

    ChessBoard *nb = new ChessBoard();

    m_board->calcState(m_boardRefurbish);
    if (m_board->requestMove(move, nb, m_boardRefurbish)) {
        m_board = nb;
        return true;
    } else {
        delete nb;
        return false;
    }
}

void Game::undo() {
    if (m_bSearching.load()) {
        return;
    }

    ChessBoard *tmp = m_board;
    ChessBoard *cb = m_board->undoMove();
    if (cb != nullptr) {
        m_board = cb;
        delete tmp;
    }
}
// allowAttack, if a new piece on the board is allowed to attack immediatly,
// for crazyhouse that's ok, for parachute not
boolean Game::putPieceHouse(const int pos, const int piece, const boolean allowAttack) {
    if (m_bSearching.load()) {
        return false;
    }

    ChessBoard *nextBoard = new ChessBoard();
    if (m_board->putHouse(pos, piece, nextBoard, m_boardRefurbish, allowAttack)) {
        m_board = nextBoard;
        return true;
    }
    delete nextBoard;
    return false;
}
