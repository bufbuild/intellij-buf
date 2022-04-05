package com.github.bufbuild.intellij.highlighting

import com.github.bufbuild.intellij.base.BufTestBase
import com.github.bufbuild.intellij.settings.BufProjectSettingsService
import com.github.bufbuild.intellij.settings.bufSettings

class BufAnalyzeBreakingTest : BufTestBase() {
    override fun getBasePath(): String = "highlighting"
    fun testBreakingType() {
        project.bufSettings.state = BufProjectSettingsService.State(
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
