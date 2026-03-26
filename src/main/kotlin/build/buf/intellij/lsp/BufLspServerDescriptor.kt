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
import build.buf.intellij.annotator.BufAnalyzeUtils
import build.buf.intellij.config.BufConfig
import build.buf.intellij.settings.BufCLIUtils
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import com.intellij.platform.lsp.api.customization.LspFormattingSupport
import com.intellij.platform.lsp.api.customization.LspSemanticTokensSupport
import java.net.URI
import java.nio.file.Path

/**
 * LSP server descriptor for the Buf Language Server.
 * Manages the lifecycle of the buf LSP server process.
 * Uses `buf lsp serve` for buf 1.59.0+.
 */
class BufLspServerDescriptor(project: Project) : ProjectWideLspServerDescriptor(project, "Buf") {
    private val log = logger<BufLspServerDescriptor>()

    // Set in createCommandLine(); non-null when buf is running inside WSL.
    // Used by getFileUri/findLocalFileByPath to translate between Windows and WSL paths.
    @Volatile
    private var wslDistro: WSLDistribution? = null

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

        // File must be in a buf workspace (have a buf.yaml or buf.work.yaml in parent hierarchy).
        // Note: we intentionally do not call ProjectFileIndex.isInContent here because
        // isSupportedFile is called from the EDT (e.g. via LspFormattingService.canFormat),
        // and isInContent triggers a slow workspace index update that is prohibited on EDT.
        // The buf workspace check is sufficient: files outside the project won't have a
        // buf.yaml ancestor, and the LSP server is project-scoped regardless.
        return findBufWorkspaceRoot(file) != null
    }

    override fun createCommandLine(): GeneralCommandLine {
        val bufExe = BufCLIUtils.getConfiguredBufExecutable(project)
            ?: throw IllegalStateException(BufBundle.message("lsp.cli.not.found"))

        // Store wslDistro before building the command line so that getFileUri and
        // findLocalFileByPath can translate paths as soon as the server starts.
        wslDistro = BufAnalyzeUtils.findWslDistro(bufExe)

        log.info("Starting buf lsp serve")
        return BufAnalyzeUtils.createBufCommandLine(bufExe, listOf("lsp", "serve", "--log-format=text"))
    }

    // Builds a file URI for use in outgoing LSP messages (textDocument/didOpen etc.).
    // When buf runs inside WSL, we must send file:///mnt/c/... URIs rather than the
    // default file:///c%3A/... Windows-style URIs, because buf (running in Linux) cannot
    // resolve Windows drive-letter paths.
    //
    // We override getFileUri rather than getFilePath because getFilePath feeds into
    // VirtualFileManager.constructUrl which prepends "file://" — yielding four slashes
    // (file:////mnt/c/...) for a path that already begins with "/".  Overriding getFileUri
    // lets us construct the URI directly with the correct form.
    //
    // Uses WSLDistribution.getWslPath so the mount root from /etc/wsl.conf is respected
    // (e.g. a custom "root = /windows" config maps C:\ to /windows/c/ instead of /mnt/c/).
    override fun getFileUri(file: VirtualFile): String {
        val distro = wslDistro ?: return super.getFileUri(file)
        val wslPath = distro.getWslPath(Path.of(file.path)) ?: return super.getFileUri(file)
        // URI(scheme, authority, path, query, fragment) with empty authority produces
        // the triple-slash form: file:///mnt/c/...
        return URI("file", "", wslPath, null, null).toString()
    }

    // Translates WSL Linux paths arriving from the LSP server back to Windows paths so that
    // IntelliJ can locate the corresponding VirtualFile. buf lsp serve sends back paths in
    // the mount layout configured by /etc/wsl.conf (e.g. /mnt/c/... or /c/... depending on
    // the mount root). WSLDistribution.getWindowsPath respects the same wsl.conf settings
    // as getWslPath, ensuring symmetry with getFileUri.
    override fun findLocalFileByPath(path: String): VirtualFile? {
        val distro = wslDistro ?: return super.findLocalFileByPath(path)
        return super.findLocalFileByPath(distro.getWindowsPath(path))
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
