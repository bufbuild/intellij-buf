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
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.process.ScriptRunnerUtil
import com.intellij.ide.plugins.PluginManagerCore.isUnitTestMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiModificationTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
        val bufExecutable = BufCLIUtils.getConfiguredBufExecutable(project) ?: return@withContext BufCmdResult(-1, emptyList(), "")
        val cmd = AtomicReference<String>()
        val stdout = mutableListOf<String>()
        val stderr = StringBuilder()
        val exitCode = AtomicInteger(-1)
        val handler = ScriptRunnerUtil.execute(
            bufExecutable.absolutePath,
            workingDirectory.toString(),
            null,
            arguments.toTypedArray(),
        )
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

    private val externalLinterLazyResultCache =
        ProjectCache<Path, Lazy<BufAnalyzeResult?>>("externalLinterLazyResultCache") {
            PsiModificationTracker.MODIFICATION_COUNT
        }
}

data class BufCmdResult(val exitCode: Int, val stdout: List<String>, val stderr: String)

data class BufAnalyzeResult(val workingDirectory: Path, val issue: List<BufIssue>)
