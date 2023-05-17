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

import build.buf.intellij.BufBundle
import build.buf.intellij.icons.BufIcons
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import java.nio.file.Path
import java.util.*
import javax.swing.Icon

class BufCacheLibrary(private val name: String, private val cacheDir: String) : SyntheticLibrary(), ItemPresentation {
    override fun getPresentableText(): String {
        return BufBundle.message("syntactic.library.text", name)
    }

    override fun getIcon(unused: Boolean): Icon {
        return BufIcons.Logo
    }

    override fun equals(other: Any?): Boolean {
        val otherLibrary = other as? BufCacheLibrary ?: return false
        return name == other.name && cacheDir == otherLibrary.cacheDir
    }

    override fun hashCode(): Int = Objects.hash(name, cacheDir)

    override fun getSourceRoots(): Collection<VirtualFile> {
        return listOfNotNull(VirtualFileManager.getInstance().findFileByNioPath(Path.of(cacheDir)))
    }
}
