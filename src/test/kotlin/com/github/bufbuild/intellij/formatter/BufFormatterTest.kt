package com.github.bufbuild.intellij.formatter

import com.github.bufbuild.intellij.base.BufTestBase
import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager

class BufFormatterTest : BufTestBase() {
    fun testFormatting() {
        val file = myFixture.configureByText(
            "user.proto", """
                            syntax =
                    "proto3";
                
                package
                
                           users.v1;
                
                message User {
                
                // A unique ID for the user.
                 string user_id =
                
                    1;
                              // The user's email.
                string      email = 2; }
        """.trimIndent())
        WriteCommandAction.runWriteCommandAction(project, ReformatCodeProcessor.getCommandName(), null, {
            CodeStyleManager.getInstance(project).reformat(file)
        })
        myFixture.checkResult("""
            syntax = "proto3";

            package users.v1;

            message User {
              // A unique ID for the user.
              string user_id = 1;
              // The user's email.
              string email = 2;
            }
        """.trimIndent())
    }
}
