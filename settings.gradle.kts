rootProject.name = "intellij-buf"

dependencyResolutionManagement {
    versionCatalogs {
        create("testIntellij").from(files("./gradle/test-intellij.versions.toml"))
    }
}