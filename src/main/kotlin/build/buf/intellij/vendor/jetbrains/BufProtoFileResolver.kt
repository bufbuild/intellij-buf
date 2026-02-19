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

package build.buf.intellij.vendor.jetbrains

import build.buf.intellij.index.BufIndexes
import build.buf.intellij.module.cache.ModuleCacheService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.protobuf.lang.resolve.FileResolveProvider
import com.intellij.protobuf.lang.resolve.FileResolveProvider.ChildEntry
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopesCore

/**
 * Enables resolving .proto files in Buf modules in the [Project] and dependencies from BSR modules.
 */
class BufProtoFileResolver : FileResolveProvider {
    private val moduleCacheService = service<ModuleCacheService>()

    override fun getChildEntries(path: String, project: Project): Collection<ChildEntry> = findFiles(path, project)
        .filter { it.isDirectory }
        .map { VfsUtil.getChildren(it, FileResolveProvider.PROTO_AND_DIRECTORY_FILTER) }
        .flatten()
        .map { ChildEntry(it.name, it.isDirectory) }
        .toSet()

    override fun findFile(path: String, project: Project): VirtualFile? = findFiles(path, project).firstOrNull()

    private fun findFiles(path: String, project: Project): Sequence<VirtualFile> {
        val files = linkedSetOf<VirtualFile>()
        val fs = VirtualFileManager.getInstance()
        for (bufModuleConfig in BufIndexes.getProjectBufModuleConfigs(project)) {
            files.add(fs.findFileByUrl(bufModuleConfig.pathUrl)?.findFileByRelativePath(path) ?: continue)
        }
        for (moduleKey in BufIndexes.getProjectModuleKeys(project)) {
            val moduleDataFile = moduleCacheService.moduleDataPathForModuleKey(moduleKey)
                ?.let { fs.findFileByNioPath(it)?.findFileByRelativePath(path) } ?: continue
            files.add(moduleDataFile)
        }
        return files.asSequence()
    }

    override fun getDescriptorFile(project: Project): VirtualFile? = null

    override fun getSearchScope(project: Project): GlobalSearchScope {
        val roots = ArrayList<VirtualFile>()
        val fs = VirtualFileManager.getInstance()
        for (moduleKey in BufIndexes.getProjectModuleKeys(project)) {
            val moduleDataFile = moduleCacheService.moduleDataPathForModuleKey(moduleKey)
                ?.let { fs.findFileByNioPath(it) } ?: continue
            roots.add(moduleDataFile)
        }
        return if (roots.isNotEmpty()) {
            GlobalSearchScopesCore.directoriesScope(project, true, *roots.toTypedArray())
        } else {
            GlobalSearchScope.allScope(project)
        }
    }
}
