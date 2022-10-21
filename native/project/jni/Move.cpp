#include "Move.h"

Move::Move(void) {
}

Move::~Move(void) {
}

// returns pgn alike string representation of the move - not full pgn format because then more
// information is needed
void Move::toDbgString(const int move, char* buf) {
    if (Move_isOO(move)) {
        strcpy(buf, "O-O");
        return;
    }
    if (Move_isOOO(move)) {
        strcpy(buf, "O-O-O");
        return;
    }
    char tmp[10] = "";
    Pos::toString(Move_getFrom(move), tmp);
    strcpy(buf, "[");
    strcat(buf, tmp);
    strcat(buf, (Move_isHIT(move) ? "x" : "-"));
    Pos::toString(Move_getTo(move), tmp);
    strcat(buf, tmp);
    if (Move_isCheck(move)) {
        strcat(buf, "+");
    }
    if (Move_isEP(move)) {
        strcat(buf, " ep");
    }
    strcat(buf, "]");
}

void Move::toBitString(const int move, char* ret) {
    strcpy(ret, "");
    int bT = 1;
    int i = 0;
    while (i < 32) {
        if ((move & bT) == bT) {
            strcat(ret, "1");
        } else {
            strcat(ret, "0");
        }
        i++;
        bT <<= 1;
    }
}
