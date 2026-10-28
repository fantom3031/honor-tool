LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := doomhook
LOCAL_SRC_FILES := doomhook.c
LOCAL_CFLAGS := -O2 -fvisibility=default
LOCAL_LDLIBS := -llog

include $(BUILD_SHARED_LIBRARY)
