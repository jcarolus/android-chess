#include <jni.h>
#include <cstdint>
#include <string>
#include <vector>

struct BoardResult {
    int32_t turnWhite, gameState, inCheck;
    uint32_t halfmoveClock, repetitionCount;
    int32_t castling;
    uint64_t hashKey;
    uint32_t captured[10];
    uint8_t piece;
    char fen[96], san[24], moves[32768], attackers[256], turns[8192], error[256];
};
extern "C" {
void rust_board_query(int32_t, const char*, const char* const*, size_t, BoardResult*);
void rust_board_legal_moves(int32_t, const char*, const char* const*, size_t, BoardResult*);
void rust_board_legal_turns(int32_t, const char*, const char* const*, size_t, BoardResult*);
void rust_board_apply_move(int32_t, const char*, const char* const*, size_t, const char*, BoardResult*);
void rust_board_san(int32_t, const char*, const char* const*, size_t, const char*, BoardResult*);
void rust_board_piece_at(int32_t, const char*, const char* const*, size_t, const char*, BoardResult*);
void rust_board_attackers(int32_t, const char*, const char* const*, size_t, int32_t, const char*, BoardResult*);
}

static void fail(JNIEnv* env, const char* message) {
    jclass type = env->FindClass("java/lang/IllegalArgumentException");
    if (type) env->ThrowNew(type, message);
}
static bool text(JNIEnv* env, jstring input, std::string& output) {
    if (!input) { fail(env, "Missing board text"); return false; }
    const char* value = env->GetStringUTFChars(input, nullptr);
    if (!value) return false;
    output = value;
    env->ReleaseStringUTFChars(input, value);
    return true;
}
static bool readHistory(JNIEnv* env, jobjectArray history, std::vector<std::string>& moves,
                        std::vector<const char*>& pointers) {
    if (!history) { fail(env, "Missing history"); return false; }
    jsize count = env->GetArrayLength(history);
    if (count > 100000) { fail(env, "History too long"); return false; }
    moves.resize(count);
    pointers.resize(count);
    for (jsize i = 0; i < count; ++i) {
        auto move = static_cast<jstring>(env->GetObjectArrayElement(history, i));
        bool ok = text(env, move, moves[i]);
        if (move) env->DeleteLocalRef(move);
        if (!ok) return false;
        pointers[i] = moves[i].c_str();
    }
    return true;
}
static jobject toJava(JNIEnv* env, const BoardResult& result) {
    jclass type = env->FindClass("jwtc/android/chess/engine/RustBoardResult");
    if (!type) return nullptr;
    jmethodID constructor = env->GetMethodID(type, "<init>",
        "(ZIZJJZZZZJ[ICLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
    if (!constructor) return nullptr;
    jintArray captured = env->NewIntArray(10);
    if (!captured) return nullptr;
    jint boxed[10];
    for (int i = 0; i < 10; ++i) boxed[i] = static_cast<jint>(result.captured[i]);
    env->SetIntArrayRegion(captured, 0, 10, boxed);
    jstring fen = env->NewStringUTF(result.fen);
    jstring san = env->NewStringUTF(result.san);
    jstring moves = env->NewStringUTF(result.moves);
    jstring attackers = env->NewStringUTF(result.attackers);
    jstring turns = env->NewStringUTF(result.turns);
    if (!fen || !san || !moves || !attackers || !turns) return nullptr;
    jobject value = env->NewObject(type, constructor,
        static_cast<jboolean>(result.turnWhite), result.gameState, static_cast<jboolean>(result.inCheck),
        static_cast<jlong>(result.halfmoveClock), static_cast<jlong>(result.repetitionCount),
        static_cast<jboolean>((result.castling & 1) != 0), static_cast<jboolean>((result.castling & 2) != 0),
        static_cast<jboolean>((result.castling & 4) != 0), static_cast<jboolean>((result.castling & 8) != 0),
        static_cast<jlong>(result.hashKey), captured, static_cast<jchar>(result.piece), fen, san, moves, attackers, turns);
    env->DeleteLocalRef(captured);
    env->DeleteLocalRef(fen);
    env->DeleteLocalRef(san);
    env->DeleteLocalRef(moves);
    env->DeleteLocalRef(attackers);
    env->DeleteLocalRef(turns);
    return value;
}
extern "C" JNIEXPORT jobject JNICALL
Java_jwtc_android_chess_engine_RustBoardNative_query(JNIEnv* env, jclass, jint variant, jstring fen, jobjectArray history) {
    std::string startingPosition;
    if (!text(env, fen, startingPosition)) return nullptr;
    std::vector<std::string> moves;
    std::vector<const char*> pointers;
    if (!readHistory(env, history, moves, pointers)) return nullptr;
    BoardResult result{};
    rust_board_query(variant, startingPosition.c_str(), pointers.data(), pointers.size(), &result);
    if (result.error[0]) { fail(env, result.error); return nullptr; }
    return toJava(env, result);
}
extern "C" JNIEXPORT jobject JNICALL
Java_jwtc_android_chess_engine_RustBoardNative_legalMoves(JNIEnv* env, jclass, jint variant, jstring fen, jobjectArray history) {
    std::string startingPosition;
    if (!text(env, fen, startingPosition)) return nullptr;
    std::vector<std::string> moves;
    std::vector<const char*> pointers;
    if (!readHistory(env, history, moves, pointers)) return nullptr;
    BoardResult result{};
    rust_board_legal_moves(variant, startingPosition.c_str(), pointers.data(), pointers.size(), &result);
    if (result.error[0]) { fail(env, result.error); return nullptr; }
    return toJava(env, result);
}
extern "C" JNIEXPORT jobject JNICALL
Java_jwtc_android_chess_engine_RustBoardNative_legalTurns(JNIEnv* env, jclass, jint variant, jstring fen, jobjectArray history) {
    std::string startingPosition;
    if (!text(env, fen, startingPosition)) return nullptr;
    std::vector<std::string> moves;
    std::vector<const char*> pointers;
    if (!readHistory(env, history, moves, pointers)) return nullptr;
    BoardResult result{};
    rust_board_legal_turns(variant, startingPosition.c_str(), pointers.data(), pointers.size(), &result);
    if (result.error[0]) { fail(env, result.error); return nullptr; }
    return toJava(env, result);
}
extern "C" JNIEXPORT jobject JNICALL
Java_jwtc_android_chess_engine_RustBoardNative_applyMove(JNIEnv* env, jclass, jint variant, jstring fen,
        jobjectArray history, jstring move) {
    std::string startingPosition, coordinate;
    if (!text(env, fen, startingPosition) || !text(env, move, coordinate)) return nullptr;
    std::vector<std::string> moves;
    std::vector<const char*> pointers;
    if (!readHistory(env, history, moves, pointers)) return nullptr;
    BoardResult result{};
    rust_board_apply_move(variant, startingPosition.c_str(), pointers.data(), pointers.size(),
                          coordinate.c_str(), &result);
    if (result.error[0]) { fail(env, result.error); return nullptr; }
    return toJava(env, result);
}
extern "C" JNIEXPORT jobject JNICALL
Java_jwtc_android_chess_engine_RustBoardNative_san(JNIEnv* env, jclass, jint variant, jstring fen,
        jobjectArray history, jstring move) {
    std::string startingPosition, coordinate;
    if (!text(env, fen, startingPosition) || !text(env, move, coordinate)) return nullptr;
    std::vector<std::string> moves;
    std::vector<const char*> pointers;
    if (!readHistory(env, history, moves, pointers)) return nullptr;
    BoardResult result{};
    rust_board_san(variant, startingPosition.c_str(), pointers.data(), pointers.size(),
                   coordinate.c_str(), &result);
    if (result.error[0]) { fail(env, result.error); return nullptr; }
    return toJava(env, result);
}
extern "C" JNIEXPORT jobject JNICALL
Java_jwtc_android_chess_engine_RustBoardNative_pieceAt(JNIEnv* env, jclass, jint variant, jstring fen,
        jobjectArray history, jstring square) {
    std::string startingPosition, target;
    if (!text(env, fen, startingPosition) || !text(env, square, target)) return nullptr;
    std::vector<std::string> moves;
    std::vector<const char*> pointers;
    if (!readHistory(env, history, moves, pointers)) return nullptr;
    BoardResult result{};
    rust_board_piece_at(variant, startingPosition.c_str(), pointers.data(), pointers.size(),
                        target.c_str(), &result);
    if (result.error[0]) { fail(env, result.error); return nullptr; }
    return toJava(env, result);
}
extern "C" JNIEXPORT jobject JNICALL
Java_jwtc_android_chess_engine_RustBoardNative_attackers(JNIEnv* env, jclass, jint variant, jstring fen,
        jobjectArray history, jboolean byWhite, jstring square) {
    std::string startingPosition, target;
    if (!text(env, fen, startingPosition) || !text(env, square, target)) return nullptr;
    std::vector<std::string> moves;
    std::vector<const char*> pointers;
    if (!readHistory(env, history, moves, pointers)) return nullptr;
    BoardResult result{};
    rust_board_attackers(variant, startingPosition.c_str(), pointers.data(), pointers.size(),
                         byWhite, target.c_str(), &result);
    if (result.error[0]) { fail(env, result.error); return nullptr; }
    return toJava(env, result);
}
