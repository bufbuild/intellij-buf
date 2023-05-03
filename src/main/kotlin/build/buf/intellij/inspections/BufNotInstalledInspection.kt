package build.buf.intellij.inspections

import build.buf.intellij.BufBundle
import build.buf.intellij.annotator.BufAnalyzeUtils
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.protobuf.lang.psi.PbFile
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.NotNull

class BufNotInstalledInspection : LocalInspectionTool() {
    override fun getGroupDisplayName(): String = BufBundle.message("buf.inspections.group.name")

    override fun getDisplayName(): String = BufBundle.message("buf.inspection.cli.not.installed")

    override fun isEnabledByDefault(): Boolean = true

    @NotNull
    override fun getShortName(): String {
        return "BufCLINotInstalled"
    }

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (file !is PbFile) return null
        if (BufAnalyzeUtils.findBufExecutable() != null) return null
        return arrayOf(
            manager.createProblemDescriptor(
                file,
                displayName,
                isOnTheFly,
                arrayOf(NavigateToDocumentationLinkQuickFix("https://buf.build/")),
                ProblemHighlightType.WARNING
            )
        )
    }
}
