// Copyright 2022-2023 Buf Technologies, Inc.
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

import build.buf.intellij.config.BufConfig
import build.buf.intellij.index.BufModuleIndex
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.protobuf.lang.resolve.FileResolveProvider
import com.intellij.protobuf.lang.resolve.FileResolveProvider.ChildEntry
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.psi.search.ProjectScope

class BufProtoFileResolver : FileResolveProvider {
    override fun getChildEntries(path: String, project: Project): Collection<ChildEntry> {
        return findFiles(path, project)
            .filter { it.isDirectory }
            .map { VfsUtil.getChildren(it, FileResolveProvider.PROTO_AND_DIRECTORY_FILTER) }
            .flatten()
            .map { ChildEntry(it.name, it.isDirectory) }
            .toSet()
    }

    override fun findFile(path: String, project: Project): VirtualFile? = findFiles(path, project).firstOrNull()

    private fun findFiles(path: String, project: Project): Sequence<VirtualFile> {
        val files = LinkedHashSet<VirtualFile>()
        for (bufConfig in FilenameIndex.getVirtualFilesByName(BufConfig.BUF_YAML, ProjectScope.getProjectScope(project))) {
            val bufRoot = bufConfig.parent ?: continue
            files.add(bufRoot.findFileByRelativePath(path) ?: continue)
        }
        for (mod in BufModuleIndex.getAllProjectModules(project)) {
            val modFolderV2Path = BufRootsProvider.getOrCreateModuleCacheFolderV2(mod)
                ?.findFileByRelativePath(path)
            if (modFolderV2Path != null) {
                files.add(modFolderV2Path)
                continue
            }
            val modFolder = BufRootsProvider.findModuleCacheFolderV1(mod) ?: continue
            files.add(modFolder.findFileByRelativePath(path) ?: continue)
        }
        return files.asSequence()
    }

    override fun getDescriptorFile(project: Project): VirtualFile? = null

    override fun getSearchScope(project: Project): GlobalSearchScope {
        val roots = ArrayList<VirtualFile>()
        for (mod in BufModuleIndex.getAllProjectModules(project)) {
            val v2Root = BufRootsProvider.getOrCreateModuleCacheFolderV2(mod)
            if (v2Root != null) {
                roots.add(v2Root)
                continue
            }
            BufRootsProvider.findModuleCacheFolderV1(mod)?.let { roots.add(it) }
        }
        return if (roots.isNotEmpty()) {
            GlobalSearchScopesCore.directoriesScope(project, true, *roots.toTypedArray())
        } else {
            GlobalSearchScope.allScope(project)
        }
    }
}
