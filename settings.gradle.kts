pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Protracktor"
include(":app")

// The workspace lives on a 9p mount that rejects chmod and utimensat, which the Android build
// needs. Keeping build output off the mount is not an optimisation -- on-tree builds fail with
// "Operation not permitted". scripts/use-tooling.sh exports PROTRACKTOR_BUILD_DIR_ROOT; the
// fallback keeps a plain `gradle` invocation working too.
val buildRoot: String = System.getenv("PROTRACKTOR_BUILD_DIR_ROOT")
    ?: "${System.getProperty("user.home")}/.protracktor/build"

gradle.beforeProject {
    layout.buildDirectory.set(file("$buildRoot/${project.path.replace(':', '_').trim('_').ifEmpty { "root" }}"))
}
