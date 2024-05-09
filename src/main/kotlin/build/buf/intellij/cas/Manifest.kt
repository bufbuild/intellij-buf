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

package build.buf.intellij.cas

import java.io.Reader
import java.io.StringReader

/**
 * A manifest contains a list of unique [FileNode] objects by path.
 * There is a standard representation of a manifest - use [Manifest.toString] to encode it as a string.
 * The [Manifest.parse] methods can be used to read a [Manifest] from a string or from a [Reader].
 * In the Buf CLI v2 cache file format, files are stored as a CAS.
 * Each commit has a corresponding [Manifest], which lists out the files contained in the commit.
 * This type is equivalent to `bufcas.Manifest` in the Buf CLI codebase.
 */
class Manifest(fileNodes: List<FileNode>) {
    private val fileNodes: List<FileNode> = fileNodes.sortedBy { it.path }
    private val pathToFileNode = fileNodes.groupingBy { it.path }
        .reduce { key, _, _ -> error("duplicate path $key") }

    /**
     * Returns the set of FileNodes that make up the Manifest.
     */
    fun getFileNodes(): List<FileNode> = fileNodes

    /**
     * Returns the FileNode for the given path, if it exists.
     */
    fun getFileNode(path: String): FileNode? = pathToFileNode[path]

    /**
     * Returns the digest for the given path, if it exists.
     */
    fun getDigest(path: String): CASDigest? = pathToFileNode[path]?.digest

    override fun equals(other: Any?): Boolean {
        val otherManifest = other as? Manifest ?: return false
        return fileNodes == otherManifest.fileNodes
    }

    override fun hashCode(): Int = fileNodes.hashCode()

    /**
     * Returns the string in its canonical form.
     */
    override fun toString(): String {
        val sb = StringBuilder()
        fileNodes.forEach { fileNode ->
            sb.append(fileNode.toString())
                .append('\n')
        }
        return sb.toString()
    }

    companion object {
        /**
         * Parses a [Manifest] from its [Manifest.toString] representation.
         */
        fun parse(reader: Reader): Result<Manifest> = runCatching {
            val fileNodes = reader.buffered().lines().map { FileNode.parse(it).getOrThrow() }.toList()
            Manifest(fileNodes)
        }

        /**
         * Parses a [Manifest] from its [Manifest.toString] representation.
         */
        fun parse(str: String): Result<Manifest> = parse(StringReader(str))
    }
}
