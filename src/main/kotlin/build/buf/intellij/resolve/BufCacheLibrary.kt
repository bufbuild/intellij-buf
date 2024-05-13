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

import build.buf.intellij.BufBundle
import build.buf.intellij.icons.BufIcons
import build.buf.intellij.module.ModuleKey
import build.buf.intellij.module.toDashless
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import javax.swing.Icon

/**
 * Represents a BSR module as an external library in the IntelliJ [com.intellij.openapi.project.Project].
 */
class BufCacheLibrary(private val moduleKey: ModuleKey, private val sourceRootUrl: String) :
    SyntheticLibrary(
        "BufCacheLibrary($sourceRootUrl)",
        null,
    ),
    ItemPresentation {
    override fun getPresentableText(): String = BufBundle.message("syntactic.library.text", "${moduleKey.moduleFullName}:${moduleKey.commitID.toDashless().substring(0, 8)}")

    override fun getIcon(unused: Boolean): Icon = BufIcons.Logo

    override fun equals(other: Any?): Boolean {
        val otherLibrary = other as? BufCacheLibrary ?: return false
        return sourceRootUrl == otherLibrary.sourceRootUrl
    }

    override fun hashCode(): Int = sourceRootUrl.hashCode()

    override fun getSourceRoots(): Collection<VirtualFile> = listOfNotNull(
        VirtualFileManager.getInstance().findFileByUrl(sourceRootUrl),
    )
}
