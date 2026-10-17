LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_DEFAULT_CPP_EXTENSION := cpp

LOCAL_MODULE    := chess-jni
LOCAL_SRC_FILES := chess-jni.cpp\
		BoardStack.cpp\
		SearchController.cpp\
		SearchSession.cpp\
		SearchWorkspace.cpp\
		Pos.cpp\
		Move.cpp\
		ChessBoard.cpp\
		BoardEvaluation.cpp\
		ChessBoardState.cpp\
		ChessBoardNotation.cpp\
		Game.cpp\
		GameSearch.cpp

LOCAL_LDLIBS := -llog
LOCAL_CPPFLAGS += -fno-sized-deallocation

include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := rust-chess-core
LOCAL_SRC_FILES := ../../rust/target/android/$(TARGET_ARCH_ABI)/librust_chess_bridge.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := rust-chess-jni
LOCAL_SRC_FILES := ../../rust/jni/rust-chess-jni.cpp
LOCAL_STATIC_LIBRARIES := rust-chess-core
LOCAL_LDLIBS := -llog -ldl -lm
LOCAL_CPPFLAGS += -fno-sized-deallocation
include $(BUILD_SHARED_LIBRARY)
