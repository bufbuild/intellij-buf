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

package build.buf.intellij.module

import build.buf.intellij.cas.CASDigestType
import org.apache.commons.codec.binary.Hex
import org.assertj.core.api.Assertions
import org.junit.Test
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

class ModuleKeyTest {
    @Test
    fun testModuleKey() {
        val fullName = "buf.build/someorg/somerepo"
        val commitID = UUID.randomUUID()
        val moduleKey = ModuleKey.parse("$fullName:${commitID.toDashless()}").getOrThrow()
        Assertions.assertThat(moduleKey.moduleFullName.toString()).isEqualTo(fullName)
        Assertions.assertThat(moduleKey.commitID).isEqualTo(commitID)
        Assertions.assertThat(moduleKey.digest).isNull()
        val moduleKeyFromToString = ModuleKey.parse(moduleKey.toString()).getOrThrow()
        Assertions.assertThat(moduleKeyFromToString).isEqualTo(moduleKey)
        Assertions.assertThat(moduleKeyFromToString.hashCode()).isEqualTo(moduleKey.hashCode())

        val randomDigestBytes = ByteArray(CASDigestType.SHAKE256.length)
        ThreadLocalRandom.current().nextBytes(randomDigestBytes)
        val digest = ModuleDigest.parse("b5:${Hex.encodeHexString(randomDigestBytes)}").getOrThrow()
        val moduleKeyWithDigest = moduleKey.copy(digest = digest)
        // Digests are optional in v1/v1beta1 lock files.
        // Ensure that equals()/hashCode() don't include optional digests.
        Assertions.assertThat(moduleKeyWithDigest).isEqualTo(moduleKey)
        Assertions.assertThat(moduleKeyWithDigest.hashCode()).isEqualTo(moduleKey.hashCode())

        for (invalid in listOf("$commitID", "invalidfullname:${commitID.toDashless()}", "$fullName:$commitID")) {
            Assertions.assertThatThrownBy { ModuleKey.parse(invalid).getOrThrow() }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun testKeyWithPort() {
        val moduleKey = ModuleKey.parse("0.0.0.0:49524/user3/repo31:17b027e298f84498a101bb98402a08be").getOrThrow()
        Assertions.assertThat(moduleKey.moduleFullName.toString()).isEqualTo("0.0.0.0:49524/user3/repo31")
        Assertions.assertThat(moduleKey.commitID).isEqualTo(UUIDUtils.fromDashless("17b027e298f84498a101bb98402a08be").getOrThrow())
    }
}
