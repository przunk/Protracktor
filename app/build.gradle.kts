// AGP 9 ships Kotlin support built in, so org.jetbrains.kotlin.android must NOT be applied -- doing
// so is a hard error. The Compose compiler plugin is still separate and still required whenever
// buildFeatures.compose is on.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

/**
 * The number of commits behind HEAD, used as the versionCode.
 *
 * Through `providers.exec` rather than `ProcessBuilder`: Gradle 9 refuses to start an external
 * process at configuration time, because doing so cannot be cached. The provider API exists for
 * exactly this and its result participates in the configuration cache properly.
 *
 * Falls back to 1 outside a git checkout — a source archive, say — which is wrong but harmless:
 * nothing built that way is going to a store.
 */
val gitCommitCount: Int = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
    workingDir = rootProject.projectDir
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().toIntOrNull() ?: 1 }.getOrElse(1)

android {
    namespace = "com.przunk.protracktor"
    compileSdk = 36

    // Pinned to match scripts/use-tooling.sh. AGP would otherwise take whichever NDK happens to be
    // installed, and a native build whose toolchain drifts between machines produces failures that
    // cannot be reproduced.
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "com.przunk.protracktor"
        minSdk = 29
        targetSdk = 36
        // Counted, not typed. Play rejects an upload whose versionCode it has seen before, and a
        // number a person has to remember to raise is a number that eventually is not raised. The
        // commit count only ever grows, changes on every merge without anyone doing anything, and
        // needs no discipline at all.
        //
        // Falls back to 1 outside a git checkout -- a source archive, say -- which is wrong but
        // harmless: nothing built that way is going to a store.
        versionCode = gitCommitCount

        // Typed, deliberately, and it means something. See docs/BUILD.md: patch for a batch of
        // fixes, minor for a round of work that added capability, major reserved for "publishable".
        // Bumping it per merge was considered and rejected -- twenty merges in a day would make it
        // a second, worse timestamp.
        versionName = "0.9.1"

        // Stated explicitly rather than left to whatever the NDK defaults to that month, because
        // native decoder builds are the expensive part of this project and the ABI list drives
        // that cost directly. 32-bit x86 is omitted: it exists only on emulators, and there is no
        // emulator in this environment anyway.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                // Oboe's prefab package is built against the shared STL and refuses to link into a
                // static one -- "[CXX1212] User is using a static STL but library requires a shared
                // STL". CMake's NDK default is c++_static, so this is not optional. It costs one
                // extra libc++_shared.so in the APK, which is the right trade when more than one
                // native library is coming and they all have to agree anyway.
                arguments += "-DANDROID_STL=c++_shared"

                // UADE. Defaulted off in native/CMakeLists.txt because it is the one backend the
                // browser cannot have -- it is a second process, and WebAssembly has no `fork`.
                // Asked for here, which is the only build that can execute one.
                arguments += "-DPROTRACKTOR_WITH_UADE=ON"
            }
        }
    }

    signingConfigs {
        create("release") {
            // Read from ~/.gradle/gradle.properties, never from this repository. A key committed
            // beside the code has to be treated as public from the day it is written.
            // Same property names as the workshop's other projects, so one keystore serves them all.
            val storePath = providers.gradleProperty("PRZUNK_UPLOAD_STORE_FILE").orNull
            if (storePath != null) {
                storeFile = rootProject.file(storePath)
                storePassword = providers.gradleProperty("PRZUNK_UPLOAD_STORE_PASSWORD").orNull
                keyAlias = providers.gradleProperty("PRZUNK_UPLOAD_KEY_ALIAS").orNull
                keyPassword = providers.gradleProperty("PRZUNK_UPLOAD_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            // The upload key when it is configured, the local debug key otherwise. An unsigned APK
            // cannot be installed at all, so falling back keeps sideloading alive on a machine with
            // no release key. build-release.sh prints which key actually signed it, because that
            // mistake is otherwise invisible until an upload is rejected.
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
                ?: signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // **The native libraries are extracted at install**, which is not the modern default and is
    // not a preference. `uadecore` is an executable shipped as `lib/<abi>/libuadecore.so`, and an
    // executable has to be a file on disk for `exec` to reach it. With the default packaging the
    // libraries are mapped straight out of the APK and `nativeLibraryDir` holds nothing, which is
    // also the one directory an app may execute from since Android 10 -- everywhere it can write,
    // it may not execute. So this is what UADE costs, stated where it is paid:
    //
    //   download  smaller, because the libraries are compressed in the APK again
    //   installed larger, because they are written out once
    //
    // It applies to every library, not only this one; there is no per-file form of it.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    buildFeatures {
        compose = true
        prefab = true  // unpacks Oboe's headers and .so from its AAR for CMake to find
    }

    externalNativeBuild {
        cmake {
            // The native tree sits beside the module rather than inside it: the decoders are shared
            // infrastructure, not part of the UI module, and several more are coming.
            path = file("../native/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// sc68 does not carry its replay routines inside the tunes it plays: SNDH and .sc68 both reference
// small 68k binaries that ship in sc68's own data directory. They are copied into the APK's assets
// from the vendored source rather than committed, so they stay pinned to the version we fetched.
// A plain File, resolved now: AGP 9 refuses a Provider here, because Android Studio cannot tell
// whether a lazily supplied directory is generated or hand-written.
val sc68Assets: File = layout.buildDirectory.dir("generated/sc68-assets").get().asFile

// `Sync` rather than `Copy`, so the destination ends up matching the source exactly. A `Copy` only
// ever adds: when this task was narrowed from 99 replays to one, the other 98 stayed in the
// generated assets and would have shipped anyway. The same would happen to any file dropped
// upstream, silently and in the APK.
val copySc68Data = tasks.register<Sync>("copySc68Data") {
    // sc68 3.0.0b keeps them under file68/data68 rather than 2.2.1's data/, and ships 99 replays
    // where 2.2.1 shipped 84 -- which is part of why more SNDH files play.
    from(rootProject.file("native/vendor/sc68-3/file68/data68")) {
        // **One replay, not ninety-nine** — see `docs/LICENSES.md`. `sndh_ice.bin` is sc68's own
        // SNDH wrapper, sitting in sc68's own GPL-3-or-later tree, so shipping it needs nobody's
        // permission. The other 98 are named after commercial Atari ST games and after other
        // people's players, and sc68 says nothing about where they came from; they are downloaded
        // from sc68's own SourceForge at the user's request instead, which leaves the
        // distributing to SourceForge.
        //
        // Measured before deciding: this costs nothing for SNDH -- 30 of 30 either way, and 5,484
        // Modland files -- and takes `.sc68` from 10 of 10 to 4 of 10 until the rest is fetched.
        include("Replay/sndh_ice.bin")
    }
    into(File(sc68Assets, "sc68"))
}

android.sourceSets["main"].assets.srcDir(sc68Assets)

tasks.named("preBuild") { dependsOn(copySc68Data) }

// UADE's own three data files, which is everything it needs except the replay routines.
//
// `score` is the 68k program that runs *inside* the emulated Amiga and drives the replay routine;
// `uaerc` configures the emulated machine; `eagleplayer.conf` is the table that says which player a
// file needs. All three are UADE's own work in UADE's own GPL tree, so they ship.
//
// **`players/` does not** -- 178 replay binaries extracted from commercial and shareware Amiga
// music programs, which UADE's own maintainers said should be downloaded rather than redistributed
// (`docs/LICENSES.md`, settled 2026-09-04). The app fetches them from the page upstream publishes
// for exactly that, which leaves the distributing where it belongs. 2.0 MB, and without them UADE
// plays 12 files in 300.
val uadeAssets: File = layout.buildDirectory.dir("generated/uade-assets").get().asFile

val copyUadeData = tasks.register<Sync>("copyUadeData") {
    from(rootProject.file("native/vendor/uade/amigasrc/score")) { include("score") }
    from(rootProject.file("native/vendor/uade")) {
        include("uaerc")
        include("eagleplayer.conf")
    }
    into(File(uadeAssets, "uade"))
}

android.sourceSets["main"].assets.srcDir(uadeAssets)

tasks.named("preBuild") { dependsOn(copyUadeData) }

// **The licences and the privacy policy, as the app shows them** (Settings → Open-source licences,
// Privacy policy). Every file named in `app/notices/components.tsv` is copied from where it came
// with its code -- the vendored sources, or `app/notices/` for what arrives as a Maven artifact --
// so the text on screen is the text the licence asks to be reproduced, not a retelling of it. The
// privacy policy is the same file that is published, so the two cannot drift.
//
// Read at configuration time: the table is a handful of lines, and a `Sync` has to know its sources
// before it runs. `Sync` rather than `Copy`, for `copySc68Data`'s reason -- a row removed from the
// table must take its files out of the APK too.
val noticeAssets: File = layout.buildDirectory.dir("generated/notice-assets").get().asFile

val copyNotices = tasks.register<Sync>("copyNotices") {
    val table = rootProject.file("app/notices/components.tsv")
    inputs.file(table)
    from(table) { into("notices") }
    from(rootProject.file("store/privacy-policy.md")) { into("legal") }
    table.readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        // Only what the app carries: the last column says which build a row is for, and the web
        // page's Emscripten runtime and QR library have no business in the APK.
        .filter { line -> "app" in line.split('\t').getOrElse(5) { "" }.split(',').map { it.trim() } }
        .forEach { line ->
            val cells = line.split('\t')
            val id = cells[0]
            cells[4].split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { path ->
                from(rootProject.file(path)) { into("notices/$id") }
            }
        }
    into(noticeAssets)
}

android.sourceSets["main"].assets.srcDir(noticeAssets)

tasks.named("preBuild") { dependsOn(copyNotices) }

// Two tests read files outside the source set: `RuleCasesTest` the shared queue rules and
// `SupportedFormatsFileTest` the page's format list. Gradle cannot see that, so an edit to either
// file alone left the test task "up to date" and the check that exists to catch drift never ran.
tasks.withType<Test>().configureEach {
    inputs.file(rootProject.file("docs/rules/queue-cases.tsv")).withPathSensitivity(PathSensitivity.RELATIVE)
    // And `UadecoreCanBeExecutedTest` this build script and UADE's CMake, for the same reason:
    // three settings that only matter together, none of which any code path mentions.
    inputs.file(rootProject.file("app/build.gradle.kts")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.file("native/backends/uade/CMakeLists.txt")).withPathSensitivity(PathSensitivity.RELATIVE)
    // And `NoticesCoverTheBuildTest` the licence table and the native build it is held to. Found by
    // taking a row out of the table: the test stayed green because Gradle had not re-run it.
    inputs.file(rootProject.file("app/notices/components.tsv")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.file("native/CMakeLists.txt")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.file("store/privacy-policy.md")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.file("web/src/formats.tsv")).withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    implementation(libs.oboe)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // The QR scanner (docs/PLAN_HANDOFF.md §3 H2). CameraX gives a preview and a stream of frames;
    // ZXing turns one into a room address. Together about 1.6 MB of the APK, which is the price of
    // not making the owner send himself a link.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.sqlite.jdbc)
}
