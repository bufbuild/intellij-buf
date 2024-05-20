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

package build.buf.intellij.fixes

import build.buf.intellij.base.BufTestBase

class BufFixesTest : BufTestBase() {
    override fun getBasePath(): String = "fixes"

    fun testIgnoreV1() {
        runIgnoreQuickFix(
            "ignore/v1",
            """
            version: v1
            name: buf.build/intellij/testing
            lint:
              use:
                - DEFAULT
              allow_comment_ignores: true
            
            """.trimIndent(),
        )
    }

    fun testIgnoreV1EnabledFalse() {
        runIgnoreQuickFix(
            "ignore/v1_enabled_false",
            """
            version: v1
            name: buf.build/intellij/testing
            lint:
              use:
                - DEFAULT
              allow_comment_ignores: true
            
            """.trimIndent(),
        )
    }

    fun testIgnoreV1EnabledTrue() {
        runIgnoreQuickFix(
            "ignore/v1_enabled_true",
            """
            version: v1
            name: buf.build/intellij/testing
            lint:
              use:
                - DEFAULT
              allow_comment_ignores: true
            
            """.trimIndent(),
        )
    }

    fun testIgnoreV2() {
        runIgnoreQuickFix(
            "ignore/v2",
            """
            version: v2
            name: buf.build/intellij/testing
            lint:
              use:
                - DEFAULT
            
            """.trimIndent(),
        )
    }

    fun testIgnoreV2DisabledFalse() {
        runIgnoreQuickFix(
            "ignore/v2_disabled_false",
            """
            version: v2
            name: buf.build/intellij/testing
            lint:
              use:
                - DEFAULT
              disallow_comment_ignores: false
            
            """.trimIndent(),
        )
    }

    fun testIgnoreV2DisabledTrue() {
        runIgnoreQuickFix(
            "ignore/v2_disabled_true",
            """
            version: v2
            name: buf.build/intellij/testing
            lint:
              use:
                - DEFAULT
            
            """.trimIndent(),
        )
    }

    fun testIgnoreV2ModulesDisabledTrue() {
        runIgnoreQuickFix(
            "ignore/v2_modules_disabled_true",
            """
            version: v2
            modules:
              - path: .
                name: buf.build/intellij/testing
                lint:
                  use:
                    - DEFAULT
            
            """.trimIndent(),
        )
    }

    private fun runIgnoreQuickFix(relPath: String, expectedBufYaml: String) {
        configureByFolder(relPath, "foo.proto")
        val intention = myFixture.findSingleIntention("Ignore")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
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

            syntax = "proto3";

            message Foo {
              // buf:lint:ignore FIELD_LOWER_SNAKE_CASE
              string B<caret>ar = 1;
            }
            
            """.trimIndent(),
        )
        myFixture.checkResult("buf.yaml", expectedBufYaml, true)
    }
}
