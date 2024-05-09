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

package build.buf.intellij.vendor.kanro

import build.buf.intellij.config.BufConfig
import build.buf.intellij.index.BufModuleKeyIndex
import build.buf.intellij.module.cache.ModuleCacheService
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.psi.search.ProjectScope
import io.kanro.idea.plugin.protobuf.lang.root.ProtobufRoot
import io.kanro.idea.plugin.protobuf.lang.root.ProtobufRootProvider

class BufProtoRootProvider : ProtobufRootProvider {

    private val moduleCacheService = service<ModuleCacheService>()

    override fun id(): String = "bufRoot2"

    override fun roots(context: PsiElement): List<ProtobufRoot> {
        val project = context.project
        val roots = mutableListOf<ProtobufRoot>()
        for (bufConfig in FilenameIndex.getVirtualFilesByName(
            BufConfig.BUF_YAML,
            ProjectScope.getProjectScope(project),
        )) {
            val parent = bufConfig.parent ?: continue
            roots.add(ProtobufRoot("bufCurrentModule", parent))
        }
        val fs = LocalFileSystem.getInstance()
        for (moduleKey in BufModuleKeyIndex.getAllProjectModuleKeys(project)) {
            val moduleKeyName = "$moduleKey"
            val moduleDataFile = moduleCacheService.moduleDataPathForModuleKey(moduleKey)
                ?.let { fs.findFileByNioFile(it) } ?: continue
            roots.add(ProtobufRoot(moduleKeyName, moduleDataFile))
        }
        return roots
    }

    override fun searchScope(context: PsiElement): GlobalSearchScope {
        val project = context.project
        val roots = ArrayList<VirtualFile>()
        val fs = LocalFileSystem.getInstance()
        for (moduleKey in BufModuleKeyIndex.getAllProjectModuleKeys(project)) {
            val moduleDataFile = moduleCacheService.moduleDataPathForModuleKey(moduleKey)
                ?.let { fs.findFileByNioFile(it) } ?: continue
            roots.add(moduleDataFile)
        }
        return if (roots.isNotEmpty()) {
            GlobalSearchScopesCore.directoriesScope(project, true, *roots.toTypedArray())
        } else {
            GlobalSearchScope.allScope(project)
        }
    }
}
