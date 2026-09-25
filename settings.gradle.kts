pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "vmstudio-code"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")

// Core layer — no feature module may be depended upon from here.
include(":core:common")
include(":core:ui")
include(":core:security")
include(":core:database")
include(":core:network")
include(":core:ssh")
include(":core:sftp")
include(":core:terminal")
include(":core:editor")
include(":core:git")
include(":core:project")
include(":core:workspace")
include(":core:ai")
include(":core:agent")
include(":core:connectors")
include(":core:update")

// Feature layer — flat; features never depend on each other.
include(":feature:dashboard")
include(":feature:projects")
include(":feature:servers")
include(":feature:files")
include(":feature:terminal")
include(":feature:editor")
include(":feature:git")
include(":feature:ai")
include(":feature:tasks")
include(":feature:activity")
include(":feature:connectors")
include(":feature:settings")
include(":feature:onboarding")
