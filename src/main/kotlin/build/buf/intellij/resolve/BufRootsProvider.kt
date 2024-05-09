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

package build.buf.intellij.resolve

import build.buf.intellij.index.BufIndexes
import build.buf.intellij.module.cache.ModuleCacheService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager

/**
 * Configures monitoring of BSR cached modules and external libraries for each BSR module found in
 * `buf.lock` files in the [Project].
 */
class BufRootsProvider : AdditionalLibraryRootsProvider() {

    private val moduleCacheService = service<ModuleCacheService>()

    override fun getRootsToWatch(project: Project): Collection<VirtualFile> {
        if (DumbService.getInstance(project).isDumb) {
            return emptyList()
        }
        val fs = VirtualFileManager.getInstance()
        return BufIndexes.getProjectModuleKeys(project).mapNotNull {
            val moduleDataPath = moduleCacheService.moduleDataPathForModuleKey(it) ?: return@mapNotNull null
            fs.findFileByNioPath(moduleDataPath)
        }
    }

    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        // We need to wait for indexing to be available to determine which module dependencies are used in the project.
        // Short circuit here if running in dumb mode to avoid exceptions.
        if (DumbService.getInstance(project).isDumb) {
            return emptyList()
        }
        val fs = VirtualFileManager.getInstance()
        return BufIndexes.getProjectModuleKeys(project)
            .sortedBy { it.toString() }
            .mapNotNull {
                val moduleDataPath = moduleCacheService.moduleDataPathForModuleKey(it) ?: return@mapNotNull null
                val moduleDataUrl = fs.findFileByNioPath(moduleDataPath)?.url ?: return@mapNotNull null
                BufCacheLibrary(it, moduleDataUrl)
            }
    }
}
