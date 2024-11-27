// Copyright 2022-2024 Buf Technologies, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package build.buf.intellij.formatter

import build.buf.intellij.base.BufTestBase
import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import kotlin.io.path.readText

class BufFormatterTest : BufTestBase() {
    fun testFormatting() {
        val file = myFixture.configureByText(
            "user.proto",
            """
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
            """.trimIndent(),
        )
        WriteCommandAction.runWriteCommandAction(project, ReformatCodeProcessor.getCommandName(), null, {
            CodeStyleManager.getInstance(project).reformat(file)
        })
        myFixture.checkResult(
            """
            syntax = "proto3";

            package users.v1;

            message User {
              // A unique ID for the user.
              string user_id = 1;
              // The user's email.
              string email = 2;
            }
            """.trimIndent() + "\n",
        )
    }

    fun testLargeFile() {
        val file = myFixture.configureByText(
            "largeprotofile.proto",
            findTestDataFolder().resolve("formatter/largeprotofile-before.proto").readText(),
        )
        WriteCommandAction.runWriteCommandAction(project, ReformatCodeProcessor.getCommandName(), null, {
            CodeStyleManager.getInstance(project).reformat(file)
        })
        val expected = findTestDataFolder().resolve("formatter/largeprotofile-after.proto").readText()
        myFixture.checkResult(expected)
    }

    fun testSyntaxError() {
        val file = myFixture.configureByText(
            "user.proto",
            """
            syntax = "proto3";
            package users.v1;
            
            message User {
                string a =
            }
            """.trimIndent(),
        )
        WriteCommandAction.runWriteCommandAction(project, ReformatCodeProcessor.getCommandName(), null, {
            CodeStyleManager.getInstance(project).reformat(file)
        })
        myFixture.checkResult(file.text)
    }
}
