# The engine: decoders, plus the audio output and JNI facade above them. One shared library.
#
# Oboe arrives as a prefab package from the com.google.oboe:oboe AAR, so this only resolves inside
# the Gradle build -- see app/build.gradle.kts. The backend libraries are plain static archives
# built from vendored sources.
find_package(oboe REQUIRED CONFIG)

# Two sources, and the split is deliberate: engine.cpp decodes and knows nothing about Android,
# player_oboe.cpp is the audio callback and the JNI surface. docs/PLAN_WEB.md §13 S0 has the
# measurement that chose the line between them; a second host links engine.cpp and replaces the
# other file rather than forking either.
# `lha_jni.cpp` is a third source and belongs to neither half: it is the UnExoticA catalogue's
# entire native surface, and `docs/PLAN_UNEXOTICA.md` promises that surface stays small enough to
# delete. It is here rather than folded into player_oboe.cpp so that deleting it is one line.
add_library(protracktor_engine SHARED engine.cpp player_oboe.cpp lha_jni.cpp)

target_compile_options(protracktor_engine PRIVATE -Wall -Wextra -fexceptions -frtti)
set_target_properties(protracktor_engine PROPERTIES CXX_STANDARD 20 CXX_STANDARD_REQUIRED ON)

# UADE is linked when the build asked for it. Unlike ZXTune it cannot be made unconditional: the
# browser has no `fork`, so `native/CMakeLists.txt` defaults it off and Gradle turns it on.
if(PROTRACKTOR_WITH_UADE)
    set(UADE_LIBRARY uade_frontend)
endif()

target_link_libraries(protracktor_engine PRIVATE openmpt sc68 asap gme::gme sidplayfp hively minimp3 zxtune ${UADE_LIBRARY} lhasa oboe::oboe android log)

# uadecore is a second program, not a library, so nothing links it -- but it has to be built, and
# packaged into the same lib/<abi>/ the engine lands in. A dependency rather than a copy step: AGP
# packages what the CMake build produces, and an explicit edge is what makes sure it produced it.
if(PROTRACKTOR_WITH_UADE)
    add_dependencies(protracktor_engine uadecore)
endif()
