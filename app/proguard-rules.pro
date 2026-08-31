# NativeEngine's methods are bound by name from C++ (Java_com_przunk_protracktor_engine_...).
# R8 has no idea those names are load-bearing, so without this the release build renames them and
# the app dies on System.loadLibrary with an UnsatisfiedLinkError that debug builds never show.
-keepclasseswithmembernames class com.przunk.protracktor.engine.NativeEngine {
    native <methods>;
}
