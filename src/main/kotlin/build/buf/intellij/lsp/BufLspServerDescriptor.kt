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

import build.buf.intellij.BufBundle
import build.buf.intellij.config.BufConfig
import build.buf.intellij.settings.BufCLIUtils
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import com.intellij.platform.lsp.api.customization.LspFormattingSupport
import com.intellij.platform.lsp.api.customization.LspSemanticTokensSupport
import java.io.File

/**
 * LSP server descriptor for the Buf Language Server.
 * Manages the lifecycle of the buf LSP server process.
 * Uses `buf lsp serve` for buf 1.59.0+.
 */
class BufLspServerDescriptor(project: Project) : ProjectWideLspServerDescriptor(project, "Buf") {
    private val log = logger<BufLspServerDescriptor>()

    // Allow the bundled Protocol Buffers plugin to supply go-to--definition support;
    // if we don't do this, Go-to-definition does not work properly with local definitions.
    // See https://plugins.jetbrains.com/plugin/14004-protocol-buffers
    override val lspGoToDefinitionSupport: Boolean = false

    // Use LSP semantic tokens for syntax highlighting, which takes precedence over
    // the lexer-based highlighting from the bundled Protocol Buffers plugin.
    override val lspSemanticTokensSupport: LspSemanticTokensSupport = object : LspSemanticTokensSupport() {
        override fun shouldAskServerForSemanticTokens(psiFile: com.intellij.psi.PsiFile): Boolean = psiFile.virtualFile?.extension == "proto"
    }

    // Use LSP formatting for .proto files, which takes precedence over the bundled
    // Protocol Buffers plugin's formatter. Without this override, the IDE defers to
    // the bundled plugin because it can format .proto files itself.
    override val lspFormattingSupport: LspFormattingSupport = object : LspFormattingSupport() {
        override fun shouldFormatThisFileExclusivelyByServer(file: VirtualFile, ideCanFormatThisFileItself: Boolean, serverExplicitlyWantsToFormatThisFile: Boolean): Boolean = file.extension == "proto"
    }

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
            ?: throw IllegalStateException(BufBundle.message("lsp.cli.not.found"))

        val cmd = GeneralCommandLine()
        cmd.exePath = bufExe.absolutePath
        cmd.addParameter("lsp")
        cmd.addParameter("serve")
        cmd.addParameter("--log-format=text")

        // Set working directory to buf workspace root
        val projectBasePath = project.basePath
        if (projectBasePath != null) {
            cmd.withWorkDirectory(File(projectBasePath))
        }

        log.info("Starting buf lsp serve")
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
