cmake_minimum_required(VERSION 3.22)
project(protracktor_native C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Oboe reaches CMake as a prefab package unpacked from an AAR, which only the Gradle build does.
# Configuring the backends alone is a much faster way to check that a newly vendored decoder
# compiles, so that path stays supported -- pass -DPROTRACKTOR_BUILD_ENGINE=OFF for it. An explicit
# option rather than sniffing for Gradle: a build that guesses which mode it is in guesses wrong
# eventually, and silently.
option(PROTRACKTOR_BUILD_ENGINE "Build the JNI engine (needs Oboe from Gradle)" ON)

add_subdirectory(backends/libopenmpt)
add_subdirectory(backends/sc68)
add_subdirectory(backends/asap)

if(PROTRACKTOR_BUILD_ENGINE)
    add_subdirectory(engine)
endif()
