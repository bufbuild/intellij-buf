package com.github.bufbuild.intellij.configurable

import ai.grazie.nlp.utils.tokenizeByWhitespace
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
            checkBox(BufBundle.message("settings.buf.use.formatter"), state::useBufFormatter)
        }
        row {
            checkBox(BufBundle.message("settings.buf.background.linting.enabled"), state::backgroundLintingEnabled)
        }
        row {
            checkBox(BufBundle.message("settings.buf.background.breaking.enabled"), state::backgroundBreakingEnabled)
            subRowIndent = 1
            row("buf breaking") {
                textField(
                    { state.breakingArgumentsOverride.joinToString(separator = " ") },
                    { text -> state.breakingArgumentsOverride = text.tokenizeByWhitespace() }
                ).comment("For example, --against .git#tag=v1.0.0. By default, breaking changes will be verified against uncommitted changes.")
                    .apply {
                        visible(state.backgroundBreakingEnabled)
                        enabled(state.backgroundBreakingEnabled)
                    }
            }
        }
    }

    override fun reset() {
        state.useBufFormatter = project.bufSettings.state.useBufFormatter
        state.backgroundLintingEnabled = project.bufSettings.state.backgroundLintingEnabled
        state.backgroundBreakingEnabled = project.bufSettings.state.backgroundBreakingEnabled
        super.reset()
    }

    override fun apply() {
        super.apply()
        project.bufSettings.state = state
    }

    override fun isModified(): Boolean {
        if (super.isModified()) return true
        return project.bufSettings.state != state
    }
}
