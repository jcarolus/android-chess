LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := rust-chess-core
LOCAL_SRC_FILES := ../../rust/target/android/$(TARGET_ARCH_ABI)/librust_chess_bridge.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := rust-chess-jni
LOCAL_SRC_FILES := ../../rust/jni/rust-chess-jni.cpp\
		../../rust/jni/rust-board-jni.cpp
LOCAL_STATIC_LIBRARIES := rust-chess-core
LOCAL_LDLIBS := -llog -ldl -lm
LOCAL_CPPFLAGS += -fno-sized-deallocation
include $(BUILD_SHARED_LIBRARY)
