package com.github.bufbuild.intellij.completion

import com.github.bufbuild.intellij.base.BufTestBase
import com.github.bufbuild.intellij.index.BufModuleIndex
import kotlin.test.assertContains

class BufCompletionTest : BufTestBase() {
    override fun getBasePath(): String = "completion"

    fun testExternalBufModule() {
        configureByFolder("external", "order.proto")
        assertNotEmpty(BufModuleIndex.getAllProjectModules(project))
        val suggestions = myFixture.completeBasic().map { it.lookupString }
        assertContains(suggestions, "google/type/money.proto")
    }
}
