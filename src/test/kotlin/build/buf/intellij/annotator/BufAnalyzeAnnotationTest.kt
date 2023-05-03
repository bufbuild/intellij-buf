package build.buf.intellij.annotator

import build.buf.intellij.base.BufTestBase

class BufAnalyzeAnnotationTest : BufTestBase() {
    fun testSnakeCase() {
        myFixture.configureByText(
            "snake_case.proto", """
            syntax = "proto3";

            message Foo {
              string <warning descr="Field name \"Bar\" should be lower_snake_case, such as \"bar\".">Bar</warning> = 1;
            }
        """.trimIndent()
        )
        myFixture.checkHighlighting()
    }
}
