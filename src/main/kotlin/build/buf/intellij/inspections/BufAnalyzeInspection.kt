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

package build.buf.intellij.inspections

import build.buf.intellij.BufBundle
import build.buf.intellij.BufPluginService
import build.buf.intellij.annotator.BufAnalyzeResult
import build.buf.intellij.annotator.BufAnalyzeUtils
import build.buf.intellij.annotator.createAnnotationsForFile
import build.buf.intellij.annotator.createDisposableOnAnyPsiChange
import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl
import com.intellij.codeInspection.*
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Disposer
import com.intellij.protobuf.lang.psi.PbFile
import com.intellij.psi.PsiFile

class BufAnalyzeInspection : GlobalSimpleInspectionTool() {
    companion object {
        private const val SHORT_NAME = "BufAnalyze"
    }

    private val appService = service<BufPluginService>()

    override fun getShortName(): String = SHORT_NAME

    override fun getDisplayName(): String = BufBundle.getMessage("buf.inspections.lint.name")

    override fun isReadActionNeeded(): Boolean = false

    override fun checkFile(
        file: PsiFile,
        manager: InspectionManager,
        problemsHolder: ProblemsHolder,
        globalContext: GlobalInspectionContext,
        problemDescriptionsProcessor: ProblemDescriptionsProcessor
    ) {
        if (file !is PbFile) return
        val project = manager.project
        val disposable = project.messageBus.createDisposableOnAnyPsiChange()
            .also { Disposer.register(appService, it) }

        val contentRootForFile = ProjectFileIndex.getInstance(project)
            .getContentRootForFile(file.virtualFile) ?: return

        val lazyResult = runReadAction {
            // no need to show a widget we are already "in" a progress bar
            BufAnalyzeUtils.checkLazily(project, disposable, contentRootForFile.toNioPath(), false)
        }

        val result = ApplicationManager.getApplication().executeOnPooledThread<BufAnalyzeResult?> {
            lazyResult.value
        }.get()

        @Suppress("UnstableApiUsage", "DEPRECATION")
        val annotationHolder = AnnotationHolderImpl(AnnotationSession(file), false)
        @Suppress("UnstableApiUsage")
        annotationHolder.runAnnotatorWithContext(file) { _, holder ->
            holder.createAnnotationsForFile(file, result)
        }

        for (descriptor in ProblemDescriptorUtil.convertToProblemDescriptors(annotationHolder, file)) {
            val element = descriptor.psiElement ?: continue
            val refElement =
                globalContext.refManager.getReference(element) ?: globalContext.refManager.getReference(file)
            problemDescriptionsProcessor.addProblemElement(refElement, descriptor)
        }
    }
}
