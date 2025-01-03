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

/**
 * Defines supported digest types in the v2 Buf cache [Manifest] file format.
 * Equivalent to `bufcas.DigestType` in the Buf CLI codebase.
 * Only the `shake256` digest type is supported.
 */
enum class CASDigestType(private val digestType: String, val length: Int) {
    SHAKE256("shake256", 64),
    ;

    override fun toString(): String = digestType

    companion object {
        /**
         * Parses a [CASDigestType] from its [CASDigestType.toString] format.
         */
        fun parse(digest: String): Result<CASDigestType> = runCatching {
            val digestType = values().find { it.digestType == digest }
            require(digestType != null) { "unknown digest type: $digest" }
            digestType
        }
    }
}
