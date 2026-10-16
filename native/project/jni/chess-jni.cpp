#include "chess-jni.hpp"

#include <memory>

#include "BoardStack.h"
#include "SearchController.h"

static std::mutex g_liveMutex;
static uint64_t g_liveRevision = 0;
static SearchController g_search;

static JavaVM* jvm;
static jint stArrMoves[ChessBoard::MAX_MOVES];

// Scratch board stack, fully separate from the live game board (Game/BoardStack).
// Used by EcoService to probe candidate moves (apply, read hash, undo) without
// ever touching the live m_current. Single-threaded use (EcoService, UI thread).
static BoardStack g_scratchStack;
static ChessBoard g_scratchTmp;  // workspace for requestMove's calcState

JNIEXPORT void JNICALL Java_jwtc_chess_JNI_destroy(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    ++g_liveRevision;
    g_search.cancelAndWait();
    Game::deleteInstance();
}
JNIEXPORT void JNICALL Java_jwtc_chess_JNI_setVariant(JNIEnv* env, jobject thiz, jint variant) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    ++g_liveRevision;
    ChessBoard* board = Game::getInstance()->getBoard();
    board->setVariant(variant);
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getVariant(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    ChessBoard* board = Game::getInstance()->getBoard();
    return board->getVariant();
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_requestMove(JNIEnv* env, jobject thiz, jint from, jint to) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    ++g_liveRevision;
    return (int) Game::getInstance()->requestMove(from, to);
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_move(JNIEnv* env, jobject thiz, jint move) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    ++g_liveRevision;
    return (int) Game::getInstance()->move(move);
}

JNIEXPORT int JNICALL Java_jwtc_chess_JNI_requestDuckMove(JNIEnv* env, jobject thiz, jint duckPos) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    ++g_liveRevision;
    return (int) Game::getInstance()->requestDuckMove(duckPos);
}

JNIEXPORT void JNICALL Java_jwtc_chess_JNI_undo(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    ++g_liveRevision;
    Game::getInstance()->undo();
}

JNIEXPORT void JNICALL Java_jwtc_chess_JNI_reset(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    ++g_liveRevision;
    Game::getInstance()->reset();
}
JNIEXPORT void JNICALL Java_jwtc_chess_JNI_putPiece(JNIEnv* env, jobject thiz, jint pos, jint piece, jint turn) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    ++g_liveRevision;
    ChessBoard* board = Game::getInstance()->getBoard();
    board->put(pos, piece, turn);
}

JNIEXPORT int JNICALL Java_jwtc_chess_JNI_newGameFromFEN(JNIEnv* env, jobject thiz, jstring str) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    ++g_liveRevision;
    const char* sFEN = env->GetStringUTFChars(str, nullptr);
    boolean ret = Game::getInstance()->newGameFromFEN(sFEN);
    env->ReleaseStringUTFChars(str, sFEN);
    return ret;
}

JNIEXPORT jlong JNICALL Java_jwtc_chess_JNI_prepareSearch(JNIEnv* env, jobject thiz, jint depth, jint millis, jint quiescentOn) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    return g_search.prepare(*Game::getInstance(), g_liveRevision, depth, millis, quiescentOn != 0);
}
JNIEXPORT jlong JNICALL Java_jwtc_chess_JNI_prepareHistorySearch(JNIEnv* env, jobject thiz,
        jstring fen, jintArray history, jint millis, jint quiescentOn) {
    if (fen == nullptr || history == nullptr) return 0;
    const char* initialFen = env->GetStringUTFChars(fen, nullptr);
    if (initialFen == nullptr) return 0;
    std::vector<jint> moves(env->GetArrayLength(history));
    env->GetIntArrayRegion(history, 0, moves.size(), moves.data());
    uint64_t id = 0;
    if (!env->ExceptionCheck()) {
        id = g_search.prepareHistory(initialFen, std::vector<int>(moves.begin(), moves.end()),
                                     millis, quiescentOn != 0);
    }
    env->ReleaseStringUTFChars(fen, initialFen);
    return id;
}
JNIEXPORT void JNICALL Java_jwtc_chess_JNI_runSearch(JNIEnv* env, jobject thiz, jlong id) {
    g_search.run(id);
}
JNIEXPORT jboolean JNICALL Java_jwtc_chess_JNI_isSearchCurrent(JNIEnv* env, jobject thiz, jlong id) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    return g_search.matches(id, g_liveRevision);
}
JNIEXPORT jintArray JNICALL Java_jwtc_chess_JNI_getSearchResult(JNIEnv* env, jobject thiz, jlong id) {
    const SearchResult snapshot = g_search.result(id);
    jint values[47] = {snapshot.move, snapshot.duckMove, snapshot.value, snapshot.depth, snapshot.evaluations};
    for (int i = 0; i < 20; ++i) {
        values[5 + i] = snapshot.moves[i];
        values[25 + i] = snapshot.duckMoves[i];
    }
    values[45] = snapshot.hasEvaluation ? 1 : 0;
    values[46] = snapshot.completedDepth;
    jintArray result = env->NewIntArray(47);
    if (result != nullptr) env->SetIntArrayRegion(result, 0, 47, values);
    return result;
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getMove(JNIEnv* env, jobject thiz) {
    return g_search.result().move;
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getDuckMove(JNIEnv* env, jobject thiz) {
    return g_search.result().duckMove;
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getBoardValue(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    return Game::getInstance()->getBoard()->boardValue();
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_peekSearchDone(JNIEnv* env, jobject thiz) {
    return g_search.done() ? 1 : 0;
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_peekSearchBestMove(JNIEnv* env, jobject thiz, jint ply) {
    return (ply >= 0 && ply < 20) ? g_search.result().moves[ply] : 0;
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_peekSearchBestDuckMove(JNIEnv* env, jobject thiz, jint ply) {
    return (ply >= 0 && ply < 20) ? g_search.result().duckMoves[ply] : -1;
}

JNIEXPORT int JNICALL Java_jwtc_chess_JNI_peekSearchBestValue(JNIEnv* env, jobject thiz) {
    return g_search.result().value;
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_peekSearchDepth(JNIEnv* env, jobject thiz) {
    return g_search.result().depth;
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getEvalCount(JNIEnv* env, jobject thiz) {
    return g_search.result().evaluations;
}
JNIEXPORT void JNICALL Java_jwtc_chess_JNI_setPromo(JNIEnv* env, jobject thiz, jint piece) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    ++g_liveRevision;
    Game::getInstance()->setPromo(piece);
}

JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getState(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    return Game::getInstance()->getBoard()->getState();
}

JNIEXPORT int JNICALL Java_jwtc_chess_JNI_isEnded(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    return Game::getInstance()->getBoard()->isEnded();
}

JNIEXPORT void JNICALL Java_jwtc_chess_JNI_setCastlingsEPAnd50(JNIEnv* env,
                                                               jobject thiz,
                                                               jint wccl,
                                                               jint wccs,
                                                               jint bccl,
                                                               jint bccs,
                                                               jint ep,
                                                               jint r50) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    ++g_liveRevision;
    Game::getInstance()->getBoard()->setCastlingsEPAnd50(wccl, wccs, bccl, bccs, ep, r50);
}

JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getWhiteCanCastleLong(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    return Game::getInstance()->getBoard()->getWhiteCanCastleLong();
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getWhiteCanCastleShort(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    return Game::getInstance()->getBoard()->getWhiteCanCastleShort();
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getBlackCanCastleLong(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    return Game::getInstance()->getBoard()->getBlackCanCastleLong();
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getBlackCanCastleShort(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    return Game::getInstance()->getBoard()->getBlackCanCastleShort();
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getEnpassantPosition(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    return Game::getInstance()->getBoard()->getEnpassantPosition();
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_get50MoveCount(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    return Game::getInstance()->getBoard()->get50MoveCount();
}

JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getNumBoard(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    return Game::getInstance()->getBoard()->getNumBoard();
}
JNIEXPORT void JNICALL Java_jwtc_chess_JNI_commitBoard(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    ++g_liveRevision;
    Game::getInstance()->commitBoard();
}
JNIEXPORT void JNICALL Java_jwtc_chess_JNI_setTurn(JNIEnv* env, jobject thiz, jint turn) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    ++g_liveRevision;
    Game::getInstance()->getBoard()->setTurn(turn);
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getMoveArraySize(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    ChessBoard* board = Game::getInstance()->getBoard();
    board->getMoves();
    int i = 0;
    while (board->hasMoreMoves()) {
        stArrMoves[i++] = board->getNextMove();
    }
    return board->getNumMoves();
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getMoveArrayAt(JNIEnv* env, jobject thiz, jint i) {
    return stArrMoves[i];
}

JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getTurn(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    return Game::getInstance()->getBoard()->getTurn();
}

JNIEXPORT int JNICALL Java_jwtc_chess_JNI_pieceAt(JNIEnv* env, jobject thiz, jint turn, jint pos) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    return Game::getInstance()->getBoard()->pieceAt(turn, pos);
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_countAttackersTo(JNIEnv* env, jobject thiz, jint pos, jint byTurn) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    return Game::getInstance()->getBoard()->countAttackersTo(pos, byTurn);
}
JNIEXPORT jintArray JNICALL Java_jwtc_chess_JNI_getAttackerPositionsTo(JNIEnv* env, jobject thiz, jint pos, jint byTurn) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    ChessBoard* board = Game::getInstance()->getBoard();
    BITBOARD attackers = board->attackersTo(pos, byTurn);
    const int count = board->bitCount(attackers);

    jintArray result = env->NewIntArray(count);
    if (result == nullptr) {
        return nullptr;
    }

    jint positions[ChessBoard::NUM_FIELDS];
    int index = 0;
    while (attackers != 0) {
        const int attackerPos = board->trailingZeros(attackers);
        positions[index++] = attackerPos;
        attackers &= ChessBoard::NOT_BITS[attackerPos];
    }

    env->SetIntArrayRegion(result, 0, count, positions);
    return result;
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getDuckPos(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    return Game::getInstance()->getBoard()->getDuckPos();
}

JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getMyDuckPos(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    return Game::getInstance()->getBoard()->getMyDuckPos();
}

JNIEXPORT jstring JNICALL Java_jwtc_chess_JNI_getMyMoveToString(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    char buf[20];
    Game::getInstance()->getBoard()->myMoveToString(buf);
    return env->NewStringUTF(buf);
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getMyMove(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    return Game::getInstance()->getBoard()->getMyMove();
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_isLegalPosition(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    return Game::getInstance()->getBoard()->isLegalPosition();
}

JNIEXPORT int JNICALL Java_jwtc_chess_JNI_isAmbiguousCastle(JNIEnv* env, jobject thiz, jint from, jint to) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    return Game::getInstance()->getBoard()->isAmbiguousCastle(from, to);
}

JNIEXPORT void JNICALL Java_jwtc_chess_JNI_doCastleMove(JNIEnv* env, jobject thiz, jint from, jint to) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    ++g_liveRevision;
    int move = Game::getInstance()->getBoard()->getCastleMove(from, to);
    Game::getInstance()->move(move);
}

JNIEXPORT jstring JNICALL Java_jwtc_chess_JNI_toFEN(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    char buf[255];
    Game::getInstance()->getBoard()->toFEN(buf);
    return env->NewStringUTF(buf);
}

JNIEXPORT void JNICALL Java_jwtc_chess_JNI_removePiece(JNIEnv* env, jobject thiz, jint turn, jint pos) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    ++g_liveRevision;
    Game::getInstance()->getBoard()->remove(turn, pos);
}
JNIEXPORT BITBOARD JNICALL Java_jwtc_chess_JNI_getHashKey(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    return Game::getInstance()->getBoard()->getHashKey();
}
JNIEXPORT void JNICALL Java_jwtc_chess_JNI_interruptSearch(JNIEnv* env, jobject thiz, jlong id) {
    g_search.interrupt(id);
}
JNIEXPORT void JNICALL Java_jwtc_chess_JNI_interrupt(JNIEnv* env, jobject thiz) {
    g_search.interrupt();
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getNumCaptured(JNIEnv* env, jobject thiz, jint turn, jint piece) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    return Game::getInstance()->getBoard()->getNumCaptured(turn, piece);
}

// Scratch board: a clone of the live board with its own move stack, so callers
// (EcoService) can apply/undo candidate moves and read hashes without mutating
// the live game board.
JNIEXPORT void JNICALL Java_jwtc_chess_JNI_scratchSyncFromCurrent(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_liveMutex);
    g_scratchStack.clearHistory();
    Game::getInstance()->getBoard()->duplicate(g_scratchStack.current());
    g_scratchStack.current()->makeRoot();  // detach from the live history chain
    g_scratchStack.current()->getMoves();  // generate legal moves for requestMove / SAN
}

JNIEXPORT int JNICALL Java_jwtc_chess_JNI_scratchMove(JNIEnv* env, jobject thiz, jint move) {
    std::unique_ptr<ChessBoard> nb(new ChessBoard());
    ChessBoard* cur = g_scratchStack.current();
    boolean moved = cur->requestMove(move, nb.get(), &g_scratchTmp);
    if (moved) {
        nb->getMoves();  // ready the new node for deeper probing / SAN disambiguation
    }
    return (int) g_scratchStack.promoteOrDiscard(std::move(nb), moved);
}

JNIEXPORT BITBOARD JNICALL Java_jwtc_chess_JNI_scratchGetHashKey(JNIEnv* env, jobject thiz) {
    return g_scratchStack.current()->getHashKey();
}

JNIEXPORT jstring JNICALL Java_jwtc_chess_JNI_scratchGetMyMoveToString(JNIEnv* env, jobject thiz) {
    char buf[20];
    g_scratchStack.current()->myMoveToString(buf);
    return env->NewStringUTF(buf);
}

JNIEXPORT int JNICALL Java_jwtc_chess_JNI_scratchUndo(JNIEnv* env, jobject thiz) {
    return (int) g_scratchStack.undo();
}

// Evaluation settings stuff
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getEvalPropertyCount(JNIEnv* env, jobject thiz) {
    return 0;
}

JNIEXPORT jstring JNICALL Java_jwtc_chess_JNI_getEvalPropertyName(JNIEnv* env, jobject thiz, jint iProp) {
    char buf[20];

    return env->NewStringUTF(buf);
}

JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getEvalPropertyValue(JNIEnv* env, jobject thiz, jint iProp) {
    return 0;
}

JNIEXPORT void JNICALL Java_jwtc_chess_JNI_setEvalPropertyValue(JNIEnv* env, jobject thiz, jint iProp, jint value) {
}

static JNINativeMethod sMethods[] = {
    {"destroy", "()V", (void*) Java_jwtc_chess_JNI_destroy},
    {"setVariant", "(I)V", (void*) Java_jwtc_chess_JNI_setVariant},
    {"getVariant", "()I", (void*) Java_jwtc_chess_JNI_getVariant},
    {"requestMove", "(II)I", (void*) Java_jwtc_chess_JNI_requestMove},
    {"move", "(I)I", (void*) Java_jwtc_chess_JNI_move},
    {"requestDuckMove", "(I)I", (void*) Java_jwtc_chess_JNI_requestDuckMove},
    {"undo", "()V", (void*) Java_jwtc_chess_JNI_undo},
    {"reset", "()V", (void*) Java_jwtc_chess_JNI_reset},
    {"mewGameFromFEN", "(Ljava/lang/String)I", (void*) Java_jwtc_chess_JNI_newGameFromFEN},
    {"putPiece", "(III)V", (void*) Java_jwtc_chess_JNI_putPiece},
    {"prepareSearch", "(III)J", (void*) Java_jwtc_chess_JNI_prepareSearch},
    {"prepareHistorySearch", "(Ljava/lang/String;[III)J", (void*) Java_jwtc_chess_JNI_prepareHistorySearch},
    {"runSearch", "(J)V", (void*) Java_jwtc_chess_JNI_runSearch},
    {"isSearchCurrent", "(J)Z", (void*) Java_jwtc_chess_JNI_isSearchCurrent},
    {"getSearchResult", "(J)[I", (void*) Java_jwtc_chess_JNI_getSearchResult},
    {"getMove", "()I", (void*) Java_jwtc_chess_JNI_getMove},
    {"getDuckMove", "()I", (void*) Java_jwtc_chess_JNI_getDuckMove},
    {"getBoardValue", "()I", (void*) Java_jwtc_chess_JNI_getBoardValue},
    {"peekSearchDone", "()I", (void*) Java_jwtc_chess_JNI_peekSearchDone},
    {"peekSearchBestMove", "(I)I", (void*) Java_jwtc_chess_JNI_peekSearchBestMove},
    {"peekSearchBestDuckMove", "(I)I", (void*) Java_jwtc_chess_JNI_peekSearchBestDuckMove},
    {"peekSearchBestValue", "()I", (void*) Java_jwtc_chess_JNI_peekSearchBestValue},
    {"peekSearchDepth", "()I", (void*) Java_jwtc_chess_JNI_peekSearchDepth},
    {"getEvalCount", "()I", (void*) Java_jwtc_chess_JNI_getEvalCount},
    {"setPromo", "(I)V", (void*) Java_jwtc_chess_JNI_setPromo},
    {"getState", "()I", (void*) Java_jwtc_chess_JNI_getState},
    {"isEnded", "()I", (void*) Java_jwtc_chess_JNI_isEnded},
    {"setCastlingsEPAnd50", "(IIIIII)V", (void*) Java_jwtc_chess_JNI_setCastlingsEPAnd50},
    {"getWhiteCanCastleLong", "()I", (void*) Java_jwtc_chess_JNI_getWhiteCanCastleLong},
    {"getWhiteCanCastleShort", "()I", (void*) Java_jwtc_chess_JNI_getWhiteCanCastleShort},
    {"getBlackCanCastleLong", "()I", (void*) Java_jwtc_chess_JNI_getBlackCanCastleLong},
    {"getBlackCanCastleShort", "()I", (void*) Java_jwtc_chess_JNI_getBlackCanCastleShort},
    {"getEnpassantPosition", "()I", (void*) Java_jwtc_chess_JNI_getEnpassantPosition},
    {"get50MoveCount", "()I", (void*) Java_jwtc_chess_JNI_get50MoveCount},
    {"getNumBoard", "()I", (void*) Java_jwtc_chess_JNI_getNumBoard},
    {"getTurn", "()I", (void*) Java_jwtc_chess_JNI_getTurn},
    {"commitBoard", "()V", (void*) Java_jwtc_chess_JNI_commitBoard},
    {"setTurn", "(I)V", (void*) Java_jwtc_chess_JNI_setTurn},
    {"getMoveArraySize", "()I", (void*) Java_jwtc_chess_JNI_getMoveArraySize},
    {"getMoveArrayAt", "(I)I", (void*) Java_jwtc_chess_JNI_getMoveArrayAt},
    {"pieceAt", "(II)I", (void*) Java_jwtc_chess_JNI_pieceAt},
    {"countAttackersTo", "(II)I", (void*) Java_jwtc_chess_JNI_countAttackersTo},
    {"getAttackerPositionsTo", "(II)[I", (void*) Java_jwtc_chess_JNI_getAttackerPositionsTo},
    {"getDuckPos", "()I", (void*) Java_jwtc_chess_JNI_getDuckPos},
    {"getMyDuckPos", "()I", (void*) Java_jwtc_chess_JNI_getMyDuckPos},
    {"getMyMoveToString", "()Ljava/lang/String;", (void*) Java_jwtc_chess_JNI_getMyMoveToString},
    {"getMyMove", "()I", (void*) Java_jwtc_chess_JNI_getMyMove},
    {"isLegalPosition", "()I", (void*) Java_jwtc_chess_JNI_isLegalPosition},
    {"isAmbiguousCastle", "(II)I", (void*) Java_jwtc_chess_JNI_isAmbiguousCastle},
    {"doCastleMove", "(II)I", (void*) Java_jwtc_chess_JNI_doCastleMove},
    {"toFEN", "()Ljava/lang/String;", (void*) Java_jwtc_chess_JNI_toFEN},
    {"removePiece", "(II)V", (void*) Java_jwtc_chess_JNI_removePiece},
    {"getHashKey", "()J", (void*) Java_jwtc_chess_JNI_getHashKey},
    {"interrupt", "()V", (void*) Java_jwtc_chess_JNI_interrupt},
    {"getNumCaptured", "(II)I", (void*) Java_jwtc_chess_JNI_getNumCaptured},
    {"getEvalPropertyName", "(I)Ljava/lang/String;", (void*) Java_jwtc_chess_JNI_getEvalPropertyName},
    {"getEvalPropertyCount", "()I", (void*) Java_jwtc_chess_JNI_getEvalPropertyCount},
    {"getEvalPropertyValue", "(I)I", (void*) Java_jwtc_chess_JNI_getEvalPropertyValue},
    {"setEvalPropertyValue", "(II)V", (void*) Java_jwtc_chess_JNI_setEvalPropertyValue}};

extern "C" jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env = nullptr;
    jint result = -1;

    DEBUG_PRINT("JNI_OnLoad called\n");

    if (vm->GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK) {
        DEBUG_PRINT("vm->GetEnv failed\n");
        return result;
    }

    jniRegisterNativeMethods(env, "jwtc/chess/JNI", sMethods, 1);

    DEBUG_PRINT("Getting pointer to JavaVM...\n");
    if (env->GetJavaVM(&jvm) < 0) {
        DEBUG_PRINT("Could not get pointer to JavaVM\n");
    }

    DEBUG_PRINT("JNI_OnLoad is DONE!\n");

    return JNI_VERSION_1_4;
}

int jniRegisterNativeMethods(JNIEnv* env, const char* className, const JNINativeMethod* gMethods, int numMethods) {
    jclass clazz;

    DEBUG_PRINT("Registering %s natives\n", className);
    clazz = env->FindClass(className);
    if (clazz == nullptr) {
        DEBUG_PRINT("Native registration unable to find class '%s'\n", className);
        return -1;
    }

    if (env->RegisterNatives(clazz, gMethods, numMethods) < 0) {
        DEBUG_PRINT("RegisterNatives failed for '%s'\n", className);
        return -1;
    }

    return 0;
}
