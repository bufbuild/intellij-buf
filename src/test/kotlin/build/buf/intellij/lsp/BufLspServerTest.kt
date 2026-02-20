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

import build.buf.intellij.base.BufTestBase
import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ui.UIUtil
import org.assertj.core.api.Assertions.assertThat
import java.io.File
import kotlin.io.path.readText

/**
 * Tests for Buf LSP server configuration and lifecycle.
 */
class BufLspServerTest : BufTestBase() {
    override fun getBasePath(): String = "lsp"

    override fun setUp() {
        super.setUp()
        // In the test fixture, project.basePath is a path that may not exist on disk yet.
        // BufLspServerDescriptor.createCommandLine() passes it as buf's working directory;
        // create it now so the process launch does not fail with "Invalid working directory".
        // buf lsp serve discovers the actual workspace from file URIs in LSP messages, so
        // the working directory does not need to contain buf.yaml.
        project.basePath?.let { File(it).mkdirs() }
        // Pre-cache the buf version so that fileOpened() takes the synchronous EDT path
        // (calling ensureServerStarted directly) rather than the async pooled-thread path.
        // The async path calls ensureServerStarted from a background thread, which creates
        // the server object but never triggers the actual process launch.
        BufVersionDetector.getVersionInfo(project, checkIfMissing = true)
        // Re-enable LSP for this test
        System.setProperty("buf.test.disableLsp", "false")
    }

    fun testLspServerConfiguration() {
        // Configure test files
        configureByFolder("configuration", "test.proto")

        // Verify version detection works
        val versionInfo = BufVersionDetector.getVersionInfo(project, checkIfMissing = true)
        assertThat(versionInfo).isNotNull()
        assertThat(versionInfo!!.supportsLsp).isTrue()

        // Verify we can create the LSP server descriptor
        val descriptor = BufLspServerDescriptor(project)
        assertThat(descriptor).isNotNull()

        // Verify the descriptor recognizes .proto files in buf workspaces
        val protoFile = myFixture.file.virtualFile
        assertThat(descriptor.isSupportedFile(protoFile)).isTrue()

        // Verify the command line is created correctly
        val commandLine = descriptor.createCommandLine()
        assertThat(commandLine.exePath).isNotNull()
        assertThat(commandLine.exePath).contains("buf")

        // Verify it includes the lsp serve command
        val params = commandLine.parametersList.parameters
        assertThat(params).contains("lsp")
        assertThat(params).contains("serve")
    }

    /**
     * Tests that formatting a .proto file via the IDE routes through the Buf LSP server
     * and produces correctly formatted output. This test actually starts `buf lsp serve`.
     */
    fun testLspFormatting() {
        configureByFolder("formatting", "largeprotofile.proto")

        val lspServer = waitForLspServer()
        assertThat(lspServer).isNotNull()

        val expected = findTestDataFolder().resolve("formatting-expected/largeprotofile.proto").readText()

        // There is a race between the LSP server reaching Running state and
        // textDocument/didOpen being sent for already-open files: the background
        // thread that sets state=Running may post the openExistingDocuments()
        // invokeLater after waitForLspServer()'s dispatchAllInvocationEvents() returns.
        // If formatting is attempted before didOpen is processed, the server returns
        // an error and the file is left unchanged.  Retry via waitWithEventsDispatching
        // so that EDT events (including the pending didOpen invokeLater) continue to be
        // processed between attempts until the output matches.
        PlatformTestUtil.waitWithEventsDispatching(
            "Buf LSP server did not produce expected formatting within 30 seconds",
            {
                WriteCommandAction.runWriteCommandAction(project, ReformatCodeProcessor.getCommandName(), null, {
                    CodeStyleManager.getInstance(project).reformat(myFixture.file)
                })
                myFixture.file.text == expected
            },
            30,
        )
        myFixture.checkResult(expected)
    }

    fun testLspFormattingEnabled() {
        configureByFolder("configuration", "test.proto")

        val descriptor = BufLspServerDescriptor(project)
        val protoFile = myFixture.file.virtualFile

        // Verify LSP formatting takes exclusive control for .proto files even when
        // the bundled Protocol Buffers plugin can format the file itself.
        val formattingSupport = descriptor.lspFormattingSupport
        assertThat(formattingSupport).isNotNull()
        assertThat(formattingSupport.shouldFormatThisFileExclusivelyByServer(protoFile, ideCanFormatThisFileItself = true, serverExplicitlyWantsToFormatThisFile = false)).isTrue()
    }

    private fun waitForLspServer(): LspServer? {
        var lspServer: LspServer? = null
        PlatformTestUtil.waitWithEventsDispatching(
            "Buf LSP server did not reach Running state within 30 seconds",
            {
                val server = ApplicationManager.getApplication().runReadAction<LspServer?> {
                    LspServerManager.getInstance(project)
                        .getServersForProvider(BufLspServerSupportProvider::class.java)
                        .firstOrNull()
                }
                if (server?.state == LspServerState.Running) {
                    lspServer = server
                    true
                } else {
                    false
                }
            },
            30,
        )
        // Flush any pending EDT events queued when the server reached Running state.
        // IntelliJ schedules textDocument/didOpen for already-open files at that point;
        // this ensures those notifications are sent before the caller triggers any LSP
        // requests (e.g. textDocument/formatting).
        UIUtil.dispatchAllInvocationEvents()
        return lspServer
    }
}
