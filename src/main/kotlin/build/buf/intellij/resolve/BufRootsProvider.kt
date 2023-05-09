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

import build.buf.intellij.index.BufModuleIndex
import build.buf.intellij.manifest.Manifest
import build.buf.intellij.model.BufModuleCoordinates
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.SystemProperties
import com.intellij.util.io.isDirectory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class BufRootsProvider : AdditionalLibraryRootsProvider(), DumbAware {
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
                return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(commitPath)
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
            return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(commitPath)
        }
    }

    /**
     * Let's index all Buf modules
     * */
    override fun getRootsToWatch(project: Project): Collection<VirtualFile> {
        return listOfNotNull(
            Paths.get(PathManager.getSystemPath(), "buf").let { LocalFileSystem.getInstance().findFileByNioFile(it) },
            getBufCacheFolderV2(),
            getBufCacheFolderV1(),
        )
    }

    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        if (DumbService.isDumb(project)) {
            return emptyList()
        }
        val sourceRootsByLockFile = LinkedHashMap<String, MutableSet<String>>()
        for (mod in BufModuleIndex.getAllProjectModules(project)) {
            // Create Buf Modules <buf.build/{owner}/{name}>
            // 1. buf.work.yaml directories for dependencies found in buf.yaml
            // 2. Add v2 cache dir with source roots set to buf.lock modules found in v2 cache
            val sourceRoots = sourceRootsByLockFile.getOrPut(mod.lockFileURL) { HashSet() }
            val sourceRootV2 = getOrCreateModuleCacheFolderV2(mod)
            if (sourceRootV2 != null) {
                sourceRootV2.let { sourceRoots.add(sourceRootV2.path) }
                continue
            }
            // 3. Add v1 cache dir with source roots set to buf.lock modules not found in v2 cache
            findModuleCacheFolderV1(mod)?.let { sourceRoots.add(it.path) }
        }
        val libraries = ArrayList<SyntheticLibrary>()
        sourceRootsByLockFile.forEach { (lockFilePath, sourceRoots) ->
            if (sourceRoots.isEmpty()) {
                return@forEach
            }
            val lockFile = LocalFileSystem.getInstance().findFileByPath(VfsUtil.urlToPath(lockFilePath)) ?: return@forEach
            // Ideally use buf.yaml name
            // Otherwise use relative path to lock file
            val projectDir = project.guessProjectDir() ?: return@forEach
            val relativePath = VfsUtil.getRelativePath(lockFile, projectDir) ?: return@forEach
            libraries.add(BufCacheLibrary(relativePath, sourceRoots))
        }
        return libraries
    }
}
