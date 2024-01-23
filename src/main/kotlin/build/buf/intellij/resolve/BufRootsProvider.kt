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

package build.buf.intellij.resolve

import build.buf.intellij.manifest.Manifest
import build.buf.intellij.model.BufModuleCoordinates
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.SystemProperties
import com.intellij.util.io.delete
import com.intellij.util.io.isDirectory
import java.io.IOException
import java.nio.file.*
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

class BufRootsProvider : AdditionalLibraryRootsProvider() {
    companion object {
        private val LOG: Logger = logger<BufRootsProvider>()

        fun getBufCacheFolderBase(env: Map<String, String> = System.getenv()): Path {
            val bufCacheDir = env["BUF_CACHE_DIR"]
            if (bufCacheDir != null) {
                return Path.of(bufCacheDir)
            }
            if (SystemInfoRt.isWindows) {
                val localAppData = env["LOCALAPPDATA"]
                if (localAppData != null) {
                    return Path.of(localAppData, "buf")
                }
            }
            val xdgCacheHome = env["XDG_CACHE_HOME"]
            if (xdgCacheHome != null) {
                return Path.of(xdgCacheHome, "buf")
            }
            val home = env["HOME"]
            if (home != null) {
                return Path.of(home, ".cache", "buf")
            }
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

        fun getIDEAModCacheV2(): Path {
            return Paths.get(PathManager.getSystemPath(), "buf", "modcache")
        }

        private fun findManifestV2(mod: BufModuleCoordinates, env: Map<String, String> = System.getenv()): Manifest? {
            val repoCacheFolder = getBufCacheFolderV2(env)
                ?.findFileByRelativePath("${mod.remote}/${mod.owner}/${mod.repository}") ?: return null
            return Manifest.fromCommit(repoCacheFolder, mod.commit)
        }

        fun findModuleCacheFolderV1(mod: BufModuleCoordinates, env: Map<String, String> = System.getenv()): VirtualFile? = getBufCacheFolderV1(env)
            ?.findFileByRelativePath("${mod.remote}/${mod.owner}/${mod.repository}/${mod.commit}")

        fun getOrCreateModuleCacheFolderV2(mod: BufModuleCoordinates, env: Map<String, String> = System.getenv()): VirtualFile? {
            val bufPath = getIDEAModCacheV2()
            if (!bufPath.isDirectory() && !bufPath.toFile().mkdirs()) {
                LOG.warn("failed to create directory: $bufPath")
                return null
            }
            val commitPath = bufPath.resolve("${mod.remote}/${mod.owner}/${mod.repository}/${mod.commit}")
            if (commitPath.isDirectory()) {
                return LocalFileSystem.getInstance().findFileByNioFile(commitPath)
            }
            val manifest = findManifestV2(mod, env) ?: return null
            if (!commitPath.parent.isDirectory() && !commitPath.parent.toFile().mkdirs()) {
                LOG.warn("failed to create directory: ${commitPath.parent}")
                return null
            }
            val commitTempPath = Files.createTempDirectory(commitPath.parent, "tmp_" + mod.commit)
            try {
                for (path in manifest.getPaths()) {
                    val canonicalPath = manifest.getCanonicalPath(path) ?: return null
                    val modulePath = commitTempPath.resolve(path)
                    if (!modulePath.parent.isDirectory()) {
                        Files.createDirectories(modulePath.parent)
                    }
                    Files.copy(Path.of(canonicalPath), modulePath)
                }
                try {
                    Files.move(commitTempPath, commitPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (e: DirectoryNotEmptyException) {
                    // This is expected if we raced with another process
                } catch (e: FileSystemException) {
                    // This can happen in a race - DirectoryNotEmpty
                    val message = e.message ?: ""
                    if (!message.contains("Directory not empty")) {
                        LOG.warn("failed to move directory $commitTempPath to $commitPath: $e")
                    }
                }
                return LocalFileSystem.getInstance().findFileByNioFile(commitPath)
            } catch (e: Exception) {
                LOG.warn("failed to copy buf v2 cache module $mod", e)
                return null
            } finally {
                if (commitTempPath.exists()) {
                    try {
                        commitTempPath.delete(recursively = true)
                    } catch (e: IOException) {
                        LOG.warn("failed to delete temporary dir: $commitTempPath", e)
                    }
                }
            }
        }
    }

    /**
     * Let's index all Buf modules
     * */
    override fun getRootsToWatch(project: Project): Collection<VirtualFile> {
        val bufPath = getIDEAModCacheV2()
        if (!bufPath.isDirectory() && !bufPath.toFile().mkdirs()) {
            LOG.warn("failed to create directory path: ${bufPath.absolutePathString()}")
        }
        return listOfNotNull(
            LocalFileSystem.getInstance().findFileByNioFile(bufPath),
            getBufCacheFolderV1(),
        )
    }

    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        val bufPath = getIDEAModCacheV2()
        if (!bufPath.isDirectory() && !bufPath.toFile().mkdirs()) {
            LOG.warn("failed to create directory path: ${bufPath.absolutePathString()}")
        }
        return listOfNotNull(
            BufCacheLibrary("v2 Cache", bufPath.absolutePathString()),
            getBufCacheFolderV1()?.let { BufCacheLibrary("v1 Cache", it.path) },
        )
    }
}
