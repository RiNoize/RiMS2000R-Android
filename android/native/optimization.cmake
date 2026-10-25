# Android APK only. Preserve desktop build configuration and the synthesis code.
if(NOT ANDROID)
    message(FATAL_ERROR "Android audio optimisation profile used outside Android")
endif()
# Same boundary as the upstream desktop LTO profile: whole-program optimisation
# of the H8S/core + host bridge, not of the separately maintained DSP JIT.
option(RIMS_ANDROID_THINLTO "Cross-file optimisation for Android H8S/core" ON)
foreach(t ms2000 dsp56kEmu dsp56kBase asmjit rims2000r_native)
    target_compile_options(${t} PRIVATE -O3)
    target_compile_definitions(${t} PRIVATE NDEBUG)
endforeach()
foreach(t ms2000 rims2000r_native)
    # JNI/C entry points carry explicit default visibility.
    set_target_properties(${t} PROPERTIES CXX_VISIBILITY_PRESET hidden VISIBILITY_INLINES_HIDDEN ON)
    if(RIMS_ANDROID_THINLTO)
        target_compile_options(${t} PRIVATE -flto=thin)
    endif()
endforeach()
if(RIMS_ANDROID_THINLTO)
    target_link_options(rims2000r_native PRIVATE -flto=thin)
endif()
target_compile_definitions(rims2000r_native PRIVATE RIMS_ANDROID_THINLTO=$<BOOL:${RIMS_ANDROID_THINLTO}>)
message(STATUS "RiMS2000R: -O3, H8S/core ThinLTO=${RIMS_ANDROID_THINLTO}; DSP JIT built separately; strict FP")
