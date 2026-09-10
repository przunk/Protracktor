# libsidplayfp -- GPL-2.0-or-later. Commodore 64: PSID and RSID.
#
# Autotools upstream, built here the way sc68 is: our own source list and a hand-written config.h,
# because cross-compiling `configure` three times to feed one CMake build is more moving parts than
# a header with thirty defines in it.
#
# The SID emulation is **SIDLite**, not ReSIDfp. In libsidplayfp 3.x ReSIDfp was split out into a
# separate library (`libresidfp`), so using it would mean vendoring a second project; SIDLite ships
# inside this one and needs nothing. ReSIDfp is the better emulation and is worth revisiting if
# SIDLite proves wanting -- said in docs/PLAN_FORMATS.md rather than left as a surprise.
set(SID_SRC "${CMAKE_CURRENT_SOURCE_DIR}/../../vendor/sidplayfp/src")

if(NOT EXISTS "${SID_SRC}/player.cpp")
    message(FATAL_ERROR
        "libsidplayfp sources are missing. Run ./scripts/fetch-native-deps.sh first.\n"
        "  expected: ${SID_SRC}/player.cpp")
endif()

file(GLOB_RECURSE SID_SOURCES CONFIGURE_DEPENDS "${SID_SRC}/*.cpp")

# The hardware builders need USB libraries that do not exist here, and the ReSIDfp builder needs the
# separate library described above.
list(FILTER SID_SOURCES EXCLUDE REGEX "builders/(exsid|usbsid|residfp)-builder/")

list(LENGTH SID_SOURCES SID_SOURCE_COUNT)
if(SID_SOURCE_COUNT LESS 30)
    message(FATAL_ERROR
        "Found only ${SID_SOURCE_COUNT} libsidplayfp sources, expected around forty. "
        "The vendored tree is not the shape this expects.")
endif()
message(STATUS "libsidplayfp: ${SID_SOURCE_COUNT} sources")

add_library(sidplayfp STATIC ${SID_SOURCES})

# Three headers upstream generates from templates are written by hand instead; see the comment in
# each for why. They are split by who needs them: sidversion.h is pulled in by the library's own
# public header, so anything that includes libsidplayfp needs it too, while config.h and sl_defs.h
# are internal -- and a config.h on a public include path would be a name every other library in
# this build could collide with.
target_include_directories(sidplayfp
    PUBLIC "${SID_SRC}"
           "${SID_SRC}/builders/sidlite-builder"
           "${CMAKE_CURRENT_SOURCE_DIR}/public"
    PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/generated"
            "${SID_SRC}/builders/sidlite-builder/sidlite"
)

target_compile_definitions(sidplayfp PRIVATE HAVE_CONFIG_H=1)
set_target_properties(sidplayfp PROPERTIES CXX_STANDARD 20 CXX_STANDARD_REQUIRED ON)
target_compile_options(sidplayfp PRIVATE -w)
