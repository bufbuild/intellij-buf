// Copyright 2022-2025 Buf Technologies, Inc.
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
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project

class NavigateToDocumentationLinkQuickFix(private val link: String) : LocalQuickFix {
    override fun getFamilyName(): String = BufBundle.getMessage("buf.quickfix.family")

    override fun getName(): String = BufBundle.getMessage("buf.quickfix.navigate.to.documentation")

    override fun availableInBatchMode(): Boolean = false

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        BrowserUtil.browse(link)
    }
}
