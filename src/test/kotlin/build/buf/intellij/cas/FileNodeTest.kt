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

import org.apache.commons.codec.binary.Hex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.ThreadLocalRandom

class FileNodeTest {
    @Test
    fun testParse() {
        val randomDigestBytes = ByteArray(CASDigestType.SHAKE_256.length)
        ThreadLocalRandom.current().nextBytes(randomDigestBytes)
        val digestStr = "shake256:${Hex.encodeHexString(randomDigestBytes)}"
        val fileNodeStr = "$digestStr  path/to/file.proto"
        val fileNode = FileNode.parse(fileNodeStr).getOrThrow()
        assertEquals("path/to/file.proto", fileNode.path)
        assertEquals(digestStr, fileNode.digest.toString())
        assertEquals(fileNodeStr, fileNode.toString())
        val invalidNodeStrs = listOf(
            "$digestStr  ",
            "$digestStr  /file.proto",
            "$digestStr  ../file.proto",
            "$digestStr  file.proto/../abc",
            "$digestStr file.proto",
            "shake256:abc  file.proto",
        )
        for (invalid in invalidNodeStrs) {
            assertThrows<IllegalArgumentException>("invalid node: $invalid") { FileNode.parse(invalid).getOrThrow() }
        }
    }
}
