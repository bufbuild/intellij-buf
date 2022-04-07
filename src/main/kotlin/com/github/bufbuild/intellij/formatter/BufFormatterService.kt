package com.github.bufbuild.intellij.formatter

import com.github.bufbuild.intellij.BufBundle
import com.github.bufbuild.intellij.annotator.BufAnalyzeUtils
import com.github.bufbuild.intellij.settings.bufSettings
import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.formatting.service.AsyncDocumentFormattingService
import com.intellij.formatting.service.AsyncFormattingRequest
import com.intellij.formatting.service.FormattingService
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.protobuf.lang.psi.PbFile
import com.intellij.psi.PsiFile
import com.intellij.psi.formatter.FormatterUtil
import kotlinx.coroutines.runBlocking

@Suppress("UnstableApiUsage")
class BufFormatterService: AsyncDocumentFormattingService() {
    override fun getFeatures(): Set<FormattingService.Feature> = emptySet()

    override fun canFormat(file: PsiFile): Boolean =
        file is PbFile && file.project.bufSettings.state.useBufFormatter && getFormattingReason() == FormattingReason.ReformatCode

    override fun createFormattingTask(request: AsyncFormattingRequest): FormattingTask? {
        val context = request.context
        val project = context.project
        val file = context.virtualFile ?: return null
        val contentRootForFile = ProjectFileIndex.getInstance(project)
            .getContentRootForFile(file) ?: return null

        val relativePath = VfsUtil.getRelativePath(file, contentRootForFile) ?: return null

        return object : FormattingTask {
            private val disposable = Disposer.newDisposable()

            override fun run() {
                val output = runBlocking {
                    BufAnalyzeUtils.runBufCommand(
                        disposable,
                        contentRootForFile.toNioPath(),
                        listOf("format", relativePath)
                    )
                }
                request.onTextReady(output.joinToString(separator = "\n"))
            }

            override fun cancel(): Boolean {
                disposable.dispose()
                return true
            }

            override fun isRunUnderProgress(): Boolean = true
        }
    }

    override fun getNotificationGroupId(): String = BufBundle.getMessage("name")

    override fun getName(): String = BufBundle.getMessage("format.name")

    companion object {
        private enum class FormattingReason {
            ReformatCode,
            ReformatCodeBeforeCommit,
            Implicit
        }

        private fun getFormattingReason(): FormattingReason =
            when (CommandProcessor.getInstance().currentCommandName) {
                ReformatCodeProcessor.getCommandName() -> FormattingReason.ReformatCode
                FormatterUtil.getReformatBeforeCommitCommandName() -> FormattingReason.ReformatCodeBeforeCommit
                else -> FormattingReason.Implicit
            }
    }
}
