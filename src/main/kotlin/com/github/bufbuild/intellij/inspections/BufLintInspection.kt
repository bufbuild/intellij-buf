package com.github.bufbuild.intellij.inspections

import com.github.bufbuild.intellij.BufBundle
import com.github.bufbuild.intellij.annotator.BufLintResult
import com.github.bufbuild.intellij.annotator.BufLintUtils
import com.github.bufbuild.intellij.annotator.createAnnotationsForFile
import com.github.bufbuild.intellij.annotator.createDisposableOnAnyPsiChange
import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl
import com.intellij.codeInspection.*
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.protobuf.lang.annotation.PbAnnotator
import com.intellij.protobuf.lang.psi.PbFile
import com.intellij.psi.PsiFile

class BufLintInspection : GlobalSimpleInspectionTool() {
    companion object {
        private val SHORT_NAME = "BufLint"
    }

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
            .also { Disposer.register(project, it) }

        val contentRootForFile = ProjectFileIndex.getInstance(project)
            .getContentRootForFile(file.virtualFile) ?: return

        val lazyResult = runReadAction {
            // no need to show a widget we are already "in" a progress bar
            BufLintUtils.checkLazily(project, disposable, contentRootForFile.toNioPath(), false)
        }

        val result = ApplicationManager.getApplication().executeOnPooledThread<BufLintResult?> {
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
            val refElement = globalContext.refManager.getReference(element) ?: globalContext.refManager.getReference(file)
            problemDescriptionsProcessor.addProblemElement(refElement, descriptor)
        }
    }
}
