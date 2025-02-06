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

/**
 * A module digest type defines the supported digest types for Buf modules.
 * This is not to be confused with [build.buf.intellij.cas.CASDigestType].
 *
 * This is equivalent to `bufmodule.DigestType` in the Buf CLI codebase.
 */
enum class ModuleDigestType(private val digestType: String) {
    /** The B4 digest type used in v1 Buf modules. */
    B4("shake256"),

    /** The B5 digest type used in v2 Buf modules. */
    B5("b5"),
    ;

    override fun toString(): String = digestType

    companion object {
        /**
         * Parses a [ModuleDigestType] from its [ModuleDigestType.toString] format.
         */
        fun parse(digest: String): Result<ModuleDigestType> = runCatching {
            val digestType = entries.find { it.digestType == digest }
            require(digestType != null) { "unknown digest type: $digest" }
            digestType
        }
    }
}
