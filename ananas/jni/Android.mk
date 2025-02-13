LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := photoprocessing

LOCAL_SRC_FILES := nanojpeg.c mem_utils.c bitmap.c bicubic_resize.c filter.c transform.c colour_space.c matrix.c blur.c photo_processing.c
LOCAL_LDLIBS    := -lm -llog -ljnigraphics -landroid $(extra_ldlibs)

include $(BUILD_SHARED_LIBRARY)