#include "ChessBoard.h"

// return FEN notation of board (just the pieces on the board board)
void ChessBoard::toFENBoard(char* s) {
    strcpy(s, "");

    char sP[2], buf[50];
    char arrP[2][7] = {"pnbrqk", "PNBRQK"};
    sP[1] = '\0';

    int numEmpty = 0, piece;
    for (int i = 0; i < 64; i++) {
        sP[0] = '\0';

        if (i == getDuckPos()) {
            sP[0] = '$';
        } else if (isPieceOfColorAt(BLACK, i)) {
            piece = pieceAt(BLACK, i);
            sP[0] = arrP[BLACK][piece];
        } else if (isPieceOfColorAt(WHITE, i)) {
            piece = pieceAt(WHITE, i);
            sP[0] = arrP[WHITE][piece];
        }
        if (i > 0 && i % 8 == 0) {
            if (numEmpty > 0) {
                snprintf(buf, sizeof(buf), "%d", numEmpty);
                strcat(s, buf);
                numEmpty = 0;
            }
            if (i < 62) {
                strcat(s, "/");
            }
        }
        if (sP[0] == '\0') {
            numEmpty++;
        } else {
            if (numEmpty > 0) {
                snprintf(buf, sizeof(buf), "%d", numEmpty);
                strcat(s, buf);
            }
            strcat(s, sP);
            numEmpty = 0;
        }
    }
    if (numEmpty > 0) {
        snprintf(buf, sizeof(buf), "%d", numEmpty);
        strcat(s, buf);
    }
    strcat(s, " ");
    if (m_turn == WHITE) {
        strcat(s, "w");
    } else {
        strcat(s, "b");
    }
}

boolean ChessBoard::parseFEN(const char* sFEN) {
    reset();

    m_variant = VARIANT_DEFAULT;
    char s;
    int pos = 0, i = 0, iAdd, duckPos = -1;
    while (pos < 64 && i < strlen(sFEN)) {
        iAdd = 1;
        s = sFEN[i];
        if (s == 'k') {
            put(pos, KING, BLACK);
        } else if (s == 'K') {
            put(pos, KING, WHITE);
        } else if (s == 'q') {
            put(pos, QUEEN, BLACK);
        } else if (s == 'Q') {
            put(pos, QUEEN, WHITE);
        } else if (s == 'r') {
            put(pos, ROOK, BLACK);
        } else if (s == 'R') {
            put(pos, ROOK, WHITE);
        } else if (s == 'b') {
            put(pos, BISHOP, BLACK);
        } else if (s == 'B') {
            put(pos, BISHOP, WHITE);
        } else if (s == 'n') {
            put(pos, KNIGHT, BLACK);
        } else if (s == 'N') {
            put(pos, KNIGHT, WHITE);
        } else if (s == 'p') {
            put(pos, PAWN, BLACK);
        } else if (s == 'P') {
            put(pos, PAWN, WHITE);
        } else if (s == '$') {
            duckPos = pos;
            m_variant = VARIANT_DUCK;
        } else if (s == '/') {
            iAdd = 0;
        } else {
            iAdd = (int) s - 48;
        }
        pos += iAdd;
        i++;
    }
    i++;  // skip space
    if (i < strlen(sFEN)) {
        int wccl = 0, wccs = 0, bccl = 0, bccs = 0, ep = -1, r50 = 0, turn;
        const int restLen = strlen(sFEN) - i;
        char sRest[restLen + 1];
        memcpy(sRest, &sFEN[i], restLen);
        sRest[restLen] = '\0';
        char* token = strtok(sRest, " ");
        if (token != nullptr) {
            if (strcmp(token, "w") == 0) {
                turn = WHITE;
            } else {
                turn = BLACK;
            }
            token = strtok(nullptr, " ");
            if (token != 0) {
                if (strstr(token, "k") != nullptr) {
                    bccs = 1;
                }
                if (strstr(token, "q") != nullptr) {
                    bccl = 1;
                }
                if (strstr(token, "K") != nullptr) {
                    wccs = 1;
                }
                if (strstr(token, "Q") != nullptr) {
                    wccl = 1;
                }
                token = strtok(nullptr, " ");
                if (token != nullptr) {
                    if (strcmp(token, "-") != 0) {
                        ep = Pos::fromString(token);
                    }
                    token = strtok(nullptr, " ");
                    if (token != nullptr) {
                        r50 = atoi(token);
                    }
                }

                setCastlingsEPAnd50(wccl, wccs, bccl, bccs, ep, r50);
                setTurn(turn);
                commitBoard();

                if (m_variant == VARIANT_DUCK) {
                    requestDuckMove(duckPos);
                }

                return true;
            }
        }
    }
    return false;
}

// return complete FEN representation of the board
void ChessBoard::toFEN(char* s) {
    toFENBoard(s);
    char buf[10];

    strcat(s, " ");

    boolean bCastle = false;
    if (hasOO(WHITE)) {
        strcat(s, "K");
        bCastle = true;
    }
    if (hasOOO(WHITE)) {
        strcat(s, "Q");
        bCastle = true;
    }
    if (hasOO(BLACK)) {
        strcat(s, "k");
        bCastle = true;
    }
    if (hasOOO(BLACK)) {
        strcat(s, "q");
        bCastle = true;
    }
    if (false == bCastle) {
        strcat(s, "-");
    }
    strcat(s, " ");
    if (m_ep == -1) {
        strcat(s, "-");
    } else {
        Pos::toString(m_ep, buf);
        strcat(s, buf);
    }

    strcat(s, " ");
    snprintf(buf, sizeof(buf), "%d", m_50RuleCount);
    strcat(s, buf);
    strcat(s, " ");
    int cnt = 0;
    ChessBoard* tmpBoard;
    tmpBoard = this;
    while (tmpBoard->m_parent != nullptr) {
        cnt++;
        tmpBoard = tmpBoard->m_parent;
    }
    cnt = cnt / 2 + 1;
    snprintf(buf, sizeof(buf), "%d", cnt);
    strcat(s, buf);
}

// resturns pgn string representation of the move that lead to @board;
// the move in the m_myMove member of the board
void ChessBoard::myMoveToString(char* buf) {
    strcpy(buf, "");
    if (m_myMove == 0) {
        return;
    }

    if (Move_isOO(m_myMove)) {
        strcat(buf, "O-O");
        strcat(buf, (this->getState() == ChessBoard::CHECK ? "+" : ""));
    } else if (Move_isOOO(m_myMove)) {
        strcat(buf, "O-O-O");
        strcat(buf, (this->getState() == ChessBoard::CHECK ? "+" : ""));
    } else if (Move_isPromotionMove(m_myMove)) {
        char tmp[10];
        if (Move_isHIT(m_myMove)) {
            Pos::colToString(Move_getFrom(m_myMove), tmp);
            strcat(buf, tmp);
            strcat(buf, "x");
        }
        Pos::toString(Move_getTo(m_myMove), tmp);
        strcat(buf, tmp);
        strcat(buf, "=");
        ChessBoard::pieceToString(this->pieceAt(this->opponentTurn(), Move_getTo(m_myMove)), tmp);
        strcat(buf, tmp);
        if (this->getState() == ChessBoard::CHECK) {
            strcat(buf, "+");
        } else if (this->getState() == ChessBoard::MATE) {
            strcat(buf, "#");
        }
    } else {
        char tmp[10];
        ChessBoard::pieceToString(this->pieceAt(this->opponentTurn(), Move_getTo(m_myMove)), tmp);
        strcat(buf, tmp);
        int m = this->ambigiousMove();
        if (m != 0) {
            const int posFromAmb = Move_getFrom(m);
            const int posFrom = Move_getFrom(m_myMove);
            if (Pos::col(posFromAmb) == Pos::col(posFrom)) {
                Pos::rowToString(posFrom, tmp);
                strcat(buf, tmp);
            } else {
                Pos::colToString(posFrom, tmp);
                strcat(buf, tmp);
            }
        }
        if (Move_isHIT(m_myMove)) {
            if (this->pieceAt(this->opponentTurn(), Move_getTo(m_myMove)) == ChessBoard::PAWN) {
                Pos::colToString(Move_getFrom(m_myMove), tmp);
                strcat(buf, tmp);
            }
            strcat(buf, "x");
        }
        Pos::toString(Move_getTo(m_myMove), tmp);
        strcat(buf, tmp);

        if (this->getState() == ChessBoard::CHECK) {
            strcat(buf, "+");
        } else if (this->getState() == ChessBoard::MATE) {
            strcat(buf, "#");
        }
    }
}
