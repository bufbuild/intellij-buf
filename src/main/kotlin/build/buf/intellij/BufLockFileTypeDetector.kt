package build.buf.intellij

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.impl.FileTypeOverrider
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.yaml.YAMLFileType

/**
 * Make sure buf.lock files are treated as YAML files, so they are parsed to PSI for BufModuleIndex to consume.
 *
 * @see build.buf.intellij.index.BufModuleIndex
 */
class BufLockFileTypeOverrider : FileTypeOverrider {
    override fun getOverriddenFileType(file: VirtualFile): FileType? {
        if (file.isDirectory) {
            return null
        }
        return when (file.name) {
            "buf.lock" -> YAMLFileType.YML
            else -> null
        }
    }
}
