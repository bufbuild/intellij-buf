package build.buf.intellij.resolve

import build.buf.intellij.base.BufTestBase

class BufResolveTest : BufTestBase() {
    override fun getBasePath(): String = "resolve"
    fun testExternalBufModule() {
        configureByFolder("external", "order.proto")
        val reference = myFixture.getReferenceAtCaretPositionWithAssertion("order.proto")
        assertNotNull(reference.resolve())
    }
}
