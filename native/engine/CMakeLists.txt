# The engine: JNI facade plus audio output. One shared library, loaded by NativeEngine.
#
# Oboe arrives as a prefab package from the com.google.oboe:oboe AAR, so this only resolves inside
# the Gradle build -- see app/build.gradle.kts. The backend libraries are plain static archives
# built from vendored sources.
find_package(oboe REQUIRED CONFIG)

add_library(protracktor_engine SHARED engine.cpp)

target_compile_options(protracktor_engine PRIVATE -Wall -Wextra -fexceptions -frtti)
set_target_properties(protracktor_engine PROPERTIES CXX_STANDARD 20 CXX_STANDARD_REQUIRED ON)

target_link_libraries(protracktor_engine PRIVATE openmpt sc68 asap gme::gme oboe::oboe android log)
