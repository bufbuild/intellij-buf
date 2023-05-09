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

package build.buf.intellij.manifest

import com.intellij.openapi.vfs.VirtualFile
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class Manifest(
    /* Contains digest hex to list of relative paths */
    private val digestToPaths: Map<String, List<String>>,
    /* Contains relative path to Digest */
    private val pathToDigest: Map<String, Digest>,
    /* Contains mapping of path to canonical path */
    private val pathToCanonicalPath: Map<String, String>
) {
    /**
     * Returns all unique paths in the manifest, order not guaranteed.
     */
    fun getPaths(): Set<String> {
        return Collections.unmodifiableSet(pathToDigest.keys)
    }

    /**
     * Returns all unique digests in the manifest, order not guaranteed.
     */
    fun getDigests(): Set<Digest> {
        return Collections.unmodifiableSet(HashSet(pathToDigest.values))
    }

    /**
     * Returns the paths for a given digest, or an empty list if not found.
     */
    fun getPathsFor(digest: String): List<String> {
        val paths = this.digestToPaths[digest] ?: return Collections.emptyList()
        return Collections.unmodifiableList(paths)
    }

    /**
     * Returns the digest for a path, or null if not found.
     */
    fun getDigestFor(path: String): Digest? {
        return this.pathToDigest[path]
    }

    /**
     * Returns the canonical path in the local cache to the relative file.
     */
    fun getCanonicalPath(relPath: String): String? {
        return this.pathToCanonicalPath[relPath]
    }

    /**
     * Returns true if the manifest has no entries.
     */
    fun isEmpty(): Boolean {
        return this.pathToDigest.isEmpty() && this.digestToPaths.isEmpty()
    }

    companion object {
        /**
         * Creates a Manifest from a Buf repository cache directory and a commit id.
         * The repo cache directory should be relative to the Buf cache directory (e.g.
         * '{remote}/{owner}/{repo}').
         *
         * Returns a Manifest if found, or null if not found (or an error occurred
         * parsing the manifest).
         */
        fun fromCommit(repoCacheDir: VirtualFile, commit: String): Manifest? {
            val commitDigest =
                repoCacheDir.findFileByRelativePath("commits/${commit}")?.inputStream?.bufferedReader()?.use {
                    Digest.fromString(it.readLine())
                } ?: return null
            val digestToBlobPath = HashMap<String, ArrayList<String>>()
            val pathToDigest = HashMap<String, Digest>()
            val pathToCanonicalPath = HashMap<String, String>()
            val blobFile = repoCacheDir.findFileByRelativePath(
                "blobs/${commitDigest.hex.substring(0, 2)}/${commitDigest.hex.substring(2)}"
            )?.inputStream?.bufferedReader()?.use {
                it.forEachLine { line ->
                    val entry = line.split("  ", limit = 2)
                    if (entry.size != 2 || entry[1].isEmpty()) {
                        return@forEachLine
                    }
                    val digest = Digest.fromString(entry[0]) ?: return@forEachLine
                    val path = entry[1]
                    val paths = digestToBlobPath[digest.hex]
                    if (paths == null) {
                        digestToBlobPath[digest.hex] = ArrayList(listOf(path))
                    } else {
                        paths.add(path)
                    }
                    pathToDigest[path] = digest
                    val blobFilePath = repoCacheDir.findFileByRelativePath(
                        "blobs/${digest.hex.substring(0, 2)}/${digest.hex.substring(2)}"
                    )?.canonicalPath
                    if (blobFilePath != null) {
                        pathToCanonicalPath[path] = blobFilePath
                    }
                }
            }
            return Manifest(digestToBlobPath.toMap(), pathToDigest, pathToCanonicalPath)
        }
    }
}
