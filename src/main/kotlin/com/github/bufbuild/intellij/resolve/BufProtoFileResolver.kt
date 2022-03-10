package com.github.bufbuild.intellij.resolve

import com.github.bufbuild.intellij.index.BufModuleIndex
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.protobuf.lang.resolve.FileResolveProvider
import com.intellij.protobuf.lang.resolve.FileResolveProvider.ChildEntry
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopesCore

class BufProtoFileResolver : FileResolveProvider {
    override fun getChildEntries(path: String, project: Project): Collection<ChildEntry> {
        val file = findFile(path, project) ?: return emptyList()
        if (!file.isDirectory) return emptyList()
        return VfsUtil.getChildren(file, FileResolveProvider.PROTO_AND_DIRECTORY_FILTER)
            .map { ChildEntry(it.name, it.isDirectory) }
    }

    override fun findFile(path: String, project: Project): VirtualFile? {
        for (mod in BufModuleIndex.getAllProjectModules(project)) {
            val modFolder = BufRootsProvider.findModuleCacheFolder(mod) ?: continue
            return modFolder.findFileByRelativePath(path) ?: continue
        }
        return null
    }

    override fun getDescriptorFile(project: Project): VirtualFile? = null

    override fun getSearchScope(project: Project): GlobalSearchScope {
        val roots = BufModuleIndex.getAllProjectModules(project).mapNotNull {
            BufRootsProvider.findModuleCacheFolder(it)
        }
        return if (roots.isNotEmpty()) {
            GlobalSearchScopesCore.directoriesScope(project, true, *roots.toTypedArray())
        } else {
            GlobalSearchScope.allScope(project)
        }
    }
}
