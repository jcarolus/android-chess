#include "Game.h"

#include <memory>

Game *Game::game = nullptr;

Game::Game(void) : m_searchWorkspace(MAX_DEPTH) {
    m_promotionPiece = ChessBoard::QUEEN;
    m_bSearching = false;
    m_quiescentSearchOn = true;
    m_evalCount = 0;
    reset();
}

Game::~Game(void) {
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

    m_boardStack.clearHistory();

    ChessBoard *board = getBoard();
    board->reset();
    board->calcState(m_searchWorkspace.refurbish());
    m_searchWorkspace.reset();

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
    ChessBoard *board = getBoard();
    board->commitBoard();
    board->calcState(m_searchWorkspace.refurbish());
}

ChessBoard *Game::getBoard() {
    return m_boardStack.current();
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

int Game::getSearchDepth() const {
    return m_searchSession.searchDepth();
}

int Game::getEvalCount() const {
    return m_evalCount;
}

void Game::interruptSearch() {
    m_searchSession.interrupt();
}

boolean Game::requestMove(int from, int to) {
    if (m_bSearching.load()) {
        return false;
    }

    std::unique_ptr<ChessBoard> nb(new ChessBoard());
    ChessBoard *board = getBoard();
    board->calcState(m_searchWorkspace.refurbish());
    boolean moved = board->requestMove(from, to, nb.get(), m_searchWorkspace.refurbish(), m_promotionPiece);

    if (!moved && from != to) {
        const int turn = board->getTurn();
        if (board->pieceAt(turn, from) == ChessBoard::KING && Pos::row(from) == Pos::row(to)) {
            const int delta = to - from;
            int castleMove = 0;
            board->getMoves();
            while (board->hasMoreMoves()) {
                const int move = board->getNextMove();
                if ((Move_isOO(move) || Move_isOOO(move)) && Move_getFrom(move) == from) {
                    const int castleTo = Move_getTo(move);
                    if (to == castleTo || (delta > 0 && Move_isOO(move)) || (delta < 0 && Move_isOOO(move))) {
                        castleMove = move;
                        break;
                    }
                }
            }
            if (castleMove != 0) {
                moved = board->requestMove(castleMove, nb.get(), m_searchWorkspace.refurbish());
            }
        }
    }

    return m_boardStack.promoteOrDiscard(std::move(nb), moved);
}

boolean Game::requestDuckMove(int duckPos) {
    if (m_bSearching.load()) {
        return false;
    }

    ChessBoard *board = getBoard();
    board->calcState(m_searchWorkspace.refurbish());
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

    std::unique_ptr<ChessBoard> nb(new ChessBoard());
    ChessBoard *board = getBoard();
    board->calcState(m_searchWorkspace.refurbish());
    boolean moved = board->requestMove(move, nb.get(), m_searchWorkspace.refurbish());
    return m_boardStack.promoteOrDiscard(std::move(nb), moved);
}

void Game::undo() {
    if (m_bSearching.load()) {
        return;
    }

    m_boardStack.undo();
}

// allowAttack, if a new piece on the board is allowed to attack immediatly,
// for crazyhouse that's ok, for parachute not
boolean Game::putPieceHouse(const int pos, const int piece, const boolean allowAttack) {
    if (m_bSearching.load()) {
        return false;
    }

    std::unique_ptr<ChessBoard> nextBoard(new ChessBoard());
    ChessBoard *board = getBoard();
    boolean moved = board->putHouse(pos, piece, nextBoard.get(), m_searchWorkspace.refurbish(), allowAttack);
    return m_boardStack.promoteOrDiscard(std::move(nextBoard), moved);
}
