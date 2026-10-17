#include "Game.h"
#include <iostream>
#include <string>

std::string coordinate(int move) {
    if (!move) return "-";
    char from[4], to[4];
    Pos::toString(Move_getFrom(move), from);
    Pos::toString(Move_getTo(move), to);
    std::string result = std::string(from) + to;
    if (Move_isPromotionMove(move)) result += "pnbrqk"[Move_getPromotionPiece(move)];
    return result;
}

int main(int argc, char** argv) {
    if (argc < 4) return 2;
    Game game;
    if (!game.newGameFromFEN(argv[1])) return 3;
    for (int i = 4; i < argc; ++i) {
        std::string text = argv[i];
        bool applied = false;
        ChessBoard* board = game.getBoard();
        for (int j = 0; j < board->getNumMoves(); ++j) {
            int move = board->getMoveAt(j);
            if (coordinate(move) == text) { applied = game.move(move); break; }
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
        std::cout << "MOVE\t" << coordinate(move) << '\t' << fen << '\t' << child.boardValue() << '\n';
    }
    game.setSearchLimit(std::stoi(argv[2]));
    game.setQuiescentOn(std::stoi(argv[3]));
    game.search();
    std::cout << "RESULT\t" << coordinate(game.getBestMove()) << '\t' << game.getBestValue() << '\t' << game.completedDepth() << '\t' << game.getEvalCount() << '\n';
}
