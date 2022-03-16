package com.github.bufbuild.intellij.icons

import com.github.bufbuild.intellij.BufBundle
import com.intellij.openapi.editor.markup.GutterIconRenderer
import javax.swing.Icon

object BufLintGutterIconRenderer: GutterIconRenderer() {
    override fun getIcon(): Icon = BufIcons.Logo

    override fun getTooltipText(): String = BufBundle.getMessage("linter.icon.tooltip")

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = BufIcons.Logo.hashCode()
}
