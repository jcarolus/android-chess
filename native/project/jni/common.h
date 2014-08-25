#ifndef COMMON_H
#define COMMON_H

#include <string.h>
#include <cstddef>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/time.h>

//#define stringtype std::string
//typedef stringtype String;

#define DEBUG_LEVEL 0 // bitmask for debug levels

#define DEBUG_MODE 0

#define TARGET_ANDROID 1

#if DEBUG_MODE
	#if TARGET_ANDROID
		#include <android/log.h>
		#define DEBUG_PRINT(s, args...) __android_log_print(ANDROID_LOG_INFO, "JNI-Chess-Game", s, args)
	#else
		#define DEBUG_PRINT(s, args...) fprintf(stdout, s, args)
	#endif
#else
	#define DEBUG_PRINT(s, args...) (s)
#endif

#ifdef _MSC_VER
	#define longlong __int64
#else
	#define longlong signed long long
#endif
typedef longlong BITBOARD;

#define MIN(a, b) (a < b ? (a) : (b))

#define boolint char
typedef boolint boolean;

#endif /* COMMON_H */
