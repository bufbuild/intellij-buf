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

package build.buf.intellij.highlighting

import build.buf.intellij.base.BufTestBase
import build.buf.intellij.settings.BufProjectSettingsService
import build.buf.intellij.settings.bufSettings

class BufAnalyzeBreakingTest : BufTestBase() {
    override fun getBasePath(): String = "highlighting"
    fun testBreakingType() {
        project.bufSettings.state = project.bufSettings.state.copy(
            backgroundLintingEnabled = false,
            backgroundBreakingEnabled = true,
            breakingArgumentsOverride = listOf(
                "--against", findTestDataFolder().resolve("breaking/type/before").toString()
            )
        )
        configureByFolder("breaking/type/after", "foo.proto")
        myFixture.checkHighlighting()
    }
}
