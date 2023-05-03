package build.buf.intellij.highlighting

import build.buf.intellij.base.BufTestBase
import build.buf.intellij.settings.BufProjectSettingsService
import build.buf.intellij.settings.bufSettings

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
