// Copyright 2022-2023 Buf Technologies, Inc.
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

package build.buf.intellij.formatter

import build.buf.intellij.BufBundle
import build.buf.intellij.annotator.BufAnalyzeUtils
import build.buf.intellij.settings.bufSettings
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
class BufFormatterService : AsyncDocumentFormattingService() {
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
                if (output.firstOrNull()?.startsWith("unknown command") == true) {
                    request.onError(
                        BufBundle.message("formatter.title"),
                        BufBundle.message("formatter.cli.version.not.supported")
                    )
                } else {
                    request.onTextReady(output.joinToString(separator = "\n"))
                }
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
