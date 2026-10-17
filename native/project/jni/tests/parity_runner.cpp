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

int main(int argc, char** argv) {
    if (argc < 5) return 2;
    Game game;
    if (!game.newGameFromFEN(argv[1])) return 3;
    bool chess960 = std::stoi(argv[4]) == 1;
    for (int i = 5; i < argc; ++i) {
        std::string text = argv[i];
        bool applied = false;
        ChessBoard* board = game.getBoard();
        for (int j = 0; j < board->getNumMoves(); ++j) {
            int move = board->getMoveAt(j);
            if (coordinate(move, board, chess960) == text) { applied = game.move(move); break; }
        }
        if (!applied) return 4;
    }
    ChessBoard* board = game.getBoard();
    char fen[256];
    board->toFEN(fen);
    std::cout << "POSITION\t" << fen << '\t' << board->boardValue() << '\t' << board->getState() << '\n';
    for (int i = 0; i < board->getNumMoves(); ++i) {
        int move = board->getMoveAt(i);
        ChessBoard child;
        board->makeMove(move, &child);
        child.toFEN(fen);
        std::cout << "MOVE\t" << coordinate(move, board, chess960) << '\t' << fen << '\t' << child.boardValue() << '\n';
    }
    game.setSearchLimit(std::stoi(argv[2]));
    game.setQuiescentOn(std::stoi(argv[3]));
    game.search();
    std::cout << "RESULT\t" << coordinate(game.getBestMove(), board, chess960) << '\t' << game.getBestValue() << '\t' << game.completedDepth() << '\t' << game.getEvalCount() << '\n';
}
