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

package build.buf.intellij.cas

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.assertj.core.api.Assertions
import java.nio.file.Path

class ManifestTest : BasePlatformTestCase() {
    override fun getBasePath(): String = "manifest/project"

    fun testManifest() {
        val cachePath = Path.of(ClassLoader.getSystemResource("testData").toURI())
            .resolve("cachev2/v2/module/buf.build/googleapis/googleapis")
        Assertions.assertThat(cachePath).isNotNull()
        val repoPath = LocalFileSystem.getInstance().findFileByNioFile(cachePath)
        Assertions.assertThat(repoPath).isNotNull()
        val commitPath = cachePath.resolve("commits/cc916c31859748a68fd229a3c8d7a2e8")
        val manifestDigest = CASDigest.parse(commitPath.toFile().readText().trim()).getOrThrow()
        val manifestPath = cachePath.resolve("blobs/${manifestDigest.hex.substring(0, 2)}/${manifestDigest.hex.substring(2)}")
        val manifest = Manifest.parse(manifestPath.toFile().readText()).getOrThrow()
        Assertions.assertThat(manifest.getFileNodes()).isNotEmpty()
        // Verify round tripping.
        Assertions.assertThat(Manifest.parse(manifest.toString()).getOrThrow()).isEqualTo(manifest)
        val moneyFileNode = manifest.getFileNode("google/type/money.proto")!!
        Assertions.assertThat(moneyFileNode.digest).isNotNull()
        Assertions.assertThat(moneyFileNode.path).isNotNull()
        Assertions.assertThat(manifest.getDigest(moneyFileNode.path)).isEqualTo(moneyFileNode.digest)
        Assertions.assertThat(manifest.getFileNode("non/existing/file.proto")).isNull()
        Assertions.assertThat(manifest.getDigest("non/existing/file.proto")).isNull()
    }
}
