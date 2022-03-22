package com.github.bufbuild.intellij.inspections

import com.github.bufbuild.intellij.BufBundle
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
