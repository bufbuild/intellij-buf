package com.github.bufbuild.intellij.settings

import com.github.bufbuild.intellij.configurable.BufConfigurable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction

object ShowBufSettingsAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        ShowSettingsUtil.getInstance().showSettingsDialog(e.project, BufConfigurable::class.java)
    }
}
