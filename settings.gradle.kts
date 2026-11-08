// Aurora Player — Etapa 1 (esqueleto, sin push todavía)
// Repo propuesto: aurora-player · Package: com.aurora.player
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
rootProject.name = "Aurora Player"
include(":app")
