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

import java.util.Objects
import java.util.UUID

/**
 * A module key represents the data stored for a dependency in the `buf.lock` file.
 * This is equivalent to the `bufmodule.ModuleKey` type in the Buf CLI.
 */
data class ModuleKey(val moduleFullName: ModuleFullName, val commitID: UUID, val digest: ModuleDigest? = null) {

    override fun toString(): String = "$moduleFullName:${commitID.toDashless()}"

    override fun equals(other: Any?): Boolean {
        val otherModuleKey = other as? ModuleKey ?: return false
        return moduleFullName == otherModuleKey.moduleFullName && commitID == otherModuleKey.commitID
    }

    override fun hashCode(): Int = Objects.hash(moduleFullName, commitID)

    companion object {
        fun parse(moduleKey: String, digest: ModuleDigest? = null): Result<ModuleKey> = try {
            val components = moduleKey.split(':')
            require(components.size == 2) { "invalid module key: $moduleKey" }
            ModuleFullName.parse(components[0]).map { fullName ->
                val commitID = UUIDUtils.fromDashless(components[1]).getOrThrow()
                ModuleKey(fullName, commitID, digest = digest)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
