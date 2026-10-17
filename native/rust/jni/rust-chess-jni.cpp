#include <jni.h>
#include <cstdint>
#include <string>
#include <vector>

struct NativeResult {
    uint64_t id;
    int32_t score, depth;
    uint64_t nodes;
    int32_t hasEvaluation, interrupted;
    char best[6], error[256];
};
extern "C" {
void* rust_chess_create();
void rust_chess_stop(const void*);
void rust_chess_destroy(void*);
void rust_chess_search(const void*, uint64_t, int32_t, const char*, const char* const*, size_t,
                       int32_t, int32_t, int32_t, NativeResult*);
}

static void fail(JNIEnv* env, const char* message) {
    jclass type = env->FindClass("java/lang/IllegalArgumentException");
    if (type) env->ThrowNew(type, message);
}
static bool text(JNIEnv* env, jstring input, std::string& output) {
    if (!input) { fail(env, "Missing search text"); return false; }
    const char* value = env->GetStringUTFChars(input, nullptr);
    if (!value) return false;
    output = value;
    env->ReleaseStringUTFChars(input, value);
    return true;
}
extern "C" JNIEXPORT jlong JNICALL
Java_jwtc_android_chess_engine_RustNative_create(JNIEnv*, jclass) {
    return reinterpret_cast<jlong>(rust_chess_create());
}
extern "C" JNIEXPORT void JNICALL
Java_jwtc_android_chess_engine_RustNative_stop(JNIEnv*, jclass, jlong handle) {
    rust_chess_stop(reinterpret_cast<void*>(handle));
}
extern "C" JNIEXPORT void JNICALL
Java_jwtc_android_chess_engine_RustNative_destroy(JNIEnv*, jclass, jlong handle) {
    rust_chess_destroy(reinterpret_cast<void*>(handle));
}
extern "C" JNIEXPORT jobject JNICALL
Java_jwtc_android_chess_engine_RustNative_search(JNIEnv* env, jclass, jlong handle, jlong id,
        jint variant, jstring fen, jobjectArray history, jint depth, jint millis, jboolean quiescence) {
    if (!handle || !history) { fail(env, "Missing search handle or history"); return nullptr; }
    std::string startingPosition;
    if (!text(env, fen, startingPosition)) return nullptr;
    jsize count = env->GetArrayLength(history);
    if (count > 100000) { fail(env, "History too long"); return nullptr; }
    std::vector<std::string> moves(count);
    std::vector<const char*> pointers(count);
    for (jsize i = 0; i < count; ++i) {
        auto move = static_cast<jstring>(env->GetObjectArrayElement(history, i));
        bool ok = text(env, move, moves[i]);
        if (move) env->DeleteLocalRef(move);
        if (!ok) return nullptr;
        pointers[i] = moves[i].c_str();
    }
    NativeResult result{};
    rust_chess_search(reinterpret_cast<void*>(handle), id, variant, startingPosition.c_str(),
                      pointers.data(), pointers.size(), depth, millis, quiescence, &result);
    if (result.error[0]) { fail(env, result.error); return nullptr; }
    jclass type = env->FindClass("jwtc/android/chess/engine/RustSearchResult");
    if (!type) return nullptr;
    jmethodID constructor = env->GetMethodID(type, "<init>", "(JLjava/lang/String;IIJZZ)V");
    if (!constructor) return nullptr;
    jstring best = env->NewStringUTF(result.best);
    if (!best) return nullptr;
    return env->NewObject(type, constructor, static_cast<jlong>(result.id), best, result.score,
                          result.depth, static_cast<jlong>(result.nodes),
                          static_cast<jboolean>(result.hasEvaluation), static_cast<jboolean>(result.interrupted));
}
