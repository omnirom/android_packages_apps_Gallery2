LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_STATIC_ANDROID_LIBRARIES := \
        androidx.legacy_legacy-support-core-ui \
        androidx.core_core \
        androidx.legacy_legacy-support-v13 \
        androidx.fragment_fragment

LOCAL_STATIC_JAVA_LIBRARIES := \
    xmp_toolkit \
    mp4parser

LOCAL_SRC_FILES := \
    $(call all-java-files-under, src) \
    $(call all-renderscript-files-under, src)

LOCAL_RESOURCE_DIR += $(LOCAL_PATH)/res

LOCAL_AAPT_FLAGS := --auto-add-overlay

LOCAL_USE_AAPT2 := true

LOCAL_PACKAGE_NAME := Gallery2

LOCAL_OVERRIDES_PACKAGES := Gallery Gallery3D GalleryNew3D

LOCAL_SDK_VERSION := current
LOCAL_MIN_SDK_VERSION := 24

LOCAL_JNI_SHARED_LIBRARIES := \
    libjni_eglfence \
    libjni_filtershow_filters \
    libjni_jpegstream
LOCAL_MULTILIB := both

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_JAVA_LIBRARIES := org.apache.http.legacy

include $(BUILD_PACKAGE)

ifeq ($(strip $(LOCAL_PACKAGE_OVERRIDES)),)

include $(call all-makefiles-under, $(LOCAL_PATH))

endif
