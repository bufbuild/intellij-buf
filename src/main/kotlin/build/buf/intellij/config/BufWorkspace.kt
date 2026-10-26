// Copyright 2022-2026 Buf Technologies, Inc.
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

package build.buf.intellij.config

import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.yaml.YAMLUtil
import org.jetbrains.yaml.psi.YAMLFile

internal fun findBufWorkingDirectory(file: PsiFile): VirtualFile? {
    val virtualFile = file.virtualFile ?: return null
    var directory = virtualFile.parent
    var moduleDirectory: VirtualFile? = null
    while (directory != null) {
        if (directory.findChild(BufConfig.BUF_WORK_YAML) != null) {
            return directory
        }
        val config = directory.findChild(BufConfig.BUF_YAML)
        if (config != null && moduleDirectory == null) {
            val yaml = PsiManager.getInstance(file.project).findFile(config) as? YAMLFile
            val version = yaml?.let { YAMLUtil.getQualifiedKeyInFile(it, "version")?.valueText }
            if (version != "v1" && version != "v1beta1") {
                return directory
            }
            // Legacy modules can belong to an ancestor buf.work.yaml workspace.
            moduleDirectory = directory
        }
        directory = directory.parent
    }
    return moduleDirectory ?: ProjectFileIndex.getInstance(file.project).getContentRootForFile(virtualFile)
}
