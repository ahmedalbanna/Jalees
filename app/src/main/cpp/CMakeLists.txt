cmake_minimum_required(VERSION 3.10.2)

project("whisper_jni")

# Add the native JNI source file
add_library(
        whisper_jni # Library name
        SHARED      # Type
        whisper_jni.cpp)

add_library(
        vad_jni
        SHARED
        vad_jni.cpp)

# When using the actual whisper.cpp source files, they would be added here:
# add_library(whisper_core STATIC whisper.cpp ggml.c)
# target_link_libraries(whisper_jni whisper_core log)

# Link the standard Android logging library
find_library(
        log-lib
        log)
        
target_link_libraries(
        whisper_jni
        ${log-lib})

target_link_libraries(
        vad_jni
        ${log-lib})
