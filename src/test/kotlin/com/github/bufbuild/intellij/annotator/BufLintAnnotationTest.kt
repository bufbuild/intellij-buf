package com.github.bufbuild.intellij.annotator

import com.github.bufbuild.intellij.base.BufTestBase

class BufLintAnnotationTest : BufTestBase() {
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
