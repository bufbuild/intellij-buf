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

package build.buf.intellij.cas

import org.apache.commons.codec.binary.Hex
import org.assertj.core.api.Assertions
import org.junit.Test
import java.io.StringReader
import java.util.concurrent.ThreadLocalRandom

class CASDigestTest {
    @Test
    fun testDigestParse() {
        val randomDigestBytes = ByteArray(CASDigestType.SHAKE256.length)
        ThreadLocalRandom.current().nextBytes(randomDigestBytes)
        val hex = Hex.encodeHexString(randomDigestBytes)
        val digestStr = "shake256:$hex"
        val digest = CASDigest.parse(digestStr).getOrThrow()
        Assertions.assertThat(digest.toString()).isEqualTo(digestStr)
        Assertions.assertThat(digest.digestType).isEqualTo(CASDigestType.SHAKE256)
        Assertions.assertThat(digest.hex).isEqualTo(hex)
        Assertions.assertThat(digest.value()).isEqualTo(randomDigestBytes)
        for (expectedFailure in listOf("othertype:$hex", "shake256:${hex.substring(0, 64)}", "$hex")) {
            Assertions.assertThatThrownBy { CASDigest.parse(expectedFailure).getOrThrow() }.isInstanceOf(IllegalArgumentException::class.java)
            Assertions.assertThatThrownBy { CASDigest.parse(StringReader(expectedFailure)).getOrThrow() }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }
}
