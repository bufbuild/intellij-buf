package com.github.bufbuild.intellij.annotator

import com.github.bufbuild.intellij.BufBundle
import com.github.bufbuild.intellij.cache.ProjectCache
import com.github.bufbuild.intellij.model.BufLintIssue
import com.github.bufbuild.intellij.status.BufCLIWidget
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ScriptRunnerUtil
import com.intellij.ide.plugins.PluginManagerCore.isUnitTestMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.util.PsiModificationTracker
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture

/**
 * Inspired by Rust's RsExternalLinterUtils
 *
 * @see https://github.com/intellij-rust/intellij-rust
 */
object BufLintUtils {
    private val LOG: Logger = logger<BufLintUtils>()
    const val TEST_MESSAGE: String = "RsExternalLint"
    public fun findBufExecutable() = PathEnvironmentVariableUtil.findExecutableInPathOnAnyOS("buf")

    fun checkLazily(
        project: Project,
        owner: Disposable,
        workingDirectory: Path,
        withWidget: Boolean = true
    ): Lazy<BufLintResult?> {
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
    ): BufLintResult? {
        val widget = WriteAction.computeAndWait<BufCLIWidget?, Throwable> {
            FileDocumentManager.getInstance().saveAllDocuments()
            val statusBar = WindowManager.getInstance().getStatusBar(project)
            statusBar?.getWidget(BufCLIWidget.ID) as? BufCLIWidget
        }

        val future = CompletableFuture<BufLintResult?>()
        val task = object : Task.Backgroundable(project, BufBundle.message("linter.in.progress"), false) {

            override fun run(indicator: ProgressIndicator) {
                widget?.inProgress = true
                future.complete(check(project, owner, workingDirectory))
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
    ): BufLintResult? {
        ProgressManager.checkCanceled()
        val started = Instant.now()

        val issues = runBufCommand(owner, workingDirectory, listOf("lint", "--error-format=json"))
            .mapNotNull { BufLintIssue.fromJSON(it) }
        val finish = Instant.now()
        thisLogger().info("Ran buf lint in ${Duration.between(started, finish).toMillis()}ms")
        ProgressManager.checkCanceled()
        return BufLintResult(workingDirectory, issues)
    }

    private fun runBufCommand(owner: Disposable, workingDirectory: Path, arguments: List<String>): Iterable<String> {
        val bufExecutable = findBufExecutable() ?: return emptyList()
        val result = LinkedList<String>()
        val handler = ScriptRunnerUtil.execute(
            bufExecutable.absolutePath,
            workingDirectory.toString(),
            null,
            arguments.toTypedArray()
        )
        handler.addProcessListener(object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: com.intellij.openapi.util.Key<*>) {
                result.add(event.text)
            }
        }, owner)
        handler.startNotify()
        handler.waitFor()
        return result
    }

    private val externalLinterLazyResultCache =
        ProjectCache<Path, Lazy<BufLintResult?>>("externalLinterLazyResultCache") {
            PsiModificationTracker.MODIFICATION_COUNT
        }

}

data class BufLintResult(val workingDirectory: Path, val issue: List<BufLintIssue>)
