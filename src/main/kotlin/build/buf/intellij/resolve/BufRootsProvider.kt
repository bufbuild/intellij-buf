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
        val bufCacheFolderBase: Path
            get() {
                val env = System.getenv()
                val bufCacheDir = env["BUF_CACHE_DIR"]
                if (bufCacheDir != null) {
                    return Path.of(bufCacheDir)
                }
                val xdgCacheHome = env["XDG_CACHE_HOME"]
                if (xdgCacheHome != null) {
                    return Path.of(xdgCacheHome, "buf")
                }
                val home = env["HOME"]
                if (home != null) {
                    return Path.of(home, ".cache", "buf")
                }
                // TODO: LOCALAPPDATA on Windows
                return Path.of(SystemProperties.getUserHome(), ".cache", "buf")
            }

        val bufCacheFolder: VirtualFile?
            get() = LocalFileSystem.getInstance().findFileByNioFile(bufCacheFolderBase)
                ?.findFileByRelativePath("v1/module/data")

        fun findModuleCacheFolder(mod: BufModuleCoordinates): VirtualFile? = bufCacheFolder
            ?.findChild(mod.remote)
            ?.findChild(mod.owner)
            ?.findChild(mod.repository)
            ?.findChild(mod.commit)
    }

    /**
     * Let's index all Buf modules
     * */
    override fun getRootsToWatch(project: Project): Collection<VirtualFile> {
        return listOfNotNull(
            bufCacheFolder ?: LocalFileSystem.getInstance().findFileByNioFile(bufCacheFolderBase)
        )
    }

    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        return listOf(BufCacheLibrary(bufCacheFolder ?: return emptyList()))
    }
}
