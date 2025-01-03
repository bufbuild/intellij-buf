// Copyright 2022-2025 Buf Technologies, Inc.
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

import org.apache.commons.codec.binary.Hex
import org.assertj.core.api.Assertions
import org.junit.Test
import java.util.concurrent.ThreadLocalRandom

class FileNodeTest {
    @Test
    fun testParse() {
        val randomDigestBytes = ByteArray(CASDigestType.SHAKE256.length)
        ThreadLocalRandom.current().nextBytes(randomDigestBytes)
        val digestStr = "shake256:${Hex.encodeHexString(randomDigestBytes)}"
        val fileNodeStr = "$digestStr  path/to/file.proto"
        val fileNode = FileNode.parse(fileNodeStr).getOrThrow()
        Assertions.assertThat(fileNode.path).isEqualTo("path/to/file.proto")
        Assertions.assertThat(fileNode.digest.toString()).isEqualTo(digestStr)
        Assertions.assertThat(fileNode.toString()).isEqualTo(fileNodeStr)
        val invalidNodeStrs = listOf(
            "$digestStr  ",
            "$digestStr  /file.proto",
            "$digestStr  ../file.proto",
            "$digestStr  file.proto/../abc",
            "$digestStr file.proto",
            "shake256:abc  file.proto",
        )
        for (invalid in invalidNodeStrs) {
            Assertions.assertThatThrownBy { FileNode.parse(invalid).getOrThrow() }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }
}
