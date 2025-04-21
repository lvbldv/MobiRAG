cmake_minimum_required(VERSION 3.14)
project(android-faiss)

# Enable verbose output for debugging
set(CMAKE_VERBOSE_MAKEFILE ON)

# Define root directories
set(NATIVE_DIR ${CMAKE_SOURCE_DIR})
set(LLAMA_DIR ${NATIVE_DIR}/llama.cpp/build/bin) # <- use final Android-compiled lib path
set(FAISS_DIR ${NATIVE_DIR}/faiss)
set(EXTRA_DIR ${NATIVE_DIR}/extra)

# Define preprocessor macros
add_definitions(
        -w
        -DGGML_USE_OPENMP
        -DGGML_USE_NEON
        -D_XOPEN_SOURCE=600
        -DNDEBUG
)

# Architecture-specific adjustments
if ((${ANDROID_ABI} STREQUAL "arm64-v8a") OR (${ANDROID_ABI} STREQUAL "x86_64"))
    add_definitions(-DANDROID_BIT64)
else ()
    set(FAISS_DIR ${NATIVE_DIR}/source/faiss_32)
    add_definitions(-DANDROID_BIT32)
endif ()

# Gather source files
file(GLOB EXTRA_FILES
        ${EXTRA_DIR}/clapack/SRC/*.c
        ${EXTRA_DIR}/clapack/SRC/*.cpp
)
file(GLOB FAISS_FILES
        ${FAISS_DIR}/*.cpp
)

# Include directories
include_directories(
        ${FAISS_DIR}
        ${EXTRA_DIR}
        ${NATIVE_DIR}/llama.cpp
        ${NATIVE_DIR}/llama.cpp/ggml/include
)

# Define the shared library
add_library(faiss SHARED
        native-lib.cpp
        ${EXTRA_FILES}
        ${FAISS_FILES}
)

# Import llama and ggml libraries (compiled via Android CMake toolchain in build-android/bin)
add_library(common SHARED IMPORTED)
set_target_properties(common PROPERTIES
        IMPORTED_LOCATION ${LLAMA_DIR}/libcommon.so
)

add_library(ggml-base SHARED IMPORTED)
set_target_properties(ggml-base PROPERTIES
        IMPORTED_LOCATION ${LLAMA_DIR}/libggml-base.so
)

add_library(ggml-cpu SHARED IMPORTED)
set_target_properties(ggml-cpu PROPERTIES
        IMPORTED_LOCATION ${LLAMA_DIR}/libggml-cpu.so
)

add_library(ggml SHARED IMPORTED)
set_target_properties(ggml PROPERTIES
        IMPORTED_LOCATION ${LLAMA_DIR}/libggml.so
)

add_library(llama SHARED IMPORTED)
set_target_properties(llama PROPERTIES
        IMPORTED_LOCATION ${LLAMA_DIR}/libllama.so
)

# Find Android log library
find_library(log-lib log)

# Link everything
target_link_libraries(faiss
        ${log-lib}
        ggml-base
        ggml-cpu
        ggml
        llama
        common
)
