cmake_minimum_required(VERSION 3.22.1)

project("nova-luna-native")

# Compiler Flags for Performance on Android arm64-v8a
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -DGGML_USE_CPU")

# Include directories
include_directories(third_party/llama.cpp)

# Define the JNI library
add_library(${CMAKE_PROJECT_NAME} SHARED
        llama-jni.cpp
        third_party/llama.cpp/llama.cpp
        third_party/llama.cpp/ggml.c
        third_party/llama.cpp/ggml-alloc.c
        third_party/llama.cpp/ggml-backend.c
        third_party/llama.cpp/ggml-quants.c
        third_party/llama.cpp/ggml-cpu.c
        third_party/llama.cpp/unicode.cpp
)

# Find and link necessary Android libraries
find_library(log-lib log)

target_link_libraries(${CMAKE_PROJECT_NAME}
        ${log-lib}
)

# Set library name to match System.loadLibrary("llama-jni")
set_target_properties(${CMAKE_PROJECT_NAME} PROPERTIES OUTPUT_NAME "llama-jni")
