package com.github.bufbuild.intellij.icons

import com.github.bufbuild.intellij.BufBundle
import com.github.bufbuild.intellij.settings.ShowBufSettingsAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.markup.GutterIconRenderer
import javax.swing.Icon

object BufAnalyzeGutterIconRenderer : GutterIconRenderer() {
    override fun getIcon(): Icon = BufIcons.Logo

    override fun getTooltipText(): String = BufBundle.getMessage("linter.icon.tooltip")

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = BufIcons.Logo.hashCode()

    override fun getClickAction(): AnAction = ShowBufSettingsAction
}
