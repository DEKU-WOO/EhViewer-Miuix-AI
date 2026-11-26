pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("com.android.settings") version "9.4.1"
}

android {
    // miuix 0.9.5-rc01 的 AAR 元数据要求 compileSdk 至少 37.2
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }
    minSdk = 24
    targetSdk = 37
    ndkVersion = "29.0.14206865"
    buildToolsVersion = "37.0.0"
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
rootProject.name = "EhViewer"
include(":app")
include(":benchmark")
include(":core:common")
include(":core:data")
include(":core:i18n")
include(":core:shell")
include(":core:ui")
include(":desktop")
