import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML

fun properties(key: String) = providers.gradleProperty(key)
fun environment(key: String) = providers.environmentVariable(key)

plugins {
    id("java") // Java support
    alias(libs.plugins.kotlin) // Kotlin support
    alias(libs.plugins.gradleIntelliJPlugin) // Gradle IntelliJ Plugin
    alias(libs.plugins.changelog) // Gradle Changelog Plugin
    alias(libs.plugins.qodana) // Gradle Qodana Plugin
    alias(libs.plugins.kover) // Gradle Kover Plugin
    alias(libs.plugins.osdetector)
}

group = properties("pluginGroup").get()
version = properties("pluginVersion").get()

val bufCLIFile = project.layout.buildDirectory.file("gobin/buf").get().asFile
val bufLicenseHeaderCLIFile = project.layout.buildDirectory.file("gobin/license-header").get().asFile
val bufLicenseHeaderCLIPath: String = bufLicenseHeaderCLIFile.absolutePath

// Configure project's dependencies
repositories {
    mavenCentral()
}

// Dependencies are managed with Gradle version catalog - read more: https://docs.gradle.org/current/userguide/platforms.html#sub:version-catalog
dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.vintage.engine)
}

// Set the JVM language level used to build the project. Use Java 11 for 2020.3+, and Java 17 for 2022.2+.
kotlin {
    @Suppress("UnstableApiUsage")
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(17)
        vendor = JvmVendorSpec.JETBRAINS
    }
}

// Configure Gradle IntelliJ Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    pluginName = properties("pluginName")
    version = properties("platformVersion")
    type = properties("platformType")

    // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
    plugins = properties("platformPlugins").map { it.split(',').map(String::trim).filter(String::isNotEmpty) }
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    groups.empty()
    version.set(properties("pluginVersion"))
    repositoryUrl = properties("pluginRepositoryUrl")
}

// Configure Gradle Qodana Plugin - read more: https://github.com/JetBrains/gradle-qodana-plugin
qodana {
    cachePath = provider { file(".qodana").canonicalPath }
    reportPath = provider { file("build/reports/inspections").canonicalPath }
    saveReport = true
    showReport = environment("QODANA_SHOW_REPORT").map { it.toBoolean() }.getOrElse(false)
}

// Configure Gradle Kover Plugin - read more: https://github.com/Kotlin/kotlinx-kover#configuration
koverReport {
    defaults {
        xml {
            onCheck = true
        }
    }
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
            properties("buf.license.header.type").get(),
            "--copyright-holder",
            properties("buf.license.header.holder").get(),
            "--year-range",
            properties("buf.license.header.range").get(),
            "--ignore",
            "/cachev",
        )
    }

    register<Exec>("licenseHeaderVerify") {
        description = "Verifies that all source code has appropriate license headers."
        dependsOn("licenseHeaderInstall")
        commandLine(
            bufLicenseHeaderCLIPath,
            "--license-type",
            properties("buf.license.header.type").get(),
            "--copyright-holder",
            properties("buf.license.header.holder").get(),
            "--year-range",
            properties("buf.license.header.range").get(),
            "--ignore",
            "/cachev",
            "--diff",
            "--exit-code",
        )
    }

    register<Exec>("bufInstall") {
        description = "Installs the bufbuild/buf CLI to build/gobin."
        environment("GOBIN", bufCLIFile.parentFile.absolutePath)
        outputs.file(bufCLIFile)
        commandLine("go", "install", "github.com/bufbuild/buf/cmd/buf@v${libs.versions.buf.get()}")
    }

    check {
        dependsOn("licenseHeaderVerify")
    }

    test {
        dependsOn("bufInstall")
        environment("BUF_CACHE_DIR", File(project.projectDir.path + "/src/test/resources/testData/cachev1").absolutePath)
        systemProperty("NO_FS_ROOTS_ACCESS_CHECK", "true") // weird issue on linux
        systemProperty("BUF_CLI", bufCLIFile.absolutePath)
        useJUnitPlatform()
    }

    wrapper {
        gradleVersion = properties("gradleVersion").get()
    }

    patchPluginXml {
        version = properties("pluginVersion")
        sinceBuild = properties("pluginSinceBuild")
        untilBuild = properties("pluginUntilBuild")

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        pluginDescription = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with (it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        val changelog = project.changelog // local variable for configuration cache compatibility
        // Get the latest available change notes from the changelog file
        changeNotes = properties("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }
    }

    // Configure UI tests plugin
    // Read more: https://github.com/JetBrains/intellij-ui-test-robot
    runIdeForUiTests {
        systemProperty("robot-server.port", "8082")
        systemProperty("ide.mac.message.dialogs.as.sheets", "false")
        systemProperty("jb.privacy.policy.text", "<!--999.999-->")
        systemProperty("jb.consents.confirmation.enabled", "false")
    }

    signPlugin {
        certificateChain = environment("CERTIFICATE_CHAIN")
        privateKey = environment("PRIVATE_KEY")
        password = environment("PRIVATE_KEY_PASSWORD")
    }

    publishPlugin {
        dependsOn("patchChangelog")
        token = environment("PUBLISH_TOKEN")
        // The pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels = properties("pluginVersion").map { listOf(it.split('-').getOrElse(1) { "default" }.split('.').first()) }
    }

    runPluginVerifier {
        ideVersions.set(testIntellij.versions.versionList.get().split(',').map(String::trim).filter(String::isNotEmpty))
    }
}
