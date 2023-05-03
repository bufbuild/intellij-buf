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
