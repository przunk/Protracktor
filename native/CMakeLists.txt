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

# lhasa first: it is not a decoder and nothing here plays an LHA file, but ZXTune's `.ym`/`.vtx`
# path links against it and the UnExoticA catalogue will want its archive reader. Built whatever
# `PROTRACKTOR_WITH_ZXTUNE` says, because the second use does not depend on the first.
add_subdirectory(lhasa)

add_subdirectory(backends/libopenmpt)
add_subdirectory(backends/sc68)
add_subdirectory(backends/asap)
add_subdirectory(backends/gme)
add_subdirectory(backends/sidplayfp)
add_subdirectory(backends/hively)
# ZXTune is the one optional decoder, and only because it is the one that does not compile
# everywhere: Emscripten's libc++ rejects `lexic_analysis.cpp`, and patching it means forking a
# library §3 of docs/ARCHITECTURE.md says we do not fork. On by default, so Android is unchanged.
option(PROTRACKTOR_WITH_ZXTUNE "Build the ZX Spectrum decoder" ON)
if(PROTRACKTOR_WITH_ZXTUNE)
    add_subdirectory(backends/zxtune)
endif()

if(PROTRACKTOR_BUILD_ENGINE)
    add_subdirectory(engine)
endif()
