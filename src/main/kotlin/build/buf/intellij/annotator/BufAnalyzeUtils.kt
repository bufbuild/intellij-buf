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

package build.buf.intellij.annotator

import build.buf.intellij.cache.ProjectCache
import build.buf.intellij.config.BufConfig
import build.buf.intellij.model.BufIssue
import build.buf.intellij.settings.BufCLIUtils
import build.buf.intellij.settings.bufSettings
import build.buf.intellij.vendor.isProtobufFile
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.process.ScriptRunnerUtil
import com.intellij.execution.wsl.WSLCommandLineOptions
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.ide.plugins.PluginManagerCore.isUnitTestMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiModificationTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.exists
import kotlin.io.path.relativeTo
import kotlin.text.StringBuilder

/**
 * Inspired by Rust's RsExternalLinterUtils
 *
 * @see <a href="https://github.com/intellij-rust/intellij-rust">intellij-rust</a>
 */
object BufAnalyzeUtils {
    private val LOG: Logger = logger<BufAnalyzeUtils>()
    private val BUF_COMMAND_EXECUTION_TIMEOUT = Duration.ofMinutes(1)

    // Exit code returned by the CLI when file annotations are printed (breaking/lint results).
    private const val BUF_EXIT_CODE_FILE_ANNOTATION = 100

    fun checkLazily(
        project: Project,
        owner: Disposable,
        workingDirectory: Path,
    ): Lazy<BufAnalyzeResult?> {
        check(ApplicationManager.getApplication().isReadAccessAllowed)
        return externalLinterLazyResultCache.getOrPut(project, workingDirectory) {
            lazy {
                // This code will be executed out of read action in background thread
                if (!isUnitTestMode) check(!ApplicationManager.getApplication().isReadAccessAllowed)
                check(project, owner, workingDirectory)
            }
        }
    }

    private fun check(
        project: Project,
        owner: Disposable,
        workingDirectory: Path,
    ): BufAnalyzeResult {
        ProgressManager.checkCanceled()
        WriteAction.computeAndWait<Unit, Throwable> {
            FileDocumentManager.getInstance().saveDocuments { document ->
                val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return@saveDocuments false
                psiFile.isProtobufFile() || BufConfig.CONFIG_FILES.contains(psiFile.name)
            }
        }
        val started = Instant.now()

        val issues = runBlocking {
            val lintIssues =
                if (project.bufSettings.state.backgroundLintingEnabled) {
                    runBufCommand(
                        project,
                        owner,
                        workingDirectory,
                        listOf("lint", "--error-format=json"),
                        expectedExitCodes = setOf(0, BUF_EXIT_CODE_FILE_ANNOTATION),
                    ).stdout.mapNotNull { BufIssue.fromJSON(it).getOrNull() }
                } else {
                    emptyList()
                }
            val gitRepoRoot = findGitRepositoryRoot(workingDirectory)
            val breakingArguments = when {
                project.bufSettings.state.breakingArgumentsOverride.isNotEmpty() ->
                    project.bufSettings.state.breakingArgumentsOverride

                gitRepoRoot != null ->
                    findBreakingArguments(workingDirectory, gitRepoRoot)

                else -> emptyList()
            }
            val breakingIssues =
                if (project.bufSettings.state.backgroundBreakingEnabled && breakingArguments.isNotEmpty()) {
                    runBufCommand(
                        project,
                        owner,
                        workingDirectory,
                        listOf("breaking", "--error-format=json") + breakingArguments,
                        expectedExitCodes = setOf(0, BUF_EXIT_CODE_FILE_ANNOTATION),
                    ).stdout.mapNotNull { BufIssue.fromJSON(it).getOrNull() }
                } else {
                    emptyList()
                }
            lintIssues + breakingIssues
        }
        val finish = Instant.now()
        thisLogger().info("Ran buf lint in ${Duration.between(started, finish).toMillis()}ms")
        ProgressManager.checkCanceled()
        val analyzeModTracker = project.service<BufAnalyzeModificationTracker>()
        project.putUserData(BufAnalyzePassFactory.LAST_ANALYZE_MOD_COUNT, analyzeModTracker.modificationCount)
        return BufAnalyzeResult(workingDirectory, issues)
    }

    private fun findBreakingArguments(workingDirectory: Path, gitRepoRoot: Path): List<String> {
        if (gitRepoRoot == workingDirectory) {
            return listOf("--against", ".git")
        }
        val relativePart = workingDirectory.relativeTo(gitRepoRoot)
        val relativePartReversed = gitRepoRoot.relativeTo(workingDirectory).resolve(".git")
        val againstArgument = "$relativePartReversed#subdir=$relativePart"
        return listOf("--against", againstArgument)
    }

    private fun findGitRepositoryRoot(workingDirectory: Path): Path? {
        var gitParent = workingDirectory
        while (!gitParent.resolve(".git").exists() && gitParent != gitParent.root) {
            gitParent = gitParent.parent
        }
        return if (gitParent == gitParent.root) null else gitParent
    }

    suspend fun runBufCommand(
        project: Project,
        owner: Disposable,
        workingDirectory: Path,
        arguments: List<String>,
        preserveNewlines: Boolean = false,
        expectedExitCodes: Set<Int> = setOf(0),
    ): BufCmdResult = withContext(Dispatchers.IO) {
        // PSI may have changed and disposed the owner before we even start. Bail early
        // to avoid creating a process we can't track.
        if (Disposer.isDisposed(owner)) return@withContext BufCmdResult(-1, emptyList(), "")
        val bufExecutable = BufCLIUtils.getConfiguredBufExecutable(project) ?: return@withContext BufCmdResult(-1, emptyList(), "")
        val cmd = AtomicReference<String>()
        val stdout = mutableListOf<String>()
        val stderr = StringBuilder()
        val exitCode = AtomicInteger(-1)
        val handler = try {
            createProcessHandler(bufExecutable, workingDirectory, arguments)
        } catch (e: Exception) {
            LOG.warn("Failed to create buf process handler", e)
            return@withContext BufCmdResult(-1, emptyList(), "")
        }
        try {
            handler.addProcessListener(
                object : ProcessAdapter() {
                    override fun onTextAvailable(event: ProcessEvent, outputType: com.intellij.openapi.util.Key<*>) {
                        when (outputType) {
                            ProcessOutputType.SYSTEM -> cmd.set(event.text.trimEnd())
                            ProcessOutputType.STDOUT -> stdout.add(if (preserveNewlines) event.text else event.text.trimEnd())
                            ProcessOutputType.STDERR -> stderr.append(event.text)
                        }
                    }

                    override fun processTerminated(event: ProcessEvent) {
                        exitCode.set(event.exitCode)
                    }
                },
                owner,
            )
        } catch (e: Exception) {
            // owner was disposed between our check and addProcessListener (TOCTOU race):
            // PSI changed, so this analysis pass is stale — abort and clean up.
            handler.destroyProcess()
            return@withContext BufCmdResult(-1, emptyList(), "")
        }
        handler.startNotify()
        if (handler.waitFor(BUF_COMMAND_EXECUTION_TIMEOUT.toMillis())) {
            val code = exitCode.get()
            if (!expectedExitCodes.contains(code)) {
                LOG.warn("${cmd.get() ?: "buf"} unexpected exit code: $code,\nstderr: ${stderr.trimEnd()}")
            }
        } else {
            // Process failed to complete within given timeout - stop it
            handler.destroyProcess()
        }
        BufCmdResult(exitCode = exitCode.get(), stdout = stdout, stderr = stderr.toString())
    }

    // Creates the appropriate process handler for buf. When buf is a Linux-style path
    // (e.g. /usr/local/bin/buf on a Windows host with WSL), we cannot pass the command
    // directly to ProcessBuilder — it requires the process to run inside WSL. We use
    // WslDistribution.patchCommandLine to convert the command to run via wsl.exe, with
    // the working directory translated to its WSL mount path. For native buf executables
    // (Windows .exe or macOS/Linux binary) we fall back to ScriptRunnerUtil.execute.
    private fun createProcessHandler(
        bufExecutable: File,
        workingDirectory: Path,
        arguments: List<String>,
    ): ProcessHandler {
        val distro = findWslDistro(bufExecutable) ?: return ScriptRunnerUtil.execute(
            bufExecutable.absolutePath,
            workingDirectory.toString(),
            null,
            arguments.toTypedArray(),
        )
        // Convert the Windows-normalized path ("\usr\local\bin\buf") back to a Unix path
        // ("/usr/local/bin/buf") so that wsl.exe executes the correct binary inside WSL.
        // Using absolutePath here would resolve the path against the current Windows drive
        // (e.g. "D:\usr\local\bin\buf"), which WSL cannot find.
        val wslExecutablePath = getWslLinuxPath(bufExecutable)
        val cmd = GeneralCommandLine(wslExecutablePath)
        cmd.addParameters(arguments)
        val options = WSLCommandLineOptions()
        // Use the distro's own getWslPath to translate the Windows working directory
        // (e.g. C:\Work) to its WSL mount path (e.g. /mnt/c/Work). This respects any
        // custom mount root configured in /etc/wsl.conf, unlike a hardcoded /mnt/ prefix.
        options.setRemoteWorkingDirectory(distro.getWslPath(workingDirectory))
        distro.patchCommandLine(cmd, null, options)
        return OSProcessHandler(cmd)
    }

    // Matches UNC WSL paths: \\wsl.localhost\<distro>\... or \\wsl$\<distro>\...
    // On Windows, File normalizes "/" to "\" but preserves the "\\" UNC prefix.
    // Group 1: distro name (e.g. "Ubuntu-24.04"), Group 2: Linux path (e.g. "\usr\local\bin\buf")
    private val WSL_UNC_PATH_REGEX = Regex("""^\\\\(?:wsl\.localhost|wsl\$)\\([^\\]+)(\\.*)?$""")

    // Returns the WSL distribution for the given buf executable, or null if the executable
    // is not a WSL path. Handles both Linux-style paths ("/usr/local/bin/buf", normalized
    // to "\usr\local\bin\buf" by Java on Windows) and UNC WSL paths
    // ("\\wsl.localhost\Ubuntu-24.04\usr\local\bin\buf" or "\\wsl$\Ubuntu\...").
    // For UNC paths, finds the named distro rather than just the first installed one.
    // Returns null on non-Windows hosts (where installedDistributions is always empty)
    // and for native Windows/macOS/Linux executables.
    internal fun findWslDistro(bufExecutable: File): WSLDistribution? {
        val path = bufExecutable.path
        // UNC WSL path: \\wsl.localhost\<distro>\... or \\wsl$\<distro>\...
        val uncMatch = WSL_UNC_PATH_REGEX.find(path)
        if (uncMatch != null) {
            val distroName = uncMatch.groupValues[1]
            val installedDistros = WslDistributionManager.getInstance().installedDistributions
            return installedDistros.firstOrNull { it.msId.equals(distroName, ignoreCase = true) }
                ?: installedDistros.firstOrNull()
        }
        // Linux-style path: "/usr/local/bin/buf" → Java normalizes to "\usr\local\bin\buf" on Windows.
        // We accept both "/" and "\" as the first character; native Windows paths always begin
        // with a drive letter (e.g. "C:\...") and UNC paths begin with "\\" (two chars).
        if (!path.startsWith("/") && !path.startsWith("\\")) return null
        return WslDistributionManager.getInstance().installedDistributions.firstOrNull()
    }

    // Returns the Linux-style path for a WSL buf executable.
    // For UNC paths like "\\wsl.localhost\Ubuntu-24.04\usr\local\bin\buf", strips the
    // UNC server+share prefix (\\wsl.localhost\Ubuntu-24.04) to yield "/usr/local/bin/buf".
    // For Linux-style paths already normalized by Java (e.g. "\usr\local\bin\buf"), just
    // replaces backslashes with forward slashes.
    internal fun getWslLinuxPath(bufExecutable: File): String {
        val path = bufExecutable.path
        val uncMatch = WSL_UNC_PATH_REGEX.find(path)
        if (uncMatch != null) {
            val linuxPart = uncMatch.groupValues[2]
            return if (linuxPart.isEmpty()) "/" else linuxPart.replace('\\', '/')
        }
        return path.replace('\\', '/')
    }

    private val externalLinterLazyResultCache =
        ProjectCache<Path, Lazy<BufAnalyzeResult?>>("externalLinterLazyResultCache") {
            PsiModificationTracker.MODIFICATION_COUNT
        }
}

data class BufCmdResult(val exitCode: Int, val stdout: List<String>, val stderr: String)

data class BufAnalyzeResult(val workingDirectory: Path, val issue: List<BufIssue>)
