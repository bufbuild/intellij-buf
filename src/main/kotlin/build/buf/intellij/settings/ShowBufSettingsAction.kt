package build.buf.intellij.settings

import build.buf.intellij.configurable.BufConfigurable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction

object ShowBufSettingsAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        ShowSettingsUtil.getInstance().showSettingsDialog(e.project, BufConfigurable::class.java)
    }
}
