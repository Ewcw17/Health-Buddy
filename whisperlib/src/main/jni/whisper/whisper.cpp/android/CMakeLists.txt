cmake_minimum_required(VERSION 3.18)
project(whisper-android)

# Pull in the NDK toolchain if not auto-set
# set(CMAKE_TOOLCHAIN_FILE ${ANDROID_NDK}/build/cmake/android.toolchain.cmake)

# Define your ABIs (you can also set abiFilters in Gradle instead)
set(WHISPER_ABIS arm64-v8a armeabi-v7a)

# Source files from the root src/
file(GLOB WHISPER_SOURCES
     "${CMAKE_CURRENT_LIST_DIR}/../src/*.cpp"
     "${CMAKE_CURRENT_LIST_DIR}/../src/*.h")

# Build a shared lib
add_library(whisper SHARED ${WHISPER_SOURCES})

# whisper.cpp needs pthread & math
find_library(log-lib log)
find_library(pthread-lib pthread)

target_link_libraries(whisper PUBLIC ${log-lib} ${pthread-lib})
target_include_directories(whisper PUBLIC
    ${CMAKE_CURRENT_LIST_DIR}/../src
)
