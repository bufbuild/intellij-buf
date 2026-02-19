// Copyright 2022-2026 Buf Technologies, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package build.buf.intellij.lsp

import build.buf.intellij.settings.BufCLIUtils
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.process.ScriptRunnerUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.platform.lsp.api.LspServerManager
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Detects buf CLI version and determines if LSP support is available.
 * Caches results per project to avoid repeated executions.
 */
object BufVersionDetector {
    private val log = logger<BufVersionDetector>()

    // Version history for buf LSP:
    // - 1.59.0+: LSP available via `buf lsp serve`
    private const val MINIMUM_LSP_VERSION = "1.59.0"

    private val VERSION_CACHE_KEY = Key.create<VersionInfo>("buf.version.info")

    data class VersionInfo(
        val version: String,
        val supportsLsp: Boolean,
    )

    /**
     * Gets version information for the configured buf CLI.
     * Results are cached per project.
     *
     * @param checkIfMissing If false, returns null instead of executing buf command when not cached.
     *                       Use false when calling from EDT to avoid blocking.
     */
    fun getVersionInfo(project: Project, checkIfMissing: Boolean = true): VersionInfo? {
        // Check cache first
        val cached = project.getUserData(VERSION_CACHE_KEY)
        if (cached != null) {
            return cached
        }

        // If not cached and we shouldn't check, return null
        if (!checkIfMissing) {
            return null
        }

        // Execute buf --version
        val bufExecutable = BufCLIUtils.getConfiguredBufExecutable(project)
        if (bufExecutable == null) {
            log.debug("Buf CLI not found, LSP not supported")
            return null
        }

        val version = executeVersionCommand(bufExecutable.absolutePath)
        if (version == null) {
            log.warn("Failed to execute buf --version")
            return null
        }

        val parsedVersion = BufVersion.parse(version)
        val minVersion = BufVersion.parse(MINIMUM_LSP_VERSION)

        val supportsLsp = parsedVersion != null && minVersion != null && parsedVersion >= minVersion

        val info = VersionInfo(version, supportsLsp)

        // Cache the result
        project.putUserData(VERSION_CACHE_KEY, info)

        log.info("Detected buf version: $version, LSP supported: $supportsLsp")
        return info
    }

    private fun executeVersionCommand(bufPath: String): String? {
        val stdout = AtomicReference<String>()
        val exitCode = AtomicInteger(-1)

        try {
            val handler = ScriptRunnerUtil.execute(
                bufPath,
                null, // working directory
                null, // environment variables
                arrayOf("--version"),
            )

            handler.addProcessListener(object : ProcessAdapter() {
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    if (outputType == ProcessOutputType.STDOUT) {
                        stdout.set(event.text.trim())
                    }
                }

                override fun processTerminated(event: ProcessEvent) {
                    exitCode.set(event.exitCode)
                }
            })

            handler.startNotify()
            if (handler.waitFor(Duration.ofSeconds(5).toMillis())) {
                if (exitCode.get() == 0) {
                    return stdout.get()
                }
            } else {
                handler.destroyProcess()
                log.warn("buf --version timed out")
            }
        } catch (e: Exception) {
            log.warn("Failed to execute buf --version", e)
        }

        return null
    }

    /**
     * Checks if the LSP server is currently active for the given project.
     * This is a consolidated method used by analyze passes, widgets, etc.
     *
     * @param checkIfMissing If false, only uses cached version info (fast, non-blocking)
     * @return true if LSP is supported and has running servers
     */
    fun isLspActive(project: Project, checkIfMissing: Boolean = false): Boolean {
        // Check if buf version supports LSP
        val versionInfo = getVersionInfo(project, checkIfMissing = checkIfMissing)
        if (versionInfo == null || !versionInfo.supportsLsp) {
            return false
        }

        // Check if LSP servers are running
        val lspServers = LspServerManager.getInstance(project)
            .getServersForProvider(BufLspServerSupportProvider::class.java)

        return lspServers.isNotEmpty()
    }
}

/**
 * Represents a semantic version for comparison.
 * Internal visibility allows testing without reflection.
 */
internal data class BufVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<BufVersion> {

    override fun compareTo(other: BufVersion): Int = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    companion object {
        private val VERSION_REGEX = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-.*)?$""")

        /**
         * Parses a version string like "1.40.0", "v1.40.0", or "1.65.1-dev".
         * Pre-release identifiers (e.g., "-dev", "-rc1") are ignored.
         * Returns null if the string cannot be parsed.
         */
        fun parse(versionString: String): BufVersion? {
            val match = VERSION_REGEX.find(versionString) ?: return null
            val (major, minor, patch) = match.destructured
            return BufVersion(major.toInt(), minor.toInt(), patch.toInt())
        }
    }
}
