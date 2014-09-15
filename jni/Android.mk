LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_C_INCLUDES := $(LOCAL_PATH)
LOCAL_MODULE     := mpg123_jni
LOCAL_ARM_MODE   := arm
LOCAL_LDLIBS     := -llog
LOCAL_CFLAGS     := -DACCURATE_ROUNDING \
                        -DOPT_ARM \
                        -DREAL_IS_FIXED \
                        -DNO_REAL \
                        -DNO_32BIT \
                        -DHAVE_STRERROR \
                        -DASMALIGN_BYTE \
                        -Wno-int-to-pointer-cast \
                        -Wno-pointer-to-int-cast

LOCAL_ASFLAGS	 := -DASMALIGN_BYTE

LOCAL_SRC_FILES :=  Mpg123Decoder.c
LOCAL_SRC_FILES +=  libmpg123/compat.c
LOCAL_SRC_FILES +=  libmpg123/frame.c
LOCAL_SRC_FILES +=  libmpg123/id3.c
LOCAL_SRC_FILES +=  libmpg123/format.c
LOCAL_SRC_FILES +=  libmpg123/stringbuf.c
LOCAL_SRC_FILES +=  libmpg123/libmpg123.c
LOCAL_SRC_FILES +=  libmpg123/readers.c
LOCAL_SRC_FILES +=  libmpg123/icy.c
LOCAL_SRC_FILES +=  libmpg123/icy2utf8.c
LOCAL_SRC_FILES +=  libmpg123/index.c
LOCAL_SRC_FILES +=  libmpg123/layer1.c
LOCAL_SRC_FILES +=  libmpg123/layer2.c
LOCAL_SRC_FILES +=  libmpg123/layer3.c
LOCAL_SRC_FILES +=  libmpg123/parse.c
LOCAL_SRC_FILES +=  libmpg123/optimize.c
LOCAL_SRC_FILES +=  libmpg123/synth.c
LOCAL_SRC_FILES +=  libmpg123/synth_8bit.c
LOCAL_SRC_FILES +=  libmpg123/synth_arm.S
LOCAL_SRC_FILES +=  libmpg123/ntom.c
LOCAL_SRC_FILES +=  libmpg123/dct64.c
LOCAL_SRC_FILES +=  libmpg123/equalizer.c
LOCAL_SRC_FILES +=  libmpg123/dither.c
LOCAL_SRC_FILES +=  libmpg123/tabinit.c
LOCAL_SRC_FILES +=  libmpg123/synth_arm_accurate.S
LOCAL_SRC_FILES +=  libmpg123/feature.c

LOCAL_SHARED_LIBRARIES += liblog

include $(BUILD_SHARED_LIBRARY)
