package com.github.bufbuild.intellij.status

import com.github.bufbuild.intellij.BufBundle
import com.github.bufbuild.intellij.icons.BufIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.TextPanel
import com.intellij.util.ui.UIUtil
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
            toolTipText = if (inProgress) BufBundle.message("linter.in.progress") else BufBundle.message("linter.done")
            icon = when {
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
