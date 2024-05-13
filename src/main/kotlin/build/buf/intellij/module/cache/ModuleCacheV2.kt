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

package build.buf.intellij.module.cache

import build.buf.intellij.cas.CASDigest
import build.buf.intellij.cas.Manifest
import build.buf.intellij.module.ModuleKey
import build.buf.intellij.module.toDashless
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.io.delete
import java.io.IOException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.bufferedReader
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/**
 * Module cache implementation for the v2 Buf CLI cache directory layout.
 * This differs from v1/v3 in that the files are stored as a content-addressable storage (CAS).
 * To work with the IDE, we need to copy the files to a file layout based on the paths within a module.
 */
internal class ModuleCacheV2(env: Map<String, String> = System.getenv()) : BaseModuleCache(env) {

    override fun moduleDataRoot(): Path = Path.of(SYSTEM_BUF_MOD_CACHE)

    override fun moduleDataPathForModuleKey(key: ModuleKey): Path {
        val fullNamePath = "${key.moduleFullName}"
        val bufModuleCachePath = Path.of("$bufCacheDir/v2/module/$fullNamePath")
        val modcachePath = moduleDataRoot().resolve("$fullNamePath/${key.commitID.toDashless()}")
        try {
            copyFromV2ModuleCacheToSystemDir(key, bufModuleCachePath, modcachePath)
        } catch (e: Exception) {
            LOG.warn("failed to copy buf v2 cache module $key: $e")
        }
        return modcachePath
    }

    private fun copyFromV2ModuleCacheToSystemDir(
        key: ModuleKey,
        sourceModuleCachePath: Path,
        modcachePath: Path,
    ) {
        if (modcachePath.isDirectory()) {
            return
        }
        val manifest = manifestForModuleKey(key).getOrNull() ?: return
        if (!modcachePath.parent.isDirectory()) {
            check(modcachePath.parent.toFile().mkdirs()) { "failed to create directory: ${modcachePath.parent}" }
        }
        val commitTempPath = Files.createTempDirectory(modcachePath.parent, "tmp_" + key.commitID.toDashless())
        try {
            for (fileNode in manifest.getFileNodes()) {
                val cacheBlobPath = sourceModuleCachePath.resolve(digestToBlobPath(fileNode.digest))
                val modulePath = commitTempPath.resolve(fileNode.path)
                if (!modulePath.parent.isDirectory()) {
                    Files.createDirectories(modulePath.parent)
                }
                Files.copy(cacheBlobPath, modulePath)
            }
            try {
                Files.move(commitTempPath, modcachePath, StandardCopyOption.ATOMIC_MOVE)
                // TODO: Not sure if this is the best way to trigger a refresh?
                ApplicationManager.getApplication().invokeLater {
                    VfsUtil.markDirtyAndRefresh(false, true, true, modcachePath.toFile())
                }
            } catch (e: FileSystemException) {
                when (e) {
                    is DirectoryNotEmptyException,
                    is FileAlreadyExistsException,
                    -> {
                        // This is expected if we raced with another process
                    }
                    else -> {
                        // This can happen in a race - DirectoryNotEmpty
                        val message = e.message ?: ""
                        if (!message.contains("Directory not empty")) {
                            error("failed to move directory $commitTempPath to $modcachePath: $e")
                        }
                    }
                }
            }
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

    private fun manifestForModuleKey(key: ModuleKey): Result<Manifest> {
        val bufModuleCachePath = "$bufCacheDir/v2/module/${key.moduleFullName}"
        return try {
            val commitIDPath = Path.of("$bufModuleCachePath/commits/${key.commitID.toDashless()}")
            commitIDPath.bufferedReader().use { CASDigest.parse(it) }
                .map { manifestDigest ->
                    val manifestPath = Path.of("$bufModuleCachePath/${digestToBlobPath(manifestDigest)}")
                    return manifestPath.bufferedReader().use { Manifest.parse(it) }
                }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun digestToBlobPath(digest: CASDigest): String = "blobs/${digest.hex.substring(0, 2)}/${digest.hex.substring(2)}"

    companion object {
        /**
         * Contains the path where v2 CAS cached modules are extracted to disk (so they can be used as source roots).
         * The other cache versions (v1 and v3) just store modules on disk in the normal paths so don't require this.
         */
        private val SYSTEM_BUF_MOD_CACHE = "${PathManager.getSystemPath()}/buf/modcache"

        private val LOG: Logger = logger<ModuleCacheV2>()
    }
}
