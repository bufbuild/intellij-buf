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

import java.util.UUID

/**
 * Converts the UUID to a dashless format (as used by commit IDs in `buf.lock` files).
 */
fun UUID.toDashless(): String = this.toString().replace("-", "")

object UUIDUtils {
    /**
     * Converts a dashless UUID (see [toDashless]) to a [UUID].
     */
    fun fromDashless(value: String): Result<UUID> = runCatching {
        require(value.length == 32) { "Value must be 32 characters long." }
        val withDashes = "${value.substring(0, 8)}-${value.substring(8, 12)}-${value.substring(12, 16)}-${value.substring(16, 20)}-${value.substring(20)}"
        UUID.fromString(withDashes)
    }
}
