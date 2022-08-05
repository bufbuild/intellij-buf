package com.github.bufbuild.intellij.completion

import com.github.bufbuild.intellij.base.BufTestBase

class BufCompletionTest : BufTestBase() {
    override fun getBasePath(): String = "completion"

    fun testExternalBufModule() {
        configureByFolder("external", "order.proto")
        val suggestions = myFixture.completeBasic().map { it.lookupString }
        assertTrue(suggestions.contains("google/type/money.proto"))
    }
}
