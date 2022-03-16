package com.github.bufbuild.intellij.status

import com.github.bufbuild.intellij.BufBundle
import com.github.bufbuild.intellij.configurable.BufConfigurable
import com.github.bufbuild.intellij.icons.BufIcons
import com.github.bufbuild.intellij.settings.bufSettings
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.TextPanel
import com.intellij.ui.ClickListener
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

class BufCLIWidget(private val project: Project) : TextPanel.WithIconAndArrows(), CustomStatusBarWidget {
    private var statusBar: StatusBar? = null

    var inProgress: Boolean = false
        set(value) {
            field = value
            update()
        }

    init {
        setTextAlignment(CENTER_ALIGNMENT)
        border = StatusBarWidget.WidgetBorder.WIDE
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
            val lintingEnabled = project.bufSettings.state.backgroundLintingEnabled
            toolTipText = when {
                !lintingEnabled -> BufBundle.message("linter.disabled")
                inProgress -> BufBundle.message("linter.in.progress")
                else -> BufBundle.message("linter.done")
            }
            icon = when {
                !lintingEnabled -> BufIcons.LogoGrayscale
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
