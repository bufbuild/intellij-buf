import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java") // Java support
    alias(libs.plugins.kotlin) // Kotlin support
    alias(libs.plugins.intelliJPlatform) // IntelliJ Platform Gradle Plugin
    alias(libs.plugins.changelog) // Gradle Changelog Plugin
    alias(libs.plugins.qodana) // Gradle Qodana Plugin
    alias(libs.plugins.kover) // Gradle Kover Plugin
    alias(libs.plugins.osdetector)
    alias(libs.plugins.spotless)
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

val buf: Configuration by configurations.creating
val bufLicenseHeaderCLIFile = project.layout.buildDirectory.file("gobin/license-header").get().asFile
val bufLicenseHeaderCLIPath: String = bufLicenseHeaderCLIFile.absolutePath

// Set the JVM language level used to build the project.
kotlin {
    jvmToolchain(17)
}

// Configure project's dependencies
repositories {
    mavenCentral()

    // IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
    intellijPlatform {
        defaultRepositories()
        marketplace()
    }
}

// Dependencies are managed with Gradle version catalog - read more: https://docs.gradle.org/current/userguide/platforms.html#sub:version-catalog
dependencies {
    buf("build.buf:buf:${libs.versions.buf.get()}:${osdetector.classifier}@exe")

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))

        // Plugin Dependencies. Uses `platformBundledPlugins` property from the gradle.properties file for bundled IntelliJ Platform plugins.
        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map { it.split(',') })

        // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file for plugin from JetBrains Marketplace.
        plugins(providers.gradleProperty("platformPlugins").map { it.split(',') })

        instrumentationTools()
        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.JUnit5)
    }
}

// Configure IntelliJ Platform Gradle Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("pluginVersion")

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        val changelog = project.changelog // local variable for configuration cache compatibility
        // Get the latest available change notes from the changelog file
        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // The pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels = providers.gradleProperty("pluginVersion").map { listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" }) }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
}

// Configure Gradle Kover Plugin - read more: https://github.com/Kotlin/kotlinx-kover#configuration
kover {
    reports {
        total {
            xml {
                onCheck = true
            }
        }
    }
}

spotless {
    kotlin {
        ktlint().editorConfigOverride(
            mapOf(
                "ktlint_experimental" to "enabled",
            ),
        )
        target("**/*.kt")
    }
    kotlinGradle {
        ktlint().editorConfigOverride(
            mapOf(
                "ktlint_experimental" to "enabled",
            ),
        )
        target("**/*.kts")
    }
}

fun bufOS(): String = when (osdetector.os) {
    "osx" -> "Darwin"
    "linux" -> "Linux"
    "windows" -> "Windows"
    else -> osdetector.os
}

fun bufArch(): String {
    return when (osdetector.arch) {
        "aarch_64" -> "arm64"
        else -> return osdetector.arch
    }
}

fun bufExt(): String = when (osdetector.os) {
    "windows" -> ".exe"
    else -> ""
}

tasks {
    register<Exec>("licenseHeaderInstall") {
        description = "Installs the bufbuild/buf license-header CLI to build/gobin."
        environment("GOBIN", bufLicenseHeaderCLIFile.parentFile.absolutePath)
        outputs.file(bufLicenseHeaderCLIFile)
        commandLine("go", "install", "github.com/bufbuild/buf/private/pkg/licenseheader/cmd/license-header@v${libs.versions.buf.get()}")
    }

    register<Exec>("licenseHeader") {
        description = "Runs the license-header CLI to add/update license information to source code."
        dependsOn("licenseHeaderInstall")
        commandLine(
            bufLicenseHeaderCLIPath,
            "--license-type",
            providers.gradleProperty("buf.license.header.type").get(),
            "--copyright-holder",
            providers.gradleProperty("buf.license.header.holder").get(),
            "--year-range",
            providers.gradleProperty("buf.license.header.range").get(),
            "--ignore",
            "/testData/cache",
        )
    }

    register<Exec>("licenseHeaderVerify") {
        description = "Verifies that all source code has appropriate license headers."
        dependsOn("licenseHeaderInstall")
        commandLine(
            bufLicenseHeaderCLIPath,
            "--license-type",
            providers.gradleProperty("buf.license.header.type").get(),
            "--copyright-holder",
            providers.gradleProperty("buf.license.header.holder").get(),
            "--year-range",
            providers.gradleProperty("buf.license.header.range").get(),
            "--ignore",
            "/testData/cache",
            "--diff",
            "--exit-code",
        )
    }

    register("configureBuf") {
        description = "Installs the Buf CLI."
        File(buf.asPath).setExecutable(true)
    }

    check {
        dependsOn("licenseHeaderVerify")
    }

    test {
        dependsOn("configureBuf")
        environment("BUF_CACHE_DIR", File(project.projectDir.path + "/src/test/resources/testData/cache").absolutePath)
        systemProperty("NO_FS_ROOTS_ACCESS_CHECK", "true") // weird issue on linux
        systemProperty("BUF_CLI", buf.asPath)
        useJUnitPlatform()
    }

    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }

    prepareSandbox {
        // Kanro plugin conflicts with Protocol Buffers plugin - disable it while running the IDE.
        disabledPlugins = listOf("io.kanro.idea.plugin.protobuf")
    }

    prepareTestSandbox {
        // Kanro plugin conflicts with Protocol Buffers plugin - disable it while testing.
        disabledPlugins = listOf("io.kanro.idea.plugin.protobuf")
    }

    publishPlugin {
        dependsOn(patchChangelog)
    }
}

intellijPlatformTesting {
    runIde {
        register("runIdeForUiTests") {
            task {
                jvmArgumentProviders += CommandLineArgumentProvider {
                    listOf(
                        "-Drobot-server.port=8082",
                        "-Dide.mac.message.dialogs.as.sheets=false",
                        "-Djb.privacy.policy.text=<!--999.999-->",
                        "-Djb.consents.confirmation.enabled=false",
                    )
                }
            }

            plugins {
                robotServerPlugin()
            }
        }
    }
}
