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

import java.net.URI

/**
 * Represents a full name of a module stored in the BSR.
 * This type is equivalent to the `bufmodule.ModuleFullName` type in the Buf CLI codebase.
 */
data class ModuleFullName(val registry: String, val owner: String, val name: String) {
    init {
        require(registry.isNotBlank() && !registry.contains('/')) { "invalid registry $registry" }
        // Best effort to parse the registry as a hostname/ip with an optional port.
        val uri = URI.create("https://$registry")
        require(uri.userInfo.isNullOrEmpty() && uri.path.isNullOrEmpty() && uri.query.isNullOrEmpty() && uri.fragment.isNullOrEmpty()) {
            "invalid registry $registry"
        }
        require(owner.isNotBlank() && !owner.contains('/')) { "invalid owner $owner" }
        require(name.isNotBlank() && !name.contains('/')) { "invalid name $name" }
    }

    override fun toString(): String = "$registry/$owner/$name"

    companion object {
        fun parse(fullName: String): Result<ModuleFullName> = runCatching {
            val components = fullName.split('/')
            require(components.size == 3) { "invalid module full name $fullName" }
            ModuleFullName(components[0], components[1], components[2])
        }
    }
}
