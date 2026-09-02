cmake_minimum_required(VERSION 3.22.1)

project(pailer_radio_writer)

set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/llama.cpp llama_cpp_build)

add_library(pailer_llama SHARED pailer_llama_jni.cpp)

target_include_directories(pailer_llama PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/llama.cpp/include
)

target_link_libraries(pailer_llama
    llama
    android
    log
)
