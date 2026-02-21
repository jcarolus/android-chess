LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_DEFAULT_CPP_EXTENSION := cpp

LOCAL_MODULE    := chess-jni
LOCAL_SRC_FILES := chess-jni.cpp\
		BoardStack.cpp\
		SearchWorkspace.cpp\
		Pos.cpp\
		Move.cpp\
		ChessBoard.cpp\
		ChessBoardState.cpp\
		ChessBoardNotation.cpp\
		Game.cpp\
		GameSearch.cpp

LOCAL_LDLIBS := -llog
LOCAL_CPPFLAGS += -fno-sized-deallocation

include $(BUILD_SHARED_LIBRARY)
