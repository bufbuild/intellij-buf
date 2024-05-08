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

import java.io.File
import java.nio.file.Path

/**
 * Represents a [FileNode] (an entry in a [Manifest]).
 * Equivalent to `bufcas.FileNode` in the Buf CLI codebase.
 */
data class FileNode(val path: String, val digest: CASDigest) {
    init {
        require(path.isNotBlank()) { "File path cannot be blank." }
        val normalizedPath = Path.of(path.replace(File.separatorChar, '/')).normalize()
        require(!normalizedPath.isAbsolute) { "File path $path must be relative." }
        require(normalizedPath.toString() == path) { "Path $path should equal normalized path $normalizedPath." }
        require(!normalizedPath.toString().startsWith("../")) { "Path $path is outside the context directory." }
    }

    /**
     * Formats a file node as represented in a [Manifest] as `digest.toString()  path` (two space delimited).
     */
    override fun toString(): String = "$digest$SEPARATOR$path"

    companion object {
        private const val SEPARATOR = "  "

        /**
         * Attempts to parse a [FileNode] as encoded by [FileNode.toString].
         */
        fun parse(fileNodeStr: String): Result<FileNode> = try {
            val components = fileNodeStr.split(SEPARATOR)
            require(components.size == 2) { "invalid file node: $fileNodeStr" }
            CASDigest.parse(components[0]).map { digest -> FileNode(components[1], digest) }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
