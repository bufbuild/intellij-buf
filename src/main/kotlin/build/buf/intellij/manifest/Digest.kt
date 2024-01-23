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

package build.buf.intellij.manifest

data class Digest(
    val digestType: String,
    val hex: String,
) {
    override fun toString(): String {
        return "$digestType:$hex"
    }

    companion object {
        private const val DIGEST_TYPE_SHAKE_256 = "shake256"

        fun fromString(line: String): Digest? {
            val components = line.split(":", limit = 2)
            if (components.size != 2 || components[0].isBlank() || components[1].trim().length < 2) {
                return null
            }
            return when (components[0]) {
                DIGEST_TYPE_SHAKE_256 -> Digest(DIGEST_TYPE_SHAKE_256, components[1])
                else -> Digest(components[0], components[1])
            }
        }
    }
}
