# Android APK only. Preserve desktop build configuration and the synthesis code.
if(NOT ANDROID)
    message(FATAL_ERROR "Android audio optimisation profile used outside Android")
endif()
# PERF-ANDROID-1: desktop builds already enable whole-program optimisation.
# Keep the debug APK debuggable, but never ship an unoptimised audio engine.
# ThinLTO also sees the H8S executor/read/write helpers across translation units.
option(RIMS_ANDROID_THINLTO "Cross-file optimisation for Android audio" ON)
foreach(t ms2000 dsp56kEmu dsp56kBase asmjit rims2000r_native)
    target_compile_options(${t} PRIVATE -O3)
    # JNI/C entry points carry explicit default visibility; everything else is internal.
    set_target_properties(${t} PROPERTIES CXX_VISIBILITY_PRESET hidden VISIBILITY_INLINES_HIDDEN ON)
    target_compile_definitions(${t} PRIVATE NDEBUG)
    if(RIMS_ANDROID_THINLTO)
        target_compile_options(${t} PRIVATE -flto=thin)
    endif()
endforeach()
if(RIMS_ANDROID_THINLTO)
    target_link_options(rims2000r_native PRIVATE -flto=thin)
endif()
target_compile_definitions(rims2000r_native PRIVATE RIMS_ANDROID_THINLTO=$<BOOL:${RIMS_ANDROID_THINLTO}>)
message(STATUS "RiMS2000R: Android audio core -O3, ThinLTO=${RIMS_ANDROID_THINLTO}; strict FP, no clock changes")
