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
import com.intellij.execution.wsl.WslDistributionManager
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import java.io.File
import java.nio.file.Paths

/**
 * Integration tests for Buf plugin behavior in a WSL (Windows Subsystem for Linux) environment.
 *
 * These tests only run on Windows. The CI workflow in `.github/workflows/wsl-test.yml`
 * sets up WSL (Ubuntu 24.04) with `buf` installed at `/usr/local/bin/buf`.
 *
 * Run with `-Pwsl` to include the `wsl` package (excluded from regular test runs).
 *
 * Covers https://github.com/bufbuild/intellij-buf/issues/288: when buf is installed in WSL,
 * IntelliJ routes the process through IJent. Passing a Windows working directory to
 * `ScriptRunnerUtil.execute` caused `ProcessNotCreatedException` because IJent could not
 * resolve the path inside the WSL filesystem. The fix detects the WSL executable, converts
 * the command to run via `wsl.exe` using `WslDistribution.patchCommandLine`, and translates
 * the working directory to its WSL mount path (e.g. `C:\Work` → `/mnt/c/Work`).
 */
class BufWslTest : BufTestBase() {
    override fun getBasePath(): String = "lsp"

    // Whether this test is running on a Windows host. WSL tests are excluded from the regular
    // test task via build.gradle.kts; this flag is a secondary guard for accidental local runs.
    private val isWindows = System.getProperty("os.name", "").startsWith("Windows")

    override fun setUp() {
        if (!isWindows) return

        super.setUp()

        // project.basePath is used as the working directory in the test. IntelliJ creates
        // this path lazily; GeneralCommandLine validates it exists before launching, so we
        // must create it up front (same as BufLspServerTest.setUp).
        project.basePath?.let { File(it).mkdirs() }

        // Use the Linux-style path for buf directly. This replicates what the full IDE does:
        // PathEnvironmentVariableUtil.findExecutableInPathOnAnyOS returns /usr/local/bin/buf,
        // which triggers the WSL code path in BufAnalyzeUtils. In headless test mode that
        // API returns null, so we set the path explicitly.
        project.bufSettings.state = project.bufSettings.state.copy(bufCLIPath = "/usr/local/bin/buf")
    }

    /**
     * Verifies that findWslDistro returns a distribution for a Linux-style buf path on Windows,
     * and null for a Windows-style path. This ensures the WSL detection logic is correct.
     */
    fun testFindWslDistro() {
        if (!isWindows) return

        if (WslDistributionManager.getInstance().installedDistributions.isEmpty()) return

        assertThat(BufAnalyzeUtils.findWslDistro(File("/usr/local/bin/buf"))).isNotNull()
        assertThat(BufAnalyzeUtils.findWslDistro(File("C:\\Program Files\\buf\\buf.exe"))).isNull()
    }

    /**
     * End-to-end test: runs `buf lint` with a WSL buf executable via `wsl.exe`. Covers the
     * regression in https://github.com/bufbuild/intellij-buf/issues/288 where a Windows working
     * directory caused `ProcessNotCreatedException`.
     *
     * The fix in `BufAnalyzeUtils.createProcessHandler` routes the command through
     * `WslDistribution.patchCommandLine` (i.e. `wsl.exe --cd /mnt/c/... -- buf lint`) instead
     * of `ScriptRunnerUtil.execute`, bypassing IJent entirely.
     */
    fun testRunBufCommandWithWslBuf() {
        if (!isWindows) return

        if (WslDistributionManager.getInstance().installedDistributions.isEmpty()) return

        configureByFolder("configuration", "test.proto")

        // configureByFolder places buf.yaml alongside test.proto; use that directory as the
        // working directory so buf can discover the workspace config.
        val workingDirectory = Paths.get(myFixture.file.virtualFile.parent.path)

        // Run buf lint — the same command the annotator issues — with JSON error format so
        // output is machine-readable. Exit 0 means no issues; exit 100 means lint found
        // annotations. Either is a successful execution through WSL (exit -1 means the
        // process could not be created, which is the regression from issue #288).
        val result = runBlocking {
            BufAnalyzeUtils.runBufCommand(
                project,
                testRootDisposable,
                workingDirectory,
                listOf("lint", "--error-format=json"),
            )
        }

        assertThat(result.exitCode)
            .withFailMessage(
                "buf lint did not run (exit ${result.exitCode}); buf may not have launched via wsl.exe.\n" +
                    "workingDirectory=$workingDirectory\n" +
                    "stderr: ${result.stderr}",
            )
            .isIn(0, 100)
    }
}
