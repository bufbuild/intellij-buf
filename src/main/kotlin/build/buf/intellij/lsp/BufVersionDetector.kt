// Copyright 2022-2025 Buf Technologies, Inc.
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
import build.buf.intellij.settings.bufSettings
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.process.ScriptRunnerUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Detects buf CLI version and determines if LSP support is available.
 * Caches results per project to avoid repeated executions.
 */
object BufVersionDetector {
    private val LOG = logger<BufVersionDetector>()
    private const val MINIMUM_LSP_VERSION = "1.40.0"
    private val VERSION_CACHE_KEY = Key.create<VersionInfo>("buf.version.info")

    data class VersionInfo(
        val version: String,
        val supportsLsp: Boolean,
    )

    /**
     * Checks if the configured buf CLI supports LSP functionality.
     * Results are cached per project.
     */
    fun isLspSupported(project: Project): Boolean {
        val info = getVersionInfo(project) ?: return false
        return info.supportsLsp
    }

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
            LOG.debug("Buf CLI not found, LSP not supported")
            return null
        }

        val version = executeVersionCommand(bufExecutable.absolutePath)
        if (version == null) {
            LOG.warn("Failed to execute buf --version")
            return null
        }

        val supportsLsp = isVersionSupported(version)
        val info = VersionInfo(version, supportsLsp)

        // Cache the result
        project.putUserData(VERSION_CACHE_KEY, info)

        LOG.info("Detected buf version: $version, LSP supported: $supportsLsp")
        return info
    }

    /**
     * Clears the version cache for a project. Call this when settings change.
     */
    fun clearCache(project: Project) {
        project.putUserData(VERSION_CACHE_KEY, null)
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
                LOG.warn("buf --version timed out")
            }
        } catch (e: Exception) {
            LOG.warn("Failed to execute buf --version", e)
        }

        return null
    }

    private fun isVersionSupported(versionOutput: String): Boolean {
        // Parse version from output like "1.40.0" or "v1.40.0"
        val versionRegex = Regex("""v?(\d+)\.(\d+)\.(\d+)""")
        val match = versionRegex.find(versionOutput) ?: return false

        val (major, minor, patch) = match.destructured
        val version = Triple(major.toInt(), minor.toInt(), patch.toInt())

        // Parse minimum version
        val minMatch = versionRegex.find(MINIMUM_LSP_VERSION) ?: return false
        val (minMajor, minMinor, minPatch) = minMatch.destructured
        val minVersion = Triple(minMajor.toInt(), minMinor.toInt(), minPatch.toInt())

        // Compare versions
        return when {
            version.first > minVersion.first -> true
            version.first < minVersion.first -> false
            version.second > minVersion.second -> true
            version.second < minVersion.second -> false
            version.third >= minVersion.third -> true
            else -> false
        }
    }

    /**
     * Gets the buf version string for display purposes.
     */
    fun getVersionString(project: Project): String? = getVersionInfo(project)?.version

    /**
     * Checks if the LSP server is currently active for the given project.
     * This is a consolidated method used by analyze passes, widgets, etc.
     *
     * @param checkIfMissing If false, only uses cached version info (fast, non-blocking)
     * @return true if LSP is enabled, supported, and has running servers
     */
    fun isLspActive(project: Project, checkIfMissing: Boolean = false): Boolean {
        // Check if LSP is enabled in settings
        if (!project.bufSettings.state.useLspServer) {
            return false
        }

        // Check if buf version supports LSP
        val versionInfo = getVersionInfo(project, checkIfMissing = checkIfMissing)
        if (versionInfo == null || !versionInfo.supportsLsp) {
            return false
        }

        // Check if LSP servers are running
        val lspServers = com.intellij.platform.lsp.api.LspServerManager.getInstance(project)
            .getServersForProvider(build.buf.intellij.lsp.BufLspServerSupportProvider::class.java)

        return lspServers.isNotEmpty()
    }
}
