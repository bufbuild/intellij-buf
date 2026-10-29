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

package build.buf.intellij.annotator

import build.buf.intellij.base.BufTestBase

class BufAnalyzeAnnotationTest : BufTestBase() {
    fun testSnakeCase() {
        myFixture.configureByText(
            "snake_case.proto",
            """
            syntax = "proto3";

            message Foo {
              string <warning descr="Field name \"Bar\" should be lower_snake_case, such as \"bar\".">Bar</warning> = 1;
            }
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testNestedV2Workspace() {
        myFixture.addFileToProject(
            "service/buf.yaml",
            """
            version: v2
            modules:
              - path: proto
            lint:
              use:
                - FIELD_LOWER_SNAKE_CASE
            """.trimIndent(),
        )
        // Fails the build if buf runs from the content root instead of service/.
        myFixture.addFileToProject("unrelated/broken.proto", "not a proto file")
        myFixture.addFileToProject(
            "service/proto/common.proto",
            """
            syntax = "proto3";
            message Common {}
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "service/proto/test.proto",
            """
            syntax = "proto3";
            import "common.proto";
            message Test {
              Common value = 1;
              string <warning descr="Field name \"BadName\" should be lower_snake_case, such as \"bad_name\".">BadName</warning> = 2;
            }
            """.trimIndent(),
        )
        myFixture.configureByFile("service/proto/test.proto")
        myFixture.checkHighlighting()
    }

    fun testNestedLegacyWorkspace() {
        myFixture.addFileToProject(
            "service/buf.work.yaml",
            """
            version: v1
            directories:
              - api
              - common
            """.trimIndent(),
        )
        for (module in listOf("api", "common")) {
            myFixture.addFileToProject(
                "service/$module/buf.yaml",
                """
                version: v1
                lint:
                  use:
                    - FIELD_LOWER_SNAKE_CASE
                """.trimIndent(),
            )
        }
        myFixture.addFileToProject(
            "service/common/common.proto",
            """
            syntax = "proto3";
            message Common {}
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "service/api/test.proto",
            """
            syntax = "proto3";
            import "common.proto";
            message Test {
              Common value = 1;
              string <warning descr="Field name \"BadName\" should be lower_snake_case, such as \"bad_name\".">BadName</warning> = 2;
            }
            """.trimIndent(),
        )
        myFixture.configureByFile("service/api/test.proto")
        myFixture.checkHighlighting()
    }

    // other/ is a v1 module that buf.work.yaml does not list, so buf must run
    // from other/. Running from the root only lints api/ and reports nothing.
    fun testModuleOutsideLegacyWorkspace() {
        myFixture.addFileToProject(
            "buf.work.yaml",
            """
            version: v1
            directories:
              - api
            """.trimIndent(),
        )
        for (module in listOf("api", "other")) {
            myFixture.addFileToProject(
                "$module/buf.yaml",
                """
                version: v1
                lint:
                  use:
                    - FIELD_LOWER_SNAKE_CASE
                """.trimIndent(),
            )
        }
        myFixture.addFileToProject("api/api.proto", "syntax = \"proto3\";")
        myFixture.addFileToProject(
            "other/test.proto",
            """
            syntax = "proto3";
            message Test {
              string <warning descr="Field name \"BadName\" should be lower_snake_case, such as \"bad_name\".">BadName</warning> = 1;
            }
            """.trimIndent(),
        )
        myFixture.configureByFile("other/test.proto")
        myFixture.checkHighlighting()
    }
}
