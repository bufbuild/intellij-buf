package build.buf.intellij.resolve

import build.buf.intellij.BufBundle
import build.buf.intellij.icons.BufIcons
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

class BufCacheLibrary(private val bufCacheFolder: VirtualFile) : SyntheticLibrary(), ItemPresentation {
    override fun getPresentableText(): String {
        return BufBundle.message("syntactic.library.text")
    }

    override fun getIcon(unused: Boolean): Icon {
        return BufIcons.Logo
    }

    override fun equals(other: Any?): Boolean {
        val otherLibrary = other as? BufCacheLibrary ?: return false
        return bufCacheFolder == otherLibrary.bufCacheFolder
    }

    override fun hashCode(): Int = bufCacheFolder.hashCode()

    override fun getSourceRoots(): Collection<VirtualFile> =
        if (bufCacheFolder.isValid) bufCacheFolder.children.toList() else emptyList()
}
