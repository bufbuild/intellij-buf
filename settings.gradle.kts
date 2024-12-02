plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "intellij-buf"

dependencyResolutionManagement {
    versionCatalogs {
        create("testIntellij").from(files("./gradle/test-intellij.versions.toml"))
    }
}
