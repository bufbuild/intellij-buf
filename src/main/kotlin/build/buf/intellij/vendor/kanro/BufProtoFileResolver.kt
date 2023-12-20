package build.buf.intellij.vendor.kanro

import build.buf.intellij.config.BufConfig
import build.buf.intellij.index.BufModuleIndex
import build.buf.intellij.resolve.BufRootsProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.psi.search.ProjectScope
import io.kanro.idea.plugin.protobuf.lang.root.ProtobufRoot
import io.kanro.idea.plugin.protobuf.lang.root.ProtobufRootProvider

class BufProtoRootProvider : ProtobufRootProvider {
    override fun id(): String = "bufRoot2"

    override fun roots(context: PsiElement): List<ProtobufRoot> {
        val project = context.project
        val roots = mutableListOf<ProtobufRoot>()
        for (bufConfig in FilenameIndex.getVirtualFilesByName(
            BufConfig.BUF_YAML, ProjectScope.getProjectScope(project)
        )) {
            val parent = bufConfig.parent ?: continue
            roots.add(ProtobufRoot("bufCurrentModule", parent))
        }
        for (mod in BufModuleIndex.getAllProjectModules(project)) {
            val modName = "${mod.remote}/${mod.owner}/${mod.repository}:${mod.commit}"
            val modFolderV2Path = BufRootsProvider.getOrCreateModuleCacheFolderV2(mod)
            if (modFolderV2Path != null) {
                roots.add(ProtobufRoot(modName, modFolderV2Path))
                continue
            }
            val modFolder = BufRootsProvider.findModuleCacheFolderV1(mod) ?: continue
            roots.add(ProtobufRoot(modName, modFolder))
        }
        return roots
    }

    override fun searchScope(context: PsiElement): GlobalSearchScope? {
        val project = context.project
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
