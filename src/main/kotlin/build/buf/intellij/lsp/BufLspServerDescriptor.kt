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

import build.buf.intellij.config.BufConfig
import build.buf.intellij.settings.BufCLIUtils
import build.buf.intellij.settings.bufSettings
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import java.io.File

/**
 * LSP server descriptor for the Buf Language Server.
 * Manages the lifecycle of the buf LSP server process.
 * Uses `buf beta lsp` for buf 1.43.0-1.58.x and `buf lsp serve` for buf 1.59.0+.
 */
class BufLspServerDescriptor(project: Project) : ProjectWideLspServerDescriptor(project, "Buf") {
    private val log = logger<BufLspServerDescriptor>()

    override fun isSupportedFile(file: VirtualFile): Boolean {
        // Only support .proto files
        if (file.extension != "proto") {
            return false
        }

        // File must be in project content
        if (!ProjectFileIndex.getInstance(project).isInContent(file)) {
            return false
        }

        // File must be in a buf workspace (have a buf.yaml or buf.work.yaml in parent hierarchy)
        return findBufWorkspaceRoot(file) != null
    }

    override fun createCommandLine(): GeneralCommandLine {
        val bufExe = BufCLIUtils.getConfiguredBufExecutable(project)
            ?: throw IllegalStateException("Buf CLI not found. Please configure the Buf CLI path in settings.")

        // Determine which command to use based on buf version
        val versionInfo = BufVersionDetector.getVersionInfo(project, checkIfMissing = false)
        val useBetaCommand = versionInfo?.useBetaCommand ?: false

        val cmd = GeneralCommandLine()
        cmd.exePath = bufExe.absolutePath

        // Use 'buf beta lsp' for 1.43.0-1.58.x, 'buf lsp serve' for 1.59.0+
        if (useBetaCommand) {
            cmd.addParameter("beta")
            cmd.addParameter("lsp")
            log.info("Using 'buf beta lsp' for version ${versionInfo?.version}")
        } else {
            cmd.addParameter("lsp")
            cmd.addParameter("serve")
            log.info("Using 'buf lsp serve' for version ${versionInfo?.version}")
        }

        // Add debug flag if enabled in settings
        if (project.bufSettings.state.lspServerDebug) {
            cmd.addParameter("--debug")
        }

        // Set working directory to buf workspace root
        val projectBasePath = project.basePath
        if (projectBasePath != null) {
            cmd.withWorkDirectory(File(projectBasePath))
        }

        return cmd
    }

    /**
     * Finds the buf workspace root for a given file by searching up the directory tree
     * for buf.work.yaml or buf.yaml.
     *
     * Returns the directory containing the config file, or null if not found.
     */
    private fun findBufWorkspaceRoot(file: VirtualFile): VirtualFile? {
        var current = file.parent
        while (current != null) {
            // Check for workspace config first (takes precedence)
            if (current.findChild("buf.work.yaml") != null) {
                return current
            }
            // Check for module config
            if (current.findChild(BufConfig.BUF_YAML) != null) {
                return current
            }
            current = current.parent
        }
        return null
    }
}
