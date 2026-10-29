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

package build.buf.intellij.config

import build.buf.intellij.base.BufTestBase
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import java.io.File

class BufWorkspaceTest : BufTestBase() {
    fun testNestedV2OverridesOuterConfiguration() {
        myFixture.addFileToProject("buf.yaml", "version: v2")
        val config = myFixture.addFileToProject("service/buf.yaml", "version: v2")
        val proto = myFixture.addFileToProject("service/proto/test.proto", "syntax = \"proto3\";")
        assertEquals(config.virtualFile.parent, findBufWorkingDirectory(proto))
    }

    fun testV2OverridesAncestorLegacyWorkspace() {
        myFixture.addFileToProject(
            "buf.work.yaml",
            """
            version: v1
            directories:
              - api
            """.trimIndent(),
        )
        val config = myFixture.addFileToProject("service/buf.yaml", "version: v2")
        val proto = myFixture.addFileToProject("service/proto/test.proto", "syntax = \"proto3\";")
        assertEquals(config.virtualFile.parent, findBufWorkingDirectory(proto))
    }

    fun testStandaloneLegacyModule() {
        val config = myFixture.addFileToProject("service/buf.yaml", "version: v1")
        val proto = myFixture.addFileToProject("service/test.proto", "syntax = \"proto3\";")
        assertEquals(config.virtualFile.parent, findBufWorkingDirectory(proto))
    }

    fun testLegacyWorkspaceContainingModule() {
        val config = myFixture.addFileToProject(
            "buf.work.yaml",
            """
            version: v1
            directories:
              - api
            """.trimIndent(),
        )
        myFixture.addFileToProject("api/buf.yaml", "version: v1")
        val proto = myFixture.addFileToProject("api/test.proto", "syntax = \"proto3\";")
        assertEquals(config.virtualFile.parent, findBufWorkingDirectory(proto))
    }

    // buf.work.yaml does not list other/, so it is skipped and the nearest v1
    // module is used instead.
    fun testLegacyWorkspaceNotContainingModule() {
        myFixture.addFileToProject(
            "buf.work.yaml",
            """
            version: v1
            directories:
              - api
            """.trimIndent(),
        )
        val config = myFixture.addFileToProject("other/buf.yaml", "version: v1")
        val proto = myFixture.addFileToProject("other/test.proto", "syntax = \"proto3\";")
        assertEquals(config.virtualFile.parent, findBufWorkingDirectory(proto))
    }

    // The file is under service/buf.yaml's directory but not in its modules, so
    // that workspace is skipped in favor of the outer one that lists
    // service/other.
    fun testV2WorkspaceModulesExcludeFile() {
        val outer = myFixture.addFileToProject(
            "buf.yaml",
            """
            version: v2
            modules:
              - path: service/other
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "service/buf.yaml",
            """
            version: v2
            modules:
              - path: proto
            """.trimIndent(),
        )
        val proto = myFixture.addFileToProject("service/other/test.proto", "syntax = \"proto3\";")
        assertEquals(outer.virtualFile.parent, findBufWorkingDirectory(proto))
    }

    fun testV2WorkspaceModulesExcludeFileWithoutFallback() {
        myFixture.addFileToProject(
            "service/buf.yaml",
            """
            version: v2
            modules:
              - path: proto
            """.trimIndent(),
        )
        val proto = myFixture.addFileToProject("service/other/test.proto", "syntax = \"proto3\";")
        assertNull(findBufWorkspaceRoot(project, proto.virtualFile))
    }

    fun testModulePathsOutsideConfigDirectoryAreIgnored() {
        myFixture.addFileToProject(
            "service/proto/buf.yaml",
            """
            version: v2
            modules:
              - path: ..
              - path: v1/../..
              - path: /
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "service/proto/buf.work.yaml",
            """
            version: v1
            directories:
              - ../proto
            """.trimIndent(),
        )
        val proto = myFixture.addFileToProject("service/proto/test.proto", "syntax = \"proto3\";")
        assertNull(findBufWorkspaceRoot(project, proto.virtualFile))
    }

    fun testV2ModuleWithoutPath() {
        val config = myFixture.addFileToProject(
            "service/buf.yaml",
            """
            version: v2
            modules:
              - name: buf.build/acme/service
            """.trimIndent(),
        )
        val proto = myFixture.addFileToProject("service/proto/test.proto", "syntax = \"proto3\";")
        assertEquals(config.virtualFile.parent, findBufWorkspaceRoot(project, proto.virtualFile))
    }

    fun testNormalizedModulePath() {
        val config = myFixture.addFileToProject(
            "buf.yaml",
            """
            version: v2
            modules:
              - path: ./service/../proto/
            """.trimIndent(),
        )
        val proto = myFixture.addFileToProject("proto/test.proto", "syntax = \"proto3\";")
        assertEquals(config.virtualFile.parent, findBufWorkspaceRoot(project, proto.virtualFile))
    }

    // The CLI rejects a buf.yaml without a version, so it is ignored and the
    // outer v2 workspace containing the file is used.
    fun testMissingVersionIsIgnored() {
        val outer = myFixture.addFileToProject("buf.yaml", "version: v2")
        myFixture.addFileToProject(
            "service/buf.yaml",
            """
            lint:
              use:
                - STANDARD
            """.trimIndent(),
        )
        val proto = myFixture.addFileToProject("service/test.proto", "syntax = \"proto3\";")
        assertEquals(outer.virtualFile.parent, findBufWorkingDirectory(proto))
    }

    // buf must never run for files outside the project, even when a buf.yaml
    // above them controls them.
    fun testFileOutsideProjectContent() {
        val directory = FileUtil.createTempDirectory("buf-external", null, true)
        File(directory, "buf.yaml").writeText("version: v2")
        val ioFile = File(directory, "test.proto").apply { writeText("syntax = \"proto3\";") }
        val virtualFile = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile))
        val proto = checkNotNull(PsiManager.getInstance(project).findFile(virtualFile))
        assertEquals(virtualFile.parent, findBufWorkspaceRoot(project, virtualFile))
        assertNull(findBufWorkingDirectory(proto))
    }

    fun testUnconfiguredFileUsesContentRoot() {
        val proto = myFixture.addFileToProject("service/test.proto", "syntax = \"proto3\";")
        assertEquals(
            ProjectFileIndex.getInstance(project).getContentRootForFile(proto.virtualFile),
            findBufWorkingDirectory(proto),
        )
    }
}
