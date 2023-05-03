package build.buf.intellij.resolve

import build.buf.intellij.model.BufModuleCoordinates
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.SystemProperties
import java.nio.file.Path

class BufRootsProvider : AdditionalLibraryRootsProvider() {
    companion object {
        public val bufCacheFolderBase: Path
            get() {
                val env = System.getenv()
                val pathFromEnv = (env["BUF_CACHE_DIR"] ?: env["XDG_CACHE_HOME"])?.let { Path.of(it) }
                val pathInHome = (env["HOME"] ?: SystemProperties.getUserHome()).let { Path.of(it, ".cache") }
                return pathFromEnv ?: pathInHome
            }

        public val bufCacheFolder: VirtualFile?
            get() = LocalFileSystem.getInstance().findFileByNioFile(bufCacheFolderBase)
                ?.findFileByRelativePath("buf/v1/module/data")

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
        // TODO: We don't want to watch everything in ~/.cache or ${XDG_CACHE_HOME}, only the buf module files.
        return listOfNotNull(
            bufCacheFolder ?: LocalFileSystem.getInstance().findFileByNioFile(bufCacheFolderBase)
        )
    }

    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        return listOf(BufCacheLibrary(bufCacheFolder ?: return emptyList()))
    }
}
