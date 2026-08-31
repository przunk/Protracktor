# NativeEngine's methods are bound by name from C++ (Java_com_przunk_protracktor_engine_...).
# R8 has no idea those names are load-bearing, so without this the release build renames them and
# the app dies on System.loadLibrary with an UnsatisfiedLinkError that debug builds never show.
#
# "membernames", not "members": this preserves the NAME of every native method that survives, and
# still lets R8 delete the ones nothing calls. That is what we want -- JNI resolves lazily and per
# call, so a native method no Kotlin code invokes is dead weight -- but it does mean a release APK
# legitimately contains fewer native declarations than NativeEngine.kt.
#
# Measured on the 0.1.0 release build: nativePositionSeconds and nativeDurationSeconds were dropped
# because no caller exists yet, while the five that are called kept their exact names. Before
# concluding this rule is broken, check whether the missing method is simply unused.
-keepclasseswithmembernames class com.przunk.protracktor.engine.NativeEngine {
    native <methods>;
}
