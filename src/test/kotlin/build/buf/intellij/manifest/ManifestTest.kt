// Copyright 2022-2023 Buf Technologies, Inc.
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

package build.buf.intellij.manifest

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.jupiter.api.Assertions
import java.nio.file.Path

class ManifestTest : BasePlatformTestCase() {

    override fun getBasePath(): String {
        return "manifest/project"
    }

    fun testManifest() {
        val cachePath = Path.of(ClassLoader.getSystemResource("testData").toURI())
            .resolve("manifest/cache/v2/module/buf.build/googleapis/googleapis")
            .toFile()
            .absolutePath
        Assertions.assertNotNull(cachePath)
        val repoPath = LocalFileSystem.getInstance().findFileByPath(cachePath)
        Assertions.assertNotNull(repoPath)
        val manifest = Manifest.fromCommit(repoPath!!, "cc916c31859748a68fd229a3c8d7a2e8")
        Assertions.assertNotNull(manifest)
        Assertions.assertFalse(manifest!!.isEmpty())
        Assertions.assertTrue(manifest.getPaths().contains("buf.md"))
        val bufMDDigest = manifest.getDigestFor("buf.md")!!
        Assertions.assertNotNull(bufMDDigest)
        Assertions.assertTrue(manifest.getPathsFor(bufMDDigest.hex).contains("buf.md"))
        // Not always the case, but true in this case (no duplicated files).
        Assertions.assertEquals(manifest.getPaths().size, manifest.getDigests().size)
    }
}
