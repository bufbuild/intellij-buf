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

package build.buf.intellij.wsl

import build.buf.intellij.annotator.BufAnalyzeUtils
import build.buf.intellij.base.BufTestBase
import build.buf.intellij.settings.bufSettings
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.Path

/**
 * Integration tests for Buf plugin behavior in a WSL (Windows Subsystem for Linux) environment.
 *
 * These tests only run on Windows. They require `buf` to be installed inside WSL and the
 * `BUF_WSL_CLI` environment variable set to the Linux path of the buf binary (e.g.
 * `/usr/local/bin/buf`). The CI workflow in `.github/workflows/wsl-test.yml` provides this
 * setup automatically.
 *
 * **Known bug (https://github.com/bufbuild/intellij-buf/issues/288)**: When buf is installed
 * in WSL and IntelliJ routes execution through IJent, passing a Windows working directory
 * (e.g. `C:\Work\project`) to `ScriptRunnerUtil.execute` fails because IJent cannot resolve
 * the path inside the WSL filesystem. The tests below are expected to fail until the
 * path-translation fix lands.
 */
class BufWslTest : BufTestBase() {
    override fun getBasePath(): String = "lsp"

    // Whether this test is running on a Windows host. WSL tests are excluded from the regular
    // test task via build.gradle.kts; this flag is a secondary guard for accidental local runs.
    private val isWindows = System.getProperty("os.name", "").startsWith("Windows")

    override fun setUp() {
        if (!isWindows) return

        super.setUp()

        // Override the configured buf CLI path to the WSL Linux binary.
        // We intentionally skip File.isFile here: a Linux path like /usr/local/bin/buf is not
        // a valid Windows File path, so the check would always fail.
        val wslBufPath = System.getenv("BUF_WSL_CLI") ?: "/usr/local/bin/buf"
        project.bufSettings.state = project.bufSettings.state.copy(bufCLIPath = wslBufPath)
    }

    /**
     * Reproduces https://github.com/bufbuild/intellij-buf/issues/288.
     *
     * When buf is installed in WSL, IntelliJ routes the process through IJent. The working
     * directory passed to `ScriptRunnerUtil.execute` is a Windows path (e.g.
     * `C:\Users\...\Temp\...`), which IJent cannot resolve inside the WSL filesystem.
     * This causes `ProcessNotCreatedException` with:
     *   "Couldn't run In "C:\..." execute /usr/local/bin/buf build: 2: No such file or directory"
     *
     * The fix must translate the Windows working directory to its WSL equivalent (e.g.
     * `C:\Work\project` → `/mnt/c/Work/project`) before handing it to `ScriptRunnerUtil`.
     */
    fun testRunBufCommandWithWslBuf() {
        if (!isWindows) return

        configureByFolder("configuration", "test.proto")

        val workingDirectory = Path.of(project.basePath!!)
        val result = runBlocking {
            BufAnalyzeUtils.runBufCommand(
                project,
                testRootDisposable,
                workingDirectory,
                listOf("build"),
            )
        }

        assertThat(result.exitCode)
            .withFailMessage("buf build failed (exit ${result.exitCode}).\nstderr: ${result.stderr}")
            .isEqualTo(0)
    }
}
