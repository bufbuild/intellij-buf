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

package build.buf.intellij

import build.buf.intellij.config.BufConfig
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.impl.FileTypeOverrider
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.yaml.YAMLFileType

/**
 * Make sure `buf.lock` files are treated as YAML files, so they are parsed to PSI for BufModuleIndex to consume.
 *
 * See [build.buf.intellij.index.ModuleKeyIndex].
 */
class BufLockFileTypeOverrider : FileTypeOverrider {
    override fun getOverriddenFileType(file: VirtualFile): FileType? {
        if (file.isDirectory) {
            return null
        }
        return when (file.name) {
            BufConfig.BUF_LOCK -> YAMLFileType.YML
            else -> null
        }
    }
}
