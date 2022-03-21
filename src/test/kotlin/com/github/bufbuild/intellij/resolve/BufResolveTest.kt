package com.github.bufbuild.intellij.resolve

import com.github.bufbuild.intellij.base.BufTestBase

class BufResolveTest : BufTestBase() {
    override fun getBasePath(): String = "resolve"
    fun testExternalBufModule() {
        configureByFolder("external", "order.proto")
        val reference = myFixture.getReferenceAtCaretPositionWithAssertion("order.proto")
        assertNotNull(reference.resolve())
    }
}
