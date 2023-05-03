package build.buf.intellij.annotator

import build.buf.intellij.fixes.IgnoreBufIssueQuickFix
import build.buf.intellij.model.BufIssue
import build.buf.intellij.settings.bufSettings
import com.intellij.codeHighlighting.DirtyScopeTrackingHighlightingPassFactory
import com.intellij.codeHighlighting.MainHighlightingPassFactory
import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.codeInsight.daemon.impl.*
import com.intellij.ide.plugins.PluginManagerCore.isUnitTestMode
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
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

class BufAnalyzePass(
    private val factory: BufAnalyzePassFactory,
    private val file: PsiFile,
    document: Document,
    private val withWidget: Boolean = true
) : TextEditorHighlightingPass(file.project, document), DumbAware {
    @Suppress("UnstableApiUsage", "DEPRECATION")
    private val annotationHolder: AnnotationHolderImpl = AnnotationHolderImpl(AnnotationSession(file), false)

    @Volatile
    private var annotationInfo: Lazy<BufAnalyzeResult?>? = null
    private val annotationResult: BufAnalyzeResult? get() = annotationInfo?.value

    @Volatile
    private var disposable: Disposable = myProject

    override fun doCollectInformation(progress: ProgressIndicator) {
        annotationHolder.clear()
        if (file !is PbFile) return
        if (!myProject.bufSettings.state.backgroundLintingEnabled && !myProject.bufSettings.state.backgroundBreakingEnabled) return

        val contentRootForFile = ProjectFileIndex.getInstance(myProject)
            .getContentRootForFile(file.virtualFile) ?: return

        disposable = myProject.messageBus.createDisposableOnAnyPsiChange()
            .also { Disposer.register(myProject, it) }

        annotationInfo = BufAnalyzeUtils.checkLazily(myProject, disposable, contentRootForFile.toNioPath(), withWidget)
    }

    override fun getInfos(): List<HighlightInfo> {
        if (myProject.isDisposed) {
            return emptyList()
        }
        val bufAnalyzeResult = ApplicationManager.getApplication().executeOnPooledThread<BufAnalyzeResult?> {
            annotationResult
        }.get() ?: return emptyList()
        doApply(bufAnalyzeResult)
        return annotationHolder.map {
            HighlightInfo.fromAnnotation(it)
        }
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

    private fun doApply(annotationResult: BufAnalyzeResult) {
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
        invokeLater(ModalityState.defaultModalityState()) {
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
        private val LOG: Logger = logger<BufAnalyzePass>()
    }
}

class BufAnalyzePassFactory(
    project: Project,
    registrar: TextEditorHighlightingPassRegistrar
) : DirtyScopeTrackingHighlightingPassFactory, MainHighlightingPassFactory {
    private val myPassId: Int = registrar.registerTextEditorHighlightingPass(
        this,
        null,
        null,
        false,
        -1
    )

    private val externalLinterQueue = MergingUpdateQueue(
        "BufAnalyzeQueue",
        TIME_SPAN,
        true,
        MergingUpdateQueue.ANY_COMPONENT,
        project,
        null,
        false
    )

    override fun createMainHighlightingPass(
        file: PsiFile,
        document: Document,
        highlightInfoProcessor: HighlightInfoProcessor
    ): TextEditorHighlightingPass {
        return BufAnalyzePass(this, file, document, false)
    }

    override fun createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass? {
        FileStatusMap.getDirtyTextRange(editor, passId) ?: return null
        return BufAnalyzePass(this, file, editor.document)
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
    annotationResult: BufAnalyzeResult
) {
    val doc = file.viewProvider.document
        ?: error("Can't find document for $file")

    val wd = annotationResult.workingDirectory
    val filteredIssues = annotationResult.issue
        .filter { it.path == file.virtualFile.toNioPath().relativeToOrNull(wd)?.toString() }
    for (issue in filteredIssues) {
        val severity = if (issue.isCompileError) HighlightSeverity.ERROR else HighlightSeverity.WARNING
        val annotationBuilder = newAnnotation(severity, issue.message)
            .range(issue.toTextRange(doc) ?: continue)
            .problemGroup { issue.type }
            .needsUpdateOnTyping(true)
            .withFix(IgnoreBufIssueQuickFix(issue.type))

        annotationBuilder.create()
    }
}

fun BufIssue.toTextRange(document: Document): TextRange? {
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

