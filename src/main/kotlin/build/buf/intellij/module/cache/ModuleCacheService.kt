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

package build.buf.intellij.module.cache

import build.buf.intellij.module.ModuleKey
import com.intellij.openapi.components.Service
import java.nio.file.Path
import kotlin.io.path.isDirectory

/**
 * Lightweight service for abstracting away the details of underlying cache directories for the plugin code.
 */
@Service(Service.Level.APP)
class ModuleCacheService {

    private val cacheV1 = ModuleCacheV1()
    private val cacheV2 = ModuleCacheV2()
    private val cacheV3 = ModuleCacheV3()

    /**
     * Returns all of the module data roots of each cache version to be watched for changes.
     */
    fun moduleDataRoots(): List<Path> = listOf(cacheV1, cacheV2, cacheV3).map { it.moduleDataRoot() }

    /**
     * Returns the first cache non-empty directory for the [ModuleKey] searching across all cache versions.
     * Returns null if the module isn't in any of the CLI caches.
     */
    fun moduleDataPathForModuleKey(key: ModuleKey): Path? {
        // Favor cache v3/v1 over v2 as they don't require separate copy step.
        for (cache in listOf(cacheV3, cacheV1, cacheV2)) {
            val file = cache.moduleDataPathForModuleKey(key)
            if (file.isDirectory() && file.toFile().list()?.isNotEmpty() == true) {
                return file
            }
        }
        return null
    }
}
