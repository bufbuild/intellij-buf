package com.github.bufbuild.intellij.annotator

import com.github.bufbuild.intellij.icons.BufLintGutterIconRenderer
import com.github.bufbuild.intellij.model.BufLintIssue
import com.github.bufbuild.intellij.settings.bufSettings
import com.intellij.codeHighlighting.DirtyScopeTrackingHighlightingPassFactory
import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.codeInsight.daemon.impl.*
import com.intellij.ide.plugins.PluginManagerCore.isUnitTestMode
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.protobuf.lang.psi.PbFile
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.AnyPsiChangeListener
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.util.messages.MessageBus
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import kotlin.io.path.relativeToOrNull

class BufLintPass(
    private val factory: BufLintPassFactory,
    private val file: PsiFile,
    private val editor: Editor
) : TextEditorHighlightingPass(file.project, editor.document), DumbAware {
    @Suppress("UnstableApiUsage", "DEPRECATION")
    private val annotationHolder: AnnotationHolderImpl = AnnotationHolderImpl(AnnotationSession(file), false)

    @Volatile
    private var annotationInfo: Lazy<BufLintResult?>? = null
    private val annotationResult: BufLintResult? get() = annotationInfo?.value

    @Volatile
    private var disposable: Disposable = myProject

    override fun doCollectInformation(progress: ProgressIndicator) {
        annotationHolder.clear()
        if (file !is PbFile) return
        if (!myProject.bufSettings.state.backgroundLintingEnabled) return

        val contentRootForFile = ProjectFileIndex.getInstance(myProject)
            .getContentRootForFile(file.virtualFile) ?: return

        disposable = myProject.messageBus.createDisposableOnAnyPsiChange()
            .also { Disposer.register(myProject, it) }

        annotationInfo = BufLintUtils.checkLazily(myProject, disposable, contentRootForFile.toNioPath())
    }

    override fun doApplyInformationToEditor() {
        if (file !is PbFile) return

        if (annotationInfo == null) {
            disposable = myProject
            doFinish(emptyList())
            return
        }

        val update = object : Update(file) {
            override fun setRejected() {
                super.setRejected()
                doFinish(highlights)
            }

            override fun run() {
                BackgroundTaskUtil.runUnderDisposeAwareIndicator(disposable, Runnable {
                    val annotationResult = annotationResult ?: return@Runnable
                    runReadAction {
                        ProgressManager.checkCanceled()
                        doApply(annotationResult)
                        ProgressManager.checkCanceled()
                        doFinish(highlights)
                    }
                })
            }

            override fun canEat(update: Update?): Boolean = true
        }

        if (isUnitTestMode) {
            update.run()
        } else {
            factory.scheduleExternalActivity(update)
        }
    }

    private fun doApply(annotationResult: BufLintResult) {
        if (file !is PbFile || !file.isValid) return
        try {
            @Suppress("UnstableApiUsage")
            annotationHolder.runAnnotatorWithContext(file) { _, holder ->
                holder.createAnnotationsForFile(file, annotationResult)
            }
        } catch (t: Throwable) {
            if (t is ProcessCanceledException) throw t
            LOG.error(t)
        }
    }

    private fun doFinish(highlights: List<HighlightInfo>) {
        invokeLater(ModalityState.stateForComponent(editor.component)) {
            if (Disposer.isDisposed(disposable)) return@invokeLater
            UpdateHighlightersUtil.setHighlightersToEditor(
                myProject,
                document,
                0,
                file.textLength,
                highlights,
                colorsScheme,
                id
            )
            DaemonCodeAnalyzerEx.getInstanceEx(myProject).fileStatusMap.markFileUpToDate(document, id)
        }
    }

    private val highlights: List<HighlightInfo>
        get() = annotationHolder.map(HighlightInfo::fromAnnotation)

    companion object {
        private val LOG: Logger = logger<BufLintPass>()
    }
}

class BufLintPassFactory(
    project: Project,
    registrar: TextEditorHighlightingPassRegistrar
) : DirtyScopeTrackingHighlightingPassFactory {
    private val myPassId: Int = registrar.registerTextEditorHighlightingPass(
        this,
        null,
        null,
        false,
        -1
    )

    private val externalLinterQueue = MergingUpdateQueue(
        "BufLintQueue",
        TIME_SPAN,
        true,
        MergingUpdateQueue.ANY_COMPONENT,
        project,
        null,
        false
    )

    override fun createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass? {
        FileStatusMap.getDirtyTextRange(editor, passId) ?: return null
        return BufLintPass(this, file, editor)
    }

    override fun getPassId(): Int = myPassId

    fun scheduleExternalActivity(update: Update) = externalLinterQueue.queue(update)

    companion object {
        private const val TIME_SPAN: Int = 300
    }
}


fun MessageBus.createDisposableOnAnyPsiChange(): Disposable {
    val disposable = Disposer.newDisposable("Dispose on PSI change")
    connect(disposable).subscribe(
        PsiManagerImpl.ANY_PSI_CHANGE_TOPIC,
        object : AnyPsiChangeListener {
            override fun beforePsiChanged(isPhysical: Boolean) {
                if (isPhysical) {
                    Disposer.dispose(disposable)
                }
            }
        }
    )
    return disposable
}

fun AnnotationHolder.createAnnotationsForFile(
    file: PbFile,
    annotationResult: BufLintResult
) {
    val doc = file.viewProvider.document
        ?: error("Can't find document for $file in external linter")

    val wd = annotationResult.workingDirectory
    val filteredIssues = annotationResult.issue
        .filter { it.path == file.virtualFile.toNioPath().relativeToOrNull(wd)?.toString() }
    for (issue in filteredIssues) {
        val annotationBuilder = newAnnotation(HighlightSeverity.WARNING, issue.message)
            .range(issue.toTextRange(doc) ?: continue)
            .problemGroup { issue.type }
            .gutterIconRenderer(BufLintGutterIconRenderer)
            .needsUpdateOnTyping(true)

        annotationBuilder.create()
    }
}

fun BufLintIssue.toTextRange(document: Document): TextRange? {
    val startOffset = toOffset(document, this.start_line, this.start_column)
    val endOffset = toOffset(document, this.end_line, this.end_column)
    return if (startOffset != null && endOffset != null && startOffset < endOffset) {
        TextRange(startOffset, endOffset)
    } else {
        null
    }
}

@Suppress("NAME_SHADOWING")
fun toOffset(document: Document, line: Int, column: Int): Int? {
    val line = line - 1
    val column = column - 1
    if (line < 0 || line >= document.lineCount) return null
    return (document.getLineStartOffset(line) + column)
        .takeIf { it <= document.textLength }
}

