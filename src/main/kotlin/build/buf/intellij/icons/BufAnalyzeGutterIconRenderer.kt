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
