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
import build.buf.intellij.lsp.BufLspServerSupportProvider
import build.buf.intellij.lsp.BufVersionDetector
import build.buf.intellij.settings.bufSettings
import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.readText

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

    override fun tearDown() {
        if (isWindows) {
            // Stop LSP servers and wait for them to fully terminate before the framework's
            // thread-leak check fires. On Windows, buf lsp serve runs via wsl.exe; the
            // Windows-to-WSL bridge threads outlive the process kill and trigger ThreadLeakTracker
            // unless we wait for the servers to reach a non-Running state here.
            LspServerManager.getInstance(project).stopServers(BufLspServerSupportProvider::class.java)
            PlatformTestUtil.waitWithEventsDispatching(
                "LSP servers did not stop within 15 seconds",
                {
                    LspServerManager.getInstance(project)
                        .getServersForProvider(BufLspServerSupportProvider::class.java)
                        .none { it.state == LspServerState.Running }
                },
                15,
            )
        }
        super.tearDown()
    }

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
        assumeTrue(isWindows)

        if (WslDistributionManager.getInstance().installedDistributions.isEmpty()) return

        assertThat(BufAnalyzeUtils.findWslDistro(File("/usr/local/bin/buf"))).isNotNull()
        assertThat(BufAnalyzeUtils.findWslDistro(File("C:\\Program Files\\buf\\buf.exe"))).isNull()
    }

    /**
     * Verifies that findWslDistro returns the correct named distro for a UNC WSL path
     * (\\wsl.localhost\<distro>\...), and that getWslLinuxPath strips the UNC prefix.
     * Covers https://github.com/bufbuild/intellij-buf/issues/288 where users configure
     * the path in "\\wsl.localhost\Ubuntu\usr\local\bin\buf" format.
     */
    fun testFindWslDistroAndLinuxPathForUncPath() {
        assumeTrue(isWindows)

        val installedDistros = WslDistributionManager.getInstance().installedDistributions
        if (installedDistros.isEmpty()) return

        val distro = installedDistros.first()
        // Build the UNC path using wsl.localhost (the format Windows Explorer shows and users
        // typically copy from the address bar). Both wsl.localhost and wsl$ are valid UNC roots.
        val uncPath = File("\\\\wsl.localhost\\${distro.msId}\\usr\\local\\bin\\buf")

        assertThat(BufAnalyzeUtils.findWslDistro(uncPath))
            .isNotNull()
            .extracting { it!!.msId }
            .isEqualTo(distro.msId)

        assertThat(BufAnalyzeUtils.getWslLinuxPath(uncPath)).isEqualTo("/usr/local/bin/buf")
    }

    /**
     * End-to-end test: runs `buf lint` using a UNC WSL path for the buf executable.
     * Covers the case where users configure the path as "\\wsl.localhost\Ubuntu\usr\local\bin\buf"
     * instead of the Linux-style "/usr/local/bin/buf".
     */
    fun testRunBufCommandWithUncWslPath() {
        assumeTrue(isWindows)

        val installedDistros = WslDistributionManager.getInstance().installedDistributions
        if (installedDistros.isEmpty()) return

        val distro = installedDistros.first()
        project.bufSettings.state = project.bufSettings.state.copy(
            bufCLIPath = "\\\\wsl.localhost\\${distro.msId}\\usr\\local\\bin\\buf",
        )

        configureByFolder("configuration", "test.proto")

        val workingDirectory = Paths.get(myFixture.file.virtualFile.parent.path)

        val result = runBlocking {
            BufAnalyzeUtils.runBufCommand(
                project,
                testRootDisposable,
                workingDirectory,
                listOf("lint", "--error-format=json"),
            )
        }

        // Exit -1 means the process could not be created (the regression from
        // https://github.com/bufbuild/intellij-buf/issues/288). Any other exit code
        // means buf launched successfully via WSL.
        assertThat(result.exitCode)
            .withFailMessage(
                "buf lint did not run via UNC WSL path (exit ${result.exitCode}).\n" +
                    "workingDirectory=$workingDirectory\n" +
                    "stderr: ${result.stderr}",
            )
            .isNotEqualTo(-1)
    }

    /**
     * End-to-end test: runs `buf lsp serve` via WSL and formats a .proto file through the
     * LSP server. Verifies that the full LSP stack — version detection, server launch, and
     * textDocument/formatting — works when buf lives inside WSL. Covers the user-reported
     * regression in https://github.com/bufbuild/intellij-buf/issues/288 where
     * "Code -> Reformat Code" stopped working.
     *
     * Path translation: BufLspServerDescriptor overrides getFilePath/findLocalFileByPath to
     * convert Windows paths (C:/...) to WSL mount paths (/mnt/c/...) and back, so that
     * buf lsp serve (running inside WSL) can resolve file URIs for files on the Windows filesystem.
     */
    fun testLspFormattingWithWslBuf() {
        assumeTrue(isWindows)

        if (WslDistributionManager.getInstance().installedDistributions.isEmpty()) return

        // Pre-cache buf version so that fileOpened() takes the synchronous EDT path
        // (calling ensureServerStarted directly) rather than the async pooled-thread path.
        // The async path calls ensureServerStarted from a background thread, which creates
        // the server object but never triggers the actual process launch.
        val versionInfo = BufVersionDetector.getVersionInfo(project, checkIfMissing = true)
        assertThat(versionInfo)
            .withFailMessage("buf --version failed via WSL; check that buf is installed at /usr/local/bin/buf")
            .isNotNull()
        assertThat(versionInfo!!.supportsLsp)
            .withFailMessage("buf ${versionInfo.version} does not support LSP (requires 1.59.0+)")
            .isTrue()

        System.setProperty("buf.test.disableLsp", "false")

        configureByFolder("formatting", "largeprotofile.proto")

        val lspServer = waitForLspServer()
        assertThat(lspServer)
            .withFailMessage("buf LSP server did not reach Running state via WSL")
            .isNotNull()

        // Normalize line endings: on Windows, git core.autocrlf=true checks out files with
        // CRLF, but buf lsp serve (running inside WSL/Linux) formats with LF. Without
        // normalization the strings never match and the test times out.
        val expected = findTestDataFolder().resolve("formatting-expected/largeprotofile.proto").readText()
            .replace("\r\n", "\n")

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
        assumeTrue(isWindows)

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
            .isNotEqualTo(-1)
    }

    /**
     * Verifies that getWslLinuxPath converts Java-normalized backslashes back to forward slashes.
     * On Windows, Java's File normalizes "/" separators to "\", so a Linux-style path configured
     * as "/usr/local/bin/buf" becomes "\usr\local\bin\buf" internally. getWslLinuxPath must
     * restore the forward slashes before passing the path to wsl.exe.
     *
     * This test is platform-independent: the path string manipulation does not require WSL.
     */
    fun testGetWslLinuxPath_nonUncPath() {
        // Simulate the Windows-normalized form of "/usr/local/bin/buf"
        assertThat(BufAnalyzeUtils.getWslLinuxPath(File("\\usr\\local\\bin\\buf")))
            .isEqualTo("/usr/local/bin/buf")
    }

    /**
     * Verifies that getWslLinuxPath strips the UNC prefix for wsl$ paths (older Windows builds).
     * Both \\wsl.localhost\<distro>\... and \\wsl$\<distro>\... are valid UNC roots for WSL;
     * the path component after the distro name must be extracted and converted to a Linux path.
     *
     * This test is platform-independent: the regex and string manipulation do not require WSL.
     */
    fun testGetWslLinuxPath_wslDollarUncPath() {
        val file = File("""\\wsl${'$'}\Ubuntu\usr\local\bin\buf""")
        assertThat(BufAnalyzeUtils.getWslLinuxPath(file)).isEqualTo("/usr/local/bin/buf")
    }

    /**
     * Verifies that findWslDistro resolves the correct named distro from a wsl$ UNC path.
     * The wsl$ share (used by older Windows builds) is equivalent to wsl.localhost and
     * must be handled by the same UNC detection logic.
     */
    fun testFindWslDistroForWslDollarUncPath() {
        assumeTrue(isWindows)

        val installedDistros = WslDistributionManager.getInstance().installedDistributions
        if (installedDistros.isEmpty()) return

        val distro = installedDistros.first()
        val uncPath = File("""\\wsl${'$'}\${distro.msId}\usr\local\bin\buf""")

        assertThat(BufAnalyzeUtils.findWslDistro(uncPath))
            .isNotNull()
            .extracting { it!!.msId }
            .isEqualTo(distro.msId)
    }
}
