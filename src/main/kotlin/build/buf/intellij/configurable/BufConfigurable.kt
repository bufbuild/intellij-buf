// Copyright 2022-2023 Buf Technologies, Inc.
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

package build.buf.intellij.configurable

import ai.grazie.nlp.utils.tokenizeByWhitespace
import build.buf.intellij.BufBundle
import build.buf.intellij.settings.BufProjectSettingsService
import build.buf.intellij.settings.bufSettings
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
