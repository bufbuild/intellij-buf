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

import org.apache.commons.codec.DecoderException
import org.apache.commons.codec.binary.Hex
import java.io.Reader

/**
 * Creates a CAS digest consisting of a [CASDigestType] and its hex representation.
 * Equivalent to `bufcas.Digest` in the Buf CLI codebase.
 */
data class CASDigest(
    val digestType: CASDigestType,
    val hex: String,
) {
    private val decoded: ByteArray = try {
        Hex.decodeHex(hex)
    } catch (e: DecoderException) {
        throw IllegalArgumentException("invalid hex digest: $hex")
    }

    init {
        require(hex.length == digestType.length * 2) { "hex digest length ${hex.length} must be ${digestType.length * 2}" }
    }

    fun value(): ByteArray = decoded.clone() // Make a defensive copy

    /**
     * Formats a digest as `digestType:hex`.
     */
    override fun toString(): String = "$digestType:$hex"

    companion object {
        /**
         * Parses a digest (as formatted with [CASDigest.toString]).
         */
        fun parse(digestStr: String): Result<CASDigest> = runCatching {
            val components = digestStr.split(":")
            require(components.size == 2) { "invalid digest string encoding: $digestStr" }
            return CASDigestType.parse(components[0]).map { digestType -> CASDigest(digestType, components[1]) }
        }

        /**
         * Parses a digest (as formatted with [CASDigest.toString]).
         */
        fun parse(reader: Reader): Result<CASDigest> = try {
            parse(reader.readText().trim())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
