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

package build.buf.intellij.index

import build.buf.intellij.base.BufTestBase

class BufModuleConfigIndexTest : BufTestBase() {
    fun testV2ModuleWithoutPath() {
        val config = myFixture.addFileToProject(
            "buf.yaml",
            """
            version: v2
            modules:
              - name: buf.build/acme/service
            """.trimIndent(),
        )
        val pathUrls = BufIndexes.getProjectBufModuleConfigs(project)
            .filter { it.bufYamlUrl == config.virtualFile.url }
            .map { it.pathUrl }
        assertEquals(listOf(config.virtualFile.parent.url), pathUrls)
    }
}
