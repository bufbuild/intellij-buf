// Copyright 2022-2026 Buf Technologies, Inc.
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

package build.buf.intellij.highlighting

import build.buf.intellij.base.BufTestBase
import build.buf.intellij.settings.bufSettings

class BufAnalyzeBreakingTest : BufTestBase() {
    override fun getBasePath(): String = "highlighting"
    fun testBreakingType() {
        project.bufSettings.state = project.bufSettings.state.copy(
            backgroundLintingEnabled = false,
            backgroundBreakingEnabled = true,
            breakingArgumentsOverride = listOf(
                "--against",
                findTestDataFolder().resolve("breaking/type/before").toString(),
            ),
        )
        configureByFolder("breaking/type/after", "foo.proto")
        myFixture.checkHighlighting()
    }
    fun testNestedWorkspaceBreakingType() {
        project.bufSettings.state = project.bufSettings.state.copy(
            backgroundLintingEnabled = false,
            backgroundBreakingEnabled = true,
            breakingArgumentsOverride = listOf(
                "--against",
                findTestDataFolder().resolve("breaking/type/before").toString(),
            ),
        )
        myFixture.addFileToProject(
            "service/buf.yaml",
            """
            version: v2
            modules:
              - path: proto
            """.trimIndent(),
        )
        myFixture.addFileToProject("service/proto/common.proto", "syntax = \"proto3\"; message Common {}")
        myFixture.addFileToProject(
            "service/proto/foo.proto",
            """
            syntax = "proto3";
            import "common.proto";
            message Foo {
              <warning descr="Field \"1\" with name \"bar\" on message \"Foo\" changed type from \"int64\" to \"string\".">string</warning> bar = 1;
              Common common = 2;
            }
            """.trimIndent(),
        )
        myFixture.configureByFile("service/proto/foo.proto")
        myFixture.checkHighlighting()
    }
}
