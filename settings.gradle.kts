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
        // libadb-android (wireless debugging / ADB pairing) is published on JitPack.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "GalaxyTuner"
include(":app")
