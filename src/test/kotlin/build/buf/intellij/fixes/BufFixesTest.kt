package build.buf.intellij.fixes

import build.buf.intellij.base.BufTestBase

class BufFixesTest : BufTestBase() {
    override fun getBasePath(): String = "fixes"

    fun testIgnore() {
        configureByFolder("ignore", "foo.proto")
        val intention = myFixture.findSingleIntention("Ignore")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            syntax = "proto3";

            message Foo {
              // buf:lint:ignore FIELD_LOWER_SNAKE_CASE
              string B<caret>ar = 1;
            }
            
        """.trimIndent()
        )
    }
}
