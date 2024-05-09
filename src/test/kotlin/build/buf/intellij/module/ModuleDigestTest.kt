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

package build.buf.intellij.module

import build.buf.intellij.cas.CASDigestType
import org.apache.commons.codec.binary.Hex
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.ThreadLocalRandom

class ModuleDigestTest {
    @Test
    fun testDigest() {
        val randomDigestBytes = ByteArray(CASDigestType.SHAKE_256.length)
        ThreadLocalRandom.current().nextBytes(randomDigestBytes)
        val hex = Hex.encodeHexString(randomDigestBytes)
        val digestStr = "b5:$hex"
        val digest = ModuleDigest.parse(digestStr).getOrThrow()
        Assertions.assertEquals(digestStr, digest.toString())
        Assertions.assertEquals(ModuleDigestType.B5, digest.digestType)
        Assertions.assertEquals(hex, digest.hex)
        Assertions.assertArrayEquals(randomDigestBytes, digest.value())
        for (expectedFailure in listOf("b4:$hex", hex)) {
            assertThrows<IllegalArgumentException> { ModuleDigest.parse(expectedFailure).getOrThrow() }
        }
    }
}
