package build.buf.intellij.resolve

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

    private fun findFiles(path: String, project: Project): Sequence<VirtualFile> = sequence {
        for (bufConfig in FilenameIndex.getFilesByName(project, "buf.yaml", ProjectScope.getProjectScope(project))) {
            val bufRoot = bufConfig.virtualFile.parent
            yield(bufRoot.findFileByRelativePath(path) ?: continue)
        }
        for (mod in BufModuleIndex.getAllProjectModules(project)) {
            val modFolder = BufRootsProvider.findModuleCacheFolder(mod) ?: continue
            yield(modFolder.findFileByRelativePath(path) ?: continue)
        }
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
