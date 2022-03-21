package com.github.bufbuild.intellij.configurable

import com.github.bufbuild.intellij.BufBundle
import com.github.bufbuild.intellij.settings.BufProjectSettingsService
import com.github.bufbuild.intellij.settings.bufSettings
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.panel

class BufConfigurable(
    private val project: Project,
) : BoundConfigurable(BufBundle.message("settings.buf.title")) {
    private val state: BufProjectSettingsService.State = project.bufSettings.state.copy()

    override fun createPanel(): DialogPanel = panel {
        row {
            checkBox(BufBundle.message("settings.buf.background.linting.enabled"), state::backgroundLintingEnabled)
        }
    }

    override fun reset() {
        state.backgroundLintingEnabled = project.bufSettings.state.backgroundLintingEnabled
        super.reset()
    }

    override fun apply() {
        super.apply()
        project.bufSettings.state = state
    }

    override fun isModified(): Boolean {
        if (super.isModified()) return true
        return project.bufSettings.state.backgroundLintingEnabled != state.backgroundLintingEnabled
    }
}
