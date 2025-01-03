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

package build.buf.intellij.module

import build.buf.intellij.cas.CASDigest
import build.buf.intellij.cas.CASDigestType

/**
 * A module digest is equivalent to `bufmodule.Digest` in the Buf CLI.
 */
data class ModuleDigest(val digestType: ModuleDigestType, val hex: String) {
    private val casDigest: CASDigest = CASDigest(CASDigestType.SHAKE256, hex)

    /**
     * Returns the underlying digest value (parsed from [hex]).
     */
    fun value(): ByteArray = casDigest.value()

    override fun toString(): String = "$digestType:$hex"

    companion object {
        fun parse(digest: String): Result<ModuleDigest> = try {
            val components = digest.split(':')
            require(components.size == 2) { "invalid module digest $digest - must have two components" }
            ModuleDigestType.parse(components[0]).map {
                ModuleDigest(it, components[1])
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
