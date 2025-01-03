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

package build.buf.intellij.status

import build.buf.intellij.BufBundle
import build.buf.intellij.configurable.BufConfigurable
import build.buf.intellij.icons.BufIcons
import build.buf.intellij.settings.bufSettings
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.TextPanel
import com.intellij.ui.ClickListener
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.event.MouseEvent
import javax.swing.JComponent

class BufCLIWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = BufCLIWidget.ID
    override fun getDisplayName(): String = "Buf Linter"
    override fun isAvailable(project: Project): Boolean = true
    override fun createWidget(project: Project): StatusBarWidget = BufCLIWidget(project)
    override fun disposeWidget(widget: StatusBarWidget) = Disposer.dispose(widget)
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

class BufCLIWidget(private val project: Project) :
    TextPanel.WithIconAndArrows(),
    CustomStatusBarWidget {
    private var statusBar: StatusBar? = null

    var inProgress: Boolean = false
        set(value) {
            field = value
            update()
        }

    init {
        setTextAlignment(CENTER_ALIGNMENT)
        border = JBUI.CurrentTheme.StatusBar.Widget.border()
    }

    override fun ID(): String = ID

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        if (!project.isDisposed) {
            object : ClickListener() {
                override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
                    if (!project.isDisposed) {
                        ShowSettingsUtil.getInstance().showSettingsDialog(project, BufConfigurable::class.java)
                    }
                    return true
                }
            }.installOn(this, true)
        }
        update()
        statusBar.updateWidget(ID())
    }

    override fun dispose() {
        statusBar = null
        UIUtil.dispose(this)
    }

    override fun getComponent(): JComponent = this

    private fun update() {
        if (project.isDisposed) return
        UIUtil.invokeLaterIfNeeded {
            if (project.isDisposed) return@invokeLaterIfNeeded
            text = BufBundle.message("name")
            val analyzingEnabled = project.bufSettings.state.backgroundLintingEnabled ||
                project.bufSettings.state.backgroundBreakingEnabled
            toolTipText = when {
                !analyzingEnabled -> BufBundle.message("analyzing.disabled")
                inProgress -> BufBundle.message("analyzing.in.progress")
                else -> BufBundle.message("analyzing.done")
            }
            icon = when {
                !analyzingEnabled -> BufIcons.LogoGrayscale
                inProgress -> BufIcons.LogoAnimated
                else -> BufIcons.Logo
            }
            repaint()
        }
    }

    companion object {
        const val ID: String = "bufLintWidget"
    }
}
