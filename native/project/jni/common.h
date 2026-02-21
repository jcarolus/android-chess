#ifndef COMMON_H
#define COMMON_H

#include <string.h>
#include <cstddef>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/time.h>

#define DEBUG_LEVEL 0  // bitmask for debug levels

#ifdef TERMINAL
#define DEBUG_MODE     1
#define TARGET_ANDROID 0
#else
#define DEBUG_MODE     1
#define TARGET_ANDROID 1
#endif

#if DEBUG_MODE
#if TARGET_ANDROID
#include <android/log.h>
#define DEBUG_PRINT(fmt, ...) do { \
  char buf[512]; \
  snprintf(buf, sizeof(buf), fmt, ##__VA_ARGS__); \
  __android_log_print(ANDROID_LOG_ERROR,"JNI-CHESS", "%s | %s:%i", buf, __FILE__, __LINE__); \
} while (0)
#else
#define DEBUG_PRINT(fmt, ...) fprintf(stdout, fmt, ##__VA_ARGS__)
#endif
#else
#define DEBUG_PRINT(fmt, ...)
#endif

#ifdef _MSC_VER
using longlong = __int64;
#else
using longlong = unsigned long long;
#endif
using BITBOARD = longlong;

template <typename T>
constexpr T chessMin(T a, T b) {
    return a < b ? a : b;
}

using boolean = char;

#endif /* COMMON_H */
