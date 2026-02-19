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
import org.assertj.core.api.Assertions.assertThat

/**
 * Tests for Buf LSP server configuration and lifecycle.
 */
class BufLspServerTest : BufTestBase() {
    override fun getBasePath(): String = "lsp"

    override fun setUp() {
        super.setUp()
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
}
