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
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

class ModuleKeyTest {
    @Test
    fun testModuleKey() {
        val fullName = "buf.build/someorg/somerepo"
        val commitID = UUID.randomUUID()
        val moduleKey = ModuleKey.parse("$fullName:${commitID.toDashless()}").getOrThrow()
        Assertions.assertEquals(fullName, moduleKey.moduleFullName.toString())
        Assertions.assertEquals(commitID, moduleKey.commitID)
        Assertions.assertNull(moduleKey.digest)
        val moduleKeyFromToString = ModuleKey.parse(moduleKey.toString()).getOrThrow()
        Assertions.assertEquals(moduleKey, moduleKeyFromToString)
        Assertions.assertEquals(moduleKey.hashCode(), moduleKeyFromToString.hashCode())

        val randomDigestBytes = ByteArray(CASDigestType.SHAKE256.length)
        ThreadLocalRandom.current().nextBytes(randomDigestBytes)
        val digest = ModuleDigest.parse("b5:${Hex.encodeHexString(randomDigestBytes)}").getOrThrow()
        val moduleKeyWithDigest = moduleKey.copy(digest = digest)
        // Digests are optional in v1/v1beta1 lock files.
        // Ensure that equals()/hashCode() don't include optional digests.
        Assertions.assertEquals(moduleKey, moduleKeyWithDigest)
        Assertions.assertEquals(moduleKey.hashCode(), moduleKeyWithDigest.hashCode())

        for (invalid in listOf("$commitID", "invalidfullname:${commitID.toDashless()}", "$fullName:$commitID")) {
            assertThrows<IllegalArgumentException> { ModuleKey.parse(invalid).getOrThrow() }
        }
    }
}
