package build.buf.intellij.annotator

import build.buf.intellij.BufBundle
import build.buf.intellij.cache.ProjectCache
import build.buf.intellij.model.BufIssue
import build.buf.intellij.settings.bufSettings
import build.buf.intellij.status.BufCLIWidget
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ScriptRunnerUtil
import com.intellij.ide.plugins.PluginManagerCore.isUnitTestMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.io.exists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.io.path.relativeTo

/**
 * Inspired by Rust's RsExternalLinterUtils
 *
 * @see https://github.com/intellij-rust/intellij-rust
 */
object BufAnalyzeUtils {
    private val BUF_COMMAND_EXECUTION_TIMEOUT = Duration.ofMinutes(1)
    public fun findBufExecutable() = PathEnvironmentVariableUtil.findExecutableInPathOnAnyOS("buf")

    fun checkLazily(
        project: Project,
        owner: Disposable,
        workingDirectory: Path,
        withWidget: Boolean = true
    ): Lazy<BufAnalyzeResult?> {
        check(ApplicationManager.getApplication().isReadAccessAllowed)
        return externalLinterLazyResultCache.getOrPut(project, workingDirectory) {
            lazy {
                // This code will be executed out of read action in background thread
                if (!isUnitTestMode) check(!ApplicationManager.getApplication().isReadAccessAllowed)
                if (withWidget) {
                    checkWrapped(project, owner, workingDirectory)
                } else {
                    check(project, owner, workingDirectory)
                }
            }
        }
    }

    private fun checkWrapped(
        project: Project,
        owner: Disposable,
        workingDirectory: Path
    ): BufAnalyzeResult? {
        val widget = WriteAction.computeAndWait<BufCLIWidget?, Throwable> {
            FileDocumentManager.getInstance().saveAllDocuments()
            val statusBar = WindowManager.getInstance().getStatusBar(project)
            statusBar?.getWidget(BufCLIWidget.ID) as? BufCLIWidget
        }

        val future = CompletableFuture<BufAnalyzeResult?>()
        val task = object : Task.Backgroundable(project, BufBundle.message("analyzing.in.progress"), false) {

            override fun run(indicator: ProgressIndicator) {
                widget?.inProgress = true
                try {
                    future.complete(check(project, owner, workingDirectory))
                } catch (th: Throwable) {
                    future.complete(null)
                }
            }

            override fun onFinished() {
                widget?.inProgress = false
            }
        }
        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, EmptyProgressIndicator())
        return future.get()
    }

    private fun check(
        project: Project,
        owner: Disposable,
        workingDirectory: Path
    ): BufAnalyzeResult? {
        ProgressManager.checkCanceled()
        val started = Instant.now()

        val issues = runBlocking {
            val lintIssues =
                if (project.bufSettings.state.backgroundLintingEnabled) {
                    runBufCommand(owner, workingDirectory, listOf("lint", "--error-format=json"))
                        .mapNotNull { BufIssue.fromJSON(it) }
                } else {
                    emptyList()
                }
            val gitRepoRoot = findGitRepositoryRoot(workingDirectory)
            val breakingArguments = when {
                project.bufSettings.state.breakingArgumentsOverride.isNotEmpty() ->
                    project.bufSettings.state.breakingArgumentsOverride

                gitRepoRoot != null ->
                    findBreakingArguments(project, workingDirectory, gitRepoRoot)

                else -> emptyList()
            }
            val breakingIssues =
                if (project.bufSettings.state.backgroundBreakingEnabled && breakingArguments.isNotEmpty()) {
                    runBufCommand(
                        owner,
                        workingDirectory,
                        listOf("breaking", "--error-format=json") + breakingArguments
                    ).mapNotNull { BufIssue.fromJSON(it) }
                } else {
                    emptyList()
                }
            lintIssues + breakingIssues
        }
        val finish = Instant.now()
        thisLogger().info("Ran buf lint in ${Duration.between(started, finish).toMillis()}ms")
        ProgressManager.checkCanceled()
        return BufAnalyzeResult(workingDirectory, issues)
    }

    private fun findBreakingArguments(project: Project, workingDirectory: Path, gitRepoRoot: Path): List<String> {
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

    public suspend fun runBufCommand(
        owner: Disposable,
        workingDirectory: Path,
        arguments: List<String>
    ): Iterable<String> = withContext(Dispatchers.IO) {
        val bufExecutable = findBufExecutable() ?: return@withContext emptyList()
        val result = LinkedList<String>()
        val handler = ScriptRunnerUtil.execute(
            bufExecutable.absolutePath,
            workingDirectory.toString(),
            null,
            arguments.toTypedArray()
        )
        handler.addProcessListener(object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: com.intellij.openapi.util.Key<*>) {
                result.add(event.text.trimEnd())
            }
        }, owner)
        handler.startNotify()
        handler.waitFor(BUF_COMMAND_EXECUTION_TIMEOUT.toMillis())
        result.drop(1) // first line is always the CMD executed
    }

    private val externalLinterLazyResultCache =
        ProjectCache<Path, Lazy<BufAnalyzeResult?>>("externalLinterLazyResultCache") {
            PsiModificationTracker.MODIFICATION_COUNT
        }

}

data class BufAnalyzeResult(val workingDirectory: Path, val issue: List<BufIssue>)
