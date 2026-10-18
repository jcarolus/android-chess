#include "Game.h"
#include <iostream>
#include <string>

// The rook's current square for a castle move, found by scanning outward
// from the king along the back rank (the rook's identity, not its square, is
// what the castling right tracks).
int castlingRookSquare(int move, ChessBoard* board) {
    int from = Move_getFrom(move);
    int dir = Move_isOO(move) ? 1 : -1;
    int rankBase = from - from % 8;
    int bound = dir > 0 ? rankBase + 7 : rankBase;
    int turn = board->getTurn();
    for (int s = from + dir; dir > 0 ? s <= bound : s >= bound; s += dir) {
        if (board->pieceAt(turn, s) == ChessBoard::ROOK) return s;
    }
    return from;
}

// King-to-destination for Standard; king-to-original-rook for Chess 960
// castling (UCI_Chess960 convention), matching the Rust core's external
// coordinate convention (see chess_core::Position::external).
std::string coordinate(int move, ChessBoard* board, bool chess960) {
    if (!move) return "-";
    char from[4], to[4];
    Pos::toString(Move_getFrom(move), from);
    if (chess960 && (Move_isOO(move) || Move_isOOO(move))) {
        Pos::toString(castlingRookSquare(move, board), to);
    } else {
        Pos::toString(Move_getTo(move), to);
    }
    std::string result = std::string(from) + to;
    if (Move_isPromotionMove(move)) result += "pnbrqk"[Move_getPromotionPiece(move)];
    return result;
}

// Standard Algebraic Notation for the move that produced @child, matching
// chess_core::Position::to_san: Duck never carries a check/mate suffix, since
// placing the duck can still answer a check the piece move alone leaves.
std::string sanFor(ChessBoard& child, bool duck) {
    if (!duck) {
        ChessBoard scratch;
        child.calcState(&scratch);
    }
    char buf[20];
    child.myMoveToString(buf);
    std::string san(buf);
    if (duck && !san.empty() && (san.back() == '+' || san.back() == '#')) {
        san.pop_back();
    }
    return san;
}

int main(int argc, char** argv) {
    if (argc < 5) return 2;
    Game game;
    if (!game.newGameFromFEN(argv[1])) return 3;
    bool chess960 = std::stoi(argv[4]) == 1;
    bool duck = std::stoi(argv[4]) == 2;
    if (duck) {
        game.getBoard()->setVariant(ChessBoard::VARIANT_DUCK);
        ChessBoard scratch;
        game.getBoard()->calcState(&scratch);
    }
    for (int i = 5; i < argc; ++i) {
        std::string text = argv[i];
        auto at = text.find('@');
        std::string piece = text.substr(0, at);
        bool applied = false;
        ChessBoard* board = game.getBoard();
        for (int j = 0; j < board->getNumMoves(); ++j) {
            int move = board->getMoveAt(j);
            if (coordinate(move, board, chess960) == piece) { applied = game.move(move); break; }
        }
        if (!applied) return 4;
        if (at != std::string::npos) {
            int square = ('8' - text[at + 2]) * 8 + text[at + 1] - 'a';
            if (!game.getBoard()->requestDuckMove(square)) return 5;
            ChessBoard scratch;
            game.getBoard()->calcState(&scratch);
        } else if (duck && !game.getBoard()->isEnded()) return 6;
    }
    ChessBoard* board = game.getBoard();
    char fen[256];
    board->toFEN(fen);
    std::cout << "POSITION\t" << fen << '\t' << board->boardValue() << '\t' << board->getState() << '\n';
    for (int i = 0; i < board->getNumMoves(); ++i) {
        int move = board->getMoveAt(i);
        ChessBoard child;
        board->makeMove(move, &child);
        std::string piece = coordinate(move, board, chess960);
        std::string pieceSan = sanFor(child, duck);
        if (duck && !child.isEnded()) {
            for (int square = 0; square < 64; ++square) {
                if (!child.isPosFree(square) || square == board->getDuckPos()) continue;
                ChessBoard placed;
                child.duplicate(&placed);
                if (!placed.requestDuckMove(square)) continue;
                char name[4];
                Pos::toString(square, name);
                placed.toFEN(fen);
                std::cout << "MOVE\t" << piece << '@' << name << '\t' << fen << '\t' << placed.boardValue() << '\t'
                          << pieceSan << '@' << name << '\n';
            }
        } else {
            child.toFEN(fen);
            std::cout << "MOVE\t" << piece << '\t' << fen << '\t' << child.boardValue() << '\t' << pieceSan << '\n';
        }
    }
    game.setSearchLimit(std::stoi(argv[2]));
    game.setQuiescentOn(std::stoi(argv[3]));
    game.search();
    std::string best = coordinate(game.getBestMove(), board, chess960);
    if (duck && game.getBestDuckMove() >= 0) {
        char name[4]; Pos::toString(game.getBestDuckMove(), name); best += std::string("@") + name;
    }
    std::cout << "RESULT\t" << best << '\t' << game.getBestValue() << '\t' << game.completedDepth() << '\t' << game.getEvalCount() << '\n';
}
