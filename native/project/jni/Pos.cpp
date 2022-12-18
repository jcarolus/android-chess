#include "Pos.h"

Pos::Pos(void) {
}

Pos::~Pos(void) {
}

// returns positional value [0-63] for squares [a8-h1]
// when a position cannot be created a message is sent on console out (co).
// used to initialize values, no speed needed

int Pos::fromString(const char* s) {
    char c = s[0], tmp[2];
    int col, row;
    tmp[0] = s[1];
    tmp[1] = '\0';
    sscanf(tmp, "%d", &row);
    col = (int) c - (int) 'a';

    return ((8 - row) * 8) + col;
}

// returns positional value [0-63] from a column and row.
// @col the column [0-7] (left to right)
// @row the row [0-7] (top to bottom)
// no check for invalid row or col is done for reasons of speed
int Pos::fromColAndRow(const int col, const int row) {
    return (row * 8) + col;
}

// returns the row [0-7] from top to bottom; ie values in [a8-h8] return 0.
int Pos::row(const int val) {
    return (val >> 3) & 7;
}
//  returns the column [0-7] from left to right; ie values in [a8-a8] return 0.
int Pos::col(const int val) {
    return val % 8;
}

// returns string representation of the value; ie "d5"
// @val positional value [0-63]

void Pos::toString(const int val, char* buf) {
    if (val >= 0 && val < 64) {
        sprintf(buf, "%c%d", (char) (Pos::col(val) + (int) 'a'), 8 - Pos::row(val));
    } else {
        sprintf(buf, "X");
    }
}

// returns string representation of the row of val - human represented so from bottom to top
// ["1"-"8"]

void Pos::rowToString(const int val, char* buf) {
    sprintf(buf, "%d", (8 - Pos::row(val)));
}
// returns string representation of the column. ["a"-"h"]
void Pos::colToString(const int val, char* buf) {
    sprintf(buf, "%c", ((char) (Pos::col(val) + (int) 'a')));
}
