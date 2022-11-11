#include "chess-jni.hpp"

static Game* stGame = NULL;
static JavaVM* jvm;
static jint stArrMoves[ChessBoard::MAX_MOVES];

static void* search_thread(void* arg) {
    JNIEnv* env;

    if (jvm->AttachCurrentThread(&env, NULL) != JNI_OK) {
        DEBUG_PRINT("Could not attach to current thread\n", 0);
        return NULL;
    }

    stGame->search();

    if (jvm->DetachCurrentThread() != JNI_OK) {
        DEBUG_PRINT("Could not deattach from current thread\n", 0);
    }
    return NULL;
}

JNIEXPORT void JNICALL Java_jwtc_chess_JNI_destroy(JNIEnv* env, jobject thiz) {
    if (stGame != NULL) {
        delete stGame;
        stGame = NULL;
    }
}

JNIEXPORT int JNICALL Java_jwtc_chess_JNI_isInited(JNIEnv* env, jobject thiz) {
    return stGame != NULL;
}

JNIEXPORT int JNICALL Java_jwtc_chess_JNI_requestMove(JNIEnv* env, jobject thiz, jint from, jint to) {
    return (int) stGame->requestMove(from, to);
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_move(JNIEnv* env, jobject thiz, jint move) {
    return (int) stGame->move(move);
}

JNIEXPORT int JNICALL Java_jwtc_chess_JNI_requestDuckMove(JNIEnv* env, jobject thiz, jint duckPos) {
    return (int) stGame->requestDuckMove(duckPos);
}

JNIEXPORT void JNICALL Java_jwtc_chess_JNI_undo(JNIEnv* env, jobject thiz) {
    stGame->undo();
}

JNIEXPORT void JNICALL Java_jwtc_chess_JNI_reset(JNIEnv* env, jobject thiz) {
    stGame->reset();
}
JNIEXPORT void JNICALL Java_jwtc_chess_JNI_putPiece(JNIEnv* env, jobject thiz, jint pos, jint piece, jint turn) {
    ChessBoard* board = stGame->getBoard();
    board->put(pos, piece, turn);
}

JNIEXPORT int JNICALL Java_jwtc_chess_JNI_newGameFromFEN(JNIEnv* env, jobject thiz, jstring str) {
    jboolean isCopy;
    const char* strChars = env->GetStringUTFChars(str, &isCopy);
    char* sFEN = strdup(strChars);

    if (isCopy == JNI_TRUE) {
        env->ReleaseStringUTFChars(str, strChars);
    }
    boolean ret = stGame->newGameFromFEN(sFEN);

    delete sFEN;
    return ret;
}

JNIEXPORT void JNICALL Java_jwtc_chess_JNI_searchMove(JNIEnv* env, jobject thiz, jint msecs) {
    pthread_t tid;

    stGame->setSearchTime(msecs);
    pthread_create(&tid, NULL, search_thread, NULL);
}
JNIEXPORT void JNICALL Java_jwtc_chess_JNI_searchDepth(JNIEnv* env, jobject thiz, jint depth) {
    pthread_t tid;

    stGame->setSearchLimit(depth);
    pthread_create(&tid, NULL, search_thread, NULL);
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getMove(JNIEnv* env, jobject thiz) {
    return stGame->getBestMove();
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getDuckMove(JNIEnv* env, jobject thiz) {
    return stGame->getBestDuckMove();
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getBoardValue(JNIEnv* env, jobject thiz) {
    return stGame->getBoard()->boardValueExtension();
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_peekSearchDone(JNIEnv* env, jobject thiz) {
    return stGame->m_bSearching ? 0 : 1;
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_peekSearchBestMove(JNIEnv* env, jobject thiz, jint ply) {
    return stGame->getBestMoveAt(ply);
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_peekSearchBestValue(JNIEnv* env, jobject thiz) {
    return stGame->m_bestValue;
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_peekSearchDepth(JNIEnv* env, jobject thiz) {
    return stGame->m_searchDepth;
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getEvalCount(JNIEnv* env, jobject thiz) {
    return stGame->m_evalCount;
}
JNIEXPORT void JNICALL Java_jwtc_chess_JNI_setPromo(JNIEnv* env, jobject thiz, jint piece) {
    stGame->setPromo(piece);
}

JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getState(JNIEnv* env, jobject thiz) {
    return stGame->getBoard()->getState();
}

JNIEXPORT int JNICALL Java_jwtc_chess_JNI_isEnded(JNIEnv* env, jobject thiz) {
    return stGame->getBoard()->isEnded();
}

JNIEXPORT void JNICALL Java_jwtc_chess_JNI_setCastlingsEPAnd50(JNIEnv* env,
                                                               jobject thiz,
                                                               jint wccl,
                                                               jint wccs,
                                                               jint bccl,
                                                               jint bccs,
                                                               jint ep,
                                                               jint r50) {
    stGame->getBoard()->setCastlingsEPAnd50(wccl, wccs, bccl, bccs, ep, r50);
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getNumBoard(JNIEnv* env, jobject thiz) {
    return stGame->getBoard()->getNumBoard();
}
JNIEXPORT void JNICALL Java_jwtc_chess_JNI_commitBoard(JNIEnv* env, jobject thiz) {
    stGame->commitBoard();
}
JNIEXPORT void JNICALL Java_jwtc_chess_JNI_setTurn(JNIEnv* env, jobject thiz, jint turn) {
    stGame->getBoard()->setTurn(turn);
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getMoveArraySize(JNIEnv* env, jobject thiz) {
    ChessBoard* board = stGame->getBoard();
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
    return stGame->getBoard()->getTurn();
}

JNIEXPORT int JNICALL Java_jwtc_chess_JNI_pieceAt(JNIEnv* env, jobject thiz, jint turn, jint pos) {
    return stGame->getBoard()->pieceAt(turn, pos);
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getDuckPos(JNIEnv* env, jobject thiz) {
    return stGame->getBoard()->getDuckPos();
}

JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getMyDuckPos(JNIEnv* env, jobject thiz) {
    return stGame->getBoard()->getMyDuckPos();
}

JNIEXPORT jstring JNICALL Java_jwtc_chess_JNI_getMyMoveToString(JNIEnv* env, jobject thiz) {
    char buf[20];
    stGame->getBoard()->myMoveToString(buf);
    return env->NewStringUTF(buf);
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getMyMove(JNIEnv* env, jobject thiz) {
    return stGame->getBoard()->getMyMove();
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_isLegalPosition(JNIEnv* env, jobject thiz) {
    return stGame->getBoard()->isLegalPosition();
}

JNIEXPORT int JNICALL Java_jwtc_chess_JNI_isAmbiguousCastle(JNIEnv* env, jobject thiz, jint from, jint to) {
    return stGame->getBoard()->isAmbiguousCastle(from, to);
}

JNIEXPORT void JNICALL Java_jwtc_chess_JNI_doCastleMove(JNIEnv* env, jobject thiz, jint from, jint to) {
    int move = stGame->getBoard()->getCastleMove(from, to);
    stGame->move(move);
}

JNIEXPORT jstring JNICALL Java_jwtc_chess_JNI_toFEN(JNIEnv* env, jobject thiz) {
    char buf[255];
    stGame->getBoard()->toFEN(buf);
    return env->NewStringUTF(buf);
}

JNIEXPORT void JNICALL Java_jwtc_chess_JNI_removePiece(JNIEnv* env, jobject thiz, jint turn, jint pos) {
    stGame->getBoard()->remove(turn, pos);
}
JNIEXPORT BITBOARD JNICALL Java_jwtc_chess_JNI_getHashKey(JNIEnv* env, jobject thiz) {
    return stGame->getBoard()->getHashKey();
}
JNIEXPORT void JNICALL Java_jwtc_chess_JNI_loadDB(JNIEnv* env, jobject thiz, jstring sFile, jint depth) {
    const char* nativeString = env->GetStringUTFChars(sFile, 0);
    stGame->loadDB(nativeString, depth);
    env->ReleaseStringUTFChars(sFile, nativeString);
}
JNIEXPORT void JNICALL Java_jwtc_chess_JNI_interrupt(JNIEnv* env, jobject thiz) {
    stGame->m_bInterrupted = true;
}
JNIEXPORT int JNICALL Java_jwtc_chess_JNI_getNumCaptured(JNIEnv* env, jobject thiz, jint turn, jint piece) {
    return stGame->getBoard()->getNumCaptured(turn, piece);
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
    {"isInited", "()I", (void*) Java_jwtc_chess_JNI_isInited},
    {"requestMove", "(II)I", (void*) Java_jwtc_chess_JNI_requestMove},
    {"move", "(I)I", (void*) Java_jwtc_chess_JNI_move},
    {"requestDuckMove", "(I)I", (void*) Java_jwtc_chess_JNI_requestDuckMove},
    {"undo", "()V", (void*) Java_jwtc_chess_JNI_undo},
    {"reset", "()V", (void*) Java_jwtc_chess_JNI_reset},
    {"mewGameFromFEN", "(Ljava/lang/String)I", (void*) Java_jwtc_chess_JNI_newGameFromFEN},
    {"putPiece", "(III)V", (void*) Java_jwtc_chess_JNI_putPiece},
    {"searchMove", "(I)V", (void*) Java_jwtc_chess_JNI_searchMove},
    {"searchDepth", "(I)V", (void*) Java_jwtc_chess_JNI_searchDepth},
    {"getMove", "()I", (void*) Java_jwtc_chess_JNI_getMove},
    {"getDuckMove", "()I", (void*) Java_jwtc_chess_JNI_getDuckMove},
    {"getBoardValue", "()I", (void*) Java_jwtc_chess_JNI_getBoardValue},
    {"peekSearchDone", "()I", (void*) Java_jwtc_chess_JNI_peekSearchDone},
    {"peekSearchBestMove", "(I)I", (void*) Java_jwtc_chess_JNI_peekSearchBestMove},
    {"peekSearchBestValue", "()I", (void*) Java_jwtc_chess_JNI_peekSearchBestValue},
    {"peekSearchDepth", "()I", (void*) Java_jwtc_chess_JNI_peekSearchDepth},
    {"getEvalCount", "()I", (void*) Java_jwtc_chess_JNI_getEvalCount},
    {"setPromo", "(I)V", (void*) Java_jwtc_chess_JNI_setPromo},
    {"getState", "()I", (void*) Java_jwtc_chess_JNI_getState},
    {"isEnded", "()I", (void*) Java_jwtc_chess_JNI_isEnded},
    {"setCastlingsEPAnd50", "(IIIIII)V", (void*) Java_jwtc_chess_JNI_setCastlingsEPAnd50},
    {"getNumBoard", "()I", (void*) Java_jwtc_chess_JNI_getNumBoard},
    {"getTurn", "()I", (void*) Java_jwtc_chess_JNI_getTurn},
    {"commitBoard", "()V", (void*) Java_jwtc_chess_JNI_commitBoard},
    {"setTurn", "(I)V", (void*) Java_jwtc_chess_JNI_setTurn},
    {"getMoveArraySize", "()I", (void*) Java_jwtc_chess_JNI_getMoveArraySize},
    {"getMoveArrayAt", "(I)I", (void*) Java_jwtc_chess_JNI_getMoveArrayAt},
    {"pieceAt", "(II)I", (void*) Java_jwtc_chess_JNI_pieceAt},
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
    {"loadDB", "(Ljava/lang/String;I)V", (void*) Java_jwtc_chess_JNI_loadDB},
    {"interrupt", "()V", (void*) Java_jwtc_chess_JNI_interrupt},
    {"getNumCaptured", "(II)I", (void*) Java_jwtc_chess_JNI_getNumCaptured},
    {"getEvalPropertyName", "(I)Ljava/lang/String;", (void*) Java_jwtc_chess_JNI_getEvalPropertyName},
    {"getEvalPropertyCount", "()I", (void*) Java_jwtc_chess_JNI_getEvalPropertyCount},
    {"getEvalPropertyValue", "(I)I", (void*) Java_jwtc_chess_JNI_getEvalPropertyValue},
    {"setEvalPropertyValue", "(II)V", (void*) Java_jwtc_chess_JNI_setEvalPropertyValue}};

extern "C" jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env = NULL;
    jint result = -1;

    DEBUG_PRINT("JNI_OnLoad called\n", 0);

    if (vm->GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK) {
        DEBUG_PRINT("vm->GetEnv failed\n", 0);
        return result;
    }

    jniRegisterNativeMethods(env, "jwtc/chess/JNI", sMethods, 1);

    DEBUG_PRINT("Getting pointer to JavaVM...\n", 0);
    if (env->GetJavaVM(&jvm) < 0) {
        DEBUG_PRINT("Could not get pointer to JavaVM\n", 0);
    }

    ChessBoard::initStatics();
    stGame = new Game();

    DEBUG_PRINT("JNI_OnLoad is DONE!\n", 0);

    return JNI_VERSION_1_4;
}

int jniRegisterNativeMethods(JNIEnv* env, const char* className, const JNINativeMethod* gMethods, int numMethods) {
    jclass clazz;

    DEBUG_PRINT("Registering %s natives\n", className);
    clazz = env->FindClass(className);
    if (clazz == NULL) {
        DEBUG_PRINT("Native registration unable to find class '%s'\n", className);
        return -1;
    }

    if (env->RegisterNatives(clazz, gMethods, numMethods) < 0) {
        DEBUG_PRINT("RegisterNatives failed for '%s'\n", className);
        return -1;
    }

    return 0;
}
