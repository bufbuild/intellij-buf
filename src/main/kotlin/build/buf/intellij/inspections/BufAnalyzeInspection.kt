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

package build.buf.intellij.inspections

import build.buf.intellij.BufBundle
import build.buf.intellij.BufPluginService
import build.buf.intellij.annotator.BufAnalyzeResult
import build.buf.intellij.annotator.BufAnalyzeUtils
import build.buf.intellij.annotator.addHighlightsForFile
import build.buf.intellij.annotator.createDisposableOnAnyPsiChange
import build.buf.intellij.vendor.isProtobufFile
import build.buf.intellij.vendor.protobufLanguage
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInspection.GlobalInspectionContext
import com.intellij.codeInspection.GlobalSimpleInspectionTool
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptionsProcessor
import com.intellij.codeInspection.ProblemDescriptorUtil
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiModificationTracker
import java.util.concurrent.ConcurrentHashMap

private val ANALYZED_FILES: Key<MutableSet<PsiFile>> = Key.create("BUF_ANALYZED_FILES")
private const val MAX_ANALYZE_ATTEMPTS = 3

class BufAnalyzeInspection : GlobalSimpleInspectionTool() {
    private val appService = service<BufPluginService>()

    override fun getDisplayName(): String = BufBundle.message("buf.inspection.analyze.display.name")

    override fun inspectionStarted(
        manager: InspectionManager,
        globalContext: GlobalInspectionContext,
        problemDescriptionsProcessor: ProblemDescriptionsProcessor,
    ) {
        globalContext.putUserData(ANALYZED_FILES, ConcurrentHashMap.newKeySet())
    }

    // The platform calls checkFile inside a read action. Waiting on buf here
    // deadlocks, because the analysis saves documents in a write action first.
    override fun checkFile(
        file: PsiFile,
        manager: InspectionManager,
        problemsHolder: ProblemsHolder,
        globalContext: GlobalInspectionContext,
        problemDescriptionsProcessor: ProblemDescriptionsProcessor,
    ) {
        if (!file.isProtobufFile()) return
        globalContext.getUserData(ANALYZED_FILES)?.add(file)
    }

    override fun inspectionFinished(
        manager: InspectionManager,
        globalContext: GlobalInspectionContext,
        problemDescriptionsProcessor: ProblemDescriptionsProcessor,
    ) {
        val analyzedFiles = globalContext.getUserData(ANALYZED_FILES) ?: return
        globalContext.putUserData(ANALYZED_FILES, null)
        if (analyzedFiles.isEmpty()) return
        val project = manager.project
        val protoModificationTracker = PsiModificationTracker.getInstance(project).forLanguage(protobufLanguage())
        repeat(MAX_ANALYZE_ATTEMPTS) { attempt ->
            ProgressManager.checkCanceled()
            val modificationCount = protoModificationTracker.modificationCount
            val disposable = project.messageBus.createDisposableOnAnyPsiChange()
                .also { Disposer.register(appService, it) }
            val lazyResults = runReadAction {
                val fileIndex = ProjectFileIndex.getInstance(project)
                analyzedFiles.filter { it.isValid }
                    .mapNotNull { fileIndex.getContentRootForFile(it.virtualFile) }
                    .distinct()
                    .map { BufAnalyzeUtils.checkLazily(project, disposable, it.toNioPath()) }
            }
            val futures = lazyResults.map { lazyResult ->
                ApplicationManager.getApplication().executeOnPooledThread<BufAnalyzeResult?> { lazyResult.value }
            }
            val results = futures.mapNotNull { ProgressIndicatorUtils.awaitWithCheckCanceled(it) }
            val reported = runReadAction {
                // Proto PSI changed while buf was running, so offsets may no longer
                // line up. Retry, but report on the last attempt rather than looping
                // for as long as the user keeps editing.
                val isLastAttempt = attempt == MAX_ANALYZE_ATTEMPTS - 1
                if (!isLastAttempt && protoModificationTracker.modificationCount != modificationCount) {
                    return@runReadAction false
                }
                for (file in analyzedFiles) {
                    if (!file.isValid) continue
                    for (result in results) {
                        reportProblems(file, result, globalContext, problemDescriptionsProcessor)
                    }
                }
                true
            }
            if (reported) return
        }
    }

    private fun reportProblems(
        file: PsiFile,
        result: BufAnalyzeResult,
        globalContext: GlobalInspectionContext,
        problemDescriptionsProcessor: ProblemDescriptionsProcessor,
    ) {
        val highlightInfos = mutableListOf<HighlightInfo>()
        highlightInfos.addHighlightsForFile(file, result)

        val problemDescriptors = highlightInfos.mapNotNull { ProblemDescriptorUtil.toProblemDescriptor(file, it) }
        for (descriptor in problemDescriptors) {
            val element = descriptor.psiElement ?: continue
            val refElement =
                globalContext.refManager.getReference(element) ?: globalContext.refManager.getReference(file)
            problemDescriptionsProcessor.addProblemElement(refElement, descriptor)
        }
    }
}
