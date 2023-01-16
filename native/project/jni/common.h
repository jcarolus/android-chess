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
#define DEBUG_PRINT(x...) do { \
  char buf[512]; \
  sprintf(buf, x); \
  __android_log_print(ANDROID_LOG_ERROR,"JNI-CHESS", "%s | %s:%i", buf, __FILE__, __LINE__); \
} while (0)
#else
#define DEBUG_PRINT(s, args...) fprintf(stdout, s, args)
#endif
#else
#define DEBUG_PRINT(s, args...)
#endif

#ifdef _MSC_VER
#define longlong __int64
#else
#define longlong unsigned long long
#endif
typedef longlong BITBOARD;

#define MIN(a, b) (a < b ? (a) : (b))

#define boolint char
typedef boolint boolean;

#endif /* COMMON_H */
