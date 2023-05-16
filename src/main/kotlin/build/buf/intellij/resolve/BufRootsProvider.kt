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

import build.buf.intellij.manifest.Manifest
import build.buf.intellij.model.BufModuleCoordinates
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.SystemProperties
import com.intellij.util.io.isDirectory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

class BufRootsProvider : AdditionalLibraryRootsProvider() {
    companion object {
        private val LOG: Logger = logger<BufRootsProvider>()

        fun getBufCacheFolderBase(env: Map<String, String> = System.getenv()): Path {
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

        fun getBufCacheFolderV1(env: Map<String, String> = System.getenv()) : VirtualFile? {
            return LocalFileSystem.getInstance().findFileByNioFile(getBufCacheFolderBase(env))
                ?.findFileByRelativePath("v1/module/data")
        }

        fun getBufCacheFolderV2(env: Map<String, String> = System.getenv()) : VirtualFile? {
            return LocalFileSystem.getInstance().findFileByNioFile(getBufCacheFolderBase(env))
                ?.findFileByRelativePath("v2/module")
        }

        private fun findManifestV2(mod: BufModuleCoordinates, env: Map<String, String> = System.getenv()): Manifest? {
            val repoCacheFolder = getBufCacheFolderV2(env)
                ?.findFileByRelativePath("${mod.remote}/${mod.owner}/${mod.repository}") ?: return null
            return Manifest.fromCommit(repoCacheFolder, mod.commit)
        }

        fun findModuleCacheFolderV1(mod: BufModuleCoordinates, env: Map<String, String> = System.getenv()): VirtualFile? = getBufCacheFolderV1(env)
            ?.findFileByRelativePath("${mod.remote}/${mod.owner}/${mod.repository}/${mod.commit}")

        fun getOrCreateModuleCacheFolderV2(mod: BufModuleCoordinates, env: Map<String, String> = System.getenv()): VirtualFile? {
            val bufPath = Paths.get(PathManager.getSystemPath(), "buf")
            if (!bufPath.isDirectory() && !bufPath.toFile().mkdirs()) {
                return null
            }
            val commitPath = bufPath.resolve("${mod.remote}/${mod.owner}/${mod.repository}/${mod.commit}")
            if (commitPath.isDirectory()) {
                return LocalFileSystem.getInstance().findFileByNioFile(commitPath)
            }
            val manifest = findManifestV2(mod, env) ?: return null
            for (path in manifest.getPaths()) {
                val canonicalPath = manifest.getCanonicalPath(path) ?: return null
                val modulePath = commitPath.resolve(path)
                if (!modulePath.parent.isDirectory()) {
                    Files.createDirectories(modulePath.parent)
                }
                Files.copy(Path.of(canonicalPath), modulePath)
            }
            return LocalFileSystem.getInstance().findFileByNioFile(commitPath)
        }
    }

    /**
     * Let's index all Buf modules
     * */
    override fun getRootsToWatch(project: Project): Collection<VirtualFile> {
        val bufPath = Paths.get(PathManager.getSystemPath(), "buf")
        if (!bufPath.isDirectory() && !bufPath.toFile().mkdirs()) {
            LOG.warn("failed to create directory path: ${bufPath.absolutePathString()}")
        }
        return listOfNotNull(
            LocalFileSystem.getInstance().findFileByNioFile(bufPath),
            getBufCacheFolderV2(),
            getBufCacheFolderV1(),
        )
    }

    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        return listOfNotNull(
            Paths.get(PathManager.getSystemPath(), "buf")?.let { BufCacheLibrary("v2 Cache", it.absolutePathString()) },
            getBufCacheFolderV1()?.let { BufCacheLibrary("v1 Cache", it.path) },
        )
    }
}
