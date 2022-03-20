package com.github.bufbuild.intellij.resolve

import com.github.bufbuild.intellij.model.BufModuleCoordinates
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

class BufRootsProvider : AdditionalLibraryRootsProvider() {
    companion object {
        private val bufCacheFolderBase: VirtualFile?
            get() {
                return (System.getenv()["BUF_CACHE_DIR"] ?: System.getenv()["XDG_CACHE_HOME"])?.let {
                    VfsUtil.findFile(Path.of(it), true)
                } ?: VfsUtil.getUserHomeDir()?.findFileByRelativePath(".cache")
            }

        public val bufCacheFolder: VirtualFile?
            get() = bufCacheFolderBase?.findFileByRelativePath("buf/v1/module/data")

        public fun findModuleCacheFolder(mod: BufModuleCoordinates): VirtualFile? = bufCacheFolder
            ?.findChild(mod.remote)
            ?.findChild(mod.owner)
            ?.findChild(mod.repository)
            ?.findChild(mod.commit)
    }

    /**
     * Let's index all Buf modules
     * */
    override fun getRootsToWatch(project: Project): Collection<VirtualFile> {
        return listOfNotNull(bufCacheFolder)
    }

    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        return listOf(BufCacheLibrary(bufCacheFolder ?: return emptyList()))
    }
}
