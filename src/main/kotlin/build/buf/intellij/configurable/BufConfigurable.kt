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

package build.buf.intellij.configurable

import build.buf.intellij.BufBundle
import build.buf.intellij.settings.BufProjectSettingsService
import build.buf.intellij.settings.bufSettings
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import java.nio.file.Paths

class BufConfigurable(
    private val project: Project,
) : BoundConfigurable(BufBundle.message("settings.buf.title")) {
    private val state: BufProjectSettingsService.State = project.bufSettings.state.copy()

    private lateinit var breakingEnabled: Cell<JBCheckBox>

    override fun createPanel(): DialogPanel = panel {
        row(BufBundle.message("settings.buf.cli.path")) {
            textFieldWithBrowseButton(
                browseDialogTitle = BufBundle.message("settings.buf.cli.path"),
                project = project,
            ).columns(COLUMNS_LARGE)
                .align(Align.FILL)
                .bindText(state::bufCLIPath)
                .validationOnApply {
                    validateBufCLI(it)
                }.validationOnInput {
                    validateBufCLI(it)
                }
        }
        row {
            checkBox(BufBundle.message("settings.buf.background.linting.enabled"))
                .bindSelected(state::backgroundLintingEnabled)
        }
        row {
            breakingEnabled = checkBox(BufBundle.message("settings.buf.background.breaking.enabled"))
                .bindSelected(state::backgroundBreakingEnabled)
        }
        indent {
            row(BufBundle.message("settings.buf.background.breaking.arguments.label")) {
                textField().bindText(
                    { state.breakingArgumentsOverride.joinToString(separator = " ") },
                    { text -> state.breakingArgumentsOverride = text.split("\\s+".toRegex()).filter { it.isNotBlank() } },
                ).columns(COLUMNS_LARGE)
                    .align(Align.FILL)
                    .comment(BufBundle.message("settings.buf.background.breaking.arguments.comment"))
            }
        }.enabledIf(breakingEnabled.selected)
    }

    override fun reset() {
        state.bufCLIPath = project.bufSettings.state.bufCLIPath
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

    private fun validateBufCLI(input: TextFieldWithBrowseButton): ValidationInfo? {
        val value = input.text
        if (value.isEmpty()) {
            return null
        }
        val file = Paths.get(value).toFile()
        return when {
            !file.exists() -> ValidationInfo(BufBundle.message("settings.buf.cli.path.error.not_exist"), input)
            !file.isFile -> ValidationInfo(BufBundle.message("settings.buf.cli.path.error.not_file"), input)
            !file.canExecute() -> ValidationInfo(BufBundle.message("settings.buf.cli.path.error.not_executable"), input)
            else -> null
        }
    }
}
