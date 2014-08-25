LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_DEFAULT_CPP_EXTENSION := cpp

LOCAL_MODULE    := chess-jni
LOCAL_SRC_FILES := chess-jni.cpp\
		Pos.cpp\
		Move.cpp\
		ChessBoard.cpp\
		Game.cpp

LOCAL_LDLIBS := -llog

include $(BUILD_SHARED_LIBRARY)
