package build.buf.intellij.icons

import build.buf.intellij.BufBundle
import build.buf.intellij.settings.ShowBufSettingsAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.markup.GutterIconRenderer
import javax.swing.Icon

object BufAnalyzeGutterIconRenderer : GutterIconRenderer() {
    override fun getIcon(): Icon = BufIcons.Logo

    override fun getTooltipText(): String = BufBundle.getMessage("analyzing.icon.tooltip")

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = BufIcons.Logo.hashCode()

    override fun getClickAction(): AnAction = ShowBufSettingsAction
}
