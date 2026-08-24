cmake_minimum_required(VERSION 3.22.1)
project(bangla_transcriber_native)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# whisper.cpp কে গিট সাবমডিউল হিসেবে এখানে ক্লোন করতে হবে:
#   git submodule add https://github.com/ggml-org/whisper.cpp.git app/src/main/cpp/whisper.cpp
# README.md-তে বিস্তারিত ধাপ দেওয়া আছে।
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)

if(NOT EXISTS ${WHISPER_DIR}/CMakeLists.txt)
    message(FATAL_ERROR
        "whisper.cpp পাওয়া যায়নি। প্রথমে সাবমডিউল যোগ করুন:\n"
        "  git submodule add https://github.com/ggml-org/whisper.cpp.git app/src/main/cpp/whisper.cpp\n"
        "  git submodule update --init --recursive")
endif()

# GGML/whisper স্ট্যাটিক লাইব্রেরি হিসেবে বিল্ড করা, উদাহরণ/টেস্ট বাদ দিয়ে
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

add_subdirectory(${WHISPER_DIR} whisper_build)

# আমাদের JNI ব্রিজ
add_library(bangla_transcriber SHARED
    jni_bridge.cpp
)

target_include_directories(bangla_transcriber PRIVATE
    ${WHISPER_DIR}/include
    ${WHISPER_DIR}/ggml/include
)

target_link_libraries(bangla_transcriber
    whisper
    android
    log
)
