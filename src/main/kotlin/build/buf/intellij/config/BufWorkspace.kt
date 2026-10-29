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

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.yaml.YAMLUtil
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence

/**
 * Returns the directory the Buf CLI should run from to check [file], falling
 * back to the file's content root when no Buf configuration controls it.
 * Returns null for files outside the project's content.
 */
internal fun findBufWorkingDirectory(file: PsiFile): VirtualFile? {
    val virtualFile = file.virtualFile ?: return null
    val contentRoot = ProjectFileIndex.getInstance(file.project).getContentRootForFile(virtualFile) ?: return null
    return findBufWorkspaceRoot(file.project, virtualFile) ?: contentRoot
}

/**
 * Returns whether any directory above [file] contains a `buf.yaml` or
 * `buf.work.yaml`, without reading either file.
 */
internal fun hasBufConfigAncestor(file: VirtualFile): Boolean = generateSequence(file.parent) { it.parent }
    .any { it.findChild(BufConfig.BUF_YAML) != null || it.findChild(BufConfig.BUF_WORK_YAML) != null }

/**
 * Returns the root of the Buf workspace or module containing [file], or null
 * if no Buf configuration controls it.
 *
 * This mirrors the Buf CLI's workspace discovery: walking up from [file], the
 * first v2 `buf.yaml` or `buf.work.yaml` that lists a module directory
 * containing [file] wins. Configuration files that do not list such a
 * directory are skipped. If no workspace is found, the nearest v1 or v1beta1
 * `buf.yaml` is used.
 */
internal fun findBufWorkspaceRoot(project: Project, file: VirtualFile): VirtualFile? = runReadAction {
    var directory = file.parent
    var v1ModuleDirectory: VirtualFile? = null
    while (directory != null) {
        val bufYAML = directory.findChild(BufConfig.BUF_YAML)?.let { parseConfigFile(project, it) }
        if (bufYAML?.version == "v2" && bufYAML.containsFile(directory, file)) {
            return@runReadAction directory
        }
        val bufWorkYAML = directory.findChild(BufConfig.BUF_WORK_YAML)?.let { parseConfigFile(project, it) }
        if (bufWorkYAML != null && bufWorkYAML.containsFile(directory, file)) {
            return@runReadAction directory
        }
        // The CLI only falls back to v1 modules, but running from a v1beta1
        // module directory also works.
        if (v1ModuleDirectory == null && (bufYAML?.version == "v1" || bufYAML?.version == "v1beta1")) {
            v1ModuleDirectory = directory
        }
        directory = directory.parent
    }
    v1ModuleDirectory
}

private data class BufConfigFile(
    val version: String,
    /** Module directories relative to the configuration file's directory. */
    val moduleDirectories: List<String>,
) {
    fun containsFile(configDirectory: VirtualFile, file: VirtualFile): Boolean = moduleDirectories.any { path ->
        val normalizedPath = normalizeModulePath(path) ?: return@any false
        val moduleDirectory = configDirectory.findFileByRelativePath(normalizedPath) ?: return@any false
        VfsUtilCore.isAncestor(moduleDirectory, file, true)
    }
}

// Returns null for paths the CLI rejects: absolute paths and paths that
// escape the configuration file's directory.
private fun normalizeModulePath(path: String): String? {
    if (OSAgnosticPathUtil.isAbsolute(path)) return null
    val segments = ArrayDeque<String>()
    for (segment in path.split('/')) {
        when (segment) {
            "", "." -> continue
            ".." -> segments.removeLastOrNull() ?: return null
            else -> segments.addLast(segment)
        }
    }
    return segments.joinToString("/")
}

private fun parseConfigFile(project: Project, configFile: VirtualFile): BufConfigFile? {
    val yamlFile = PsiManager.getInstance(project).findFile(configFile) as? YAMLFile ?: return null
    return CachedValuesManager.getCachedValue(yamlFile) {
        CachedValueProvider.Result.create(parseConfigFile(yamlFile), yamlFile)
    }
}

// Returns null for files without a version, which the CLI rejects.
private fun parseConfigFile(yamlFile: YAMLFile): BufConfigFile? {
    val version = YAMLUtil.getQualifiedKeyInFile(yamlFile, "version")?.valueText ?: return null
    if (yamlFile.name == BufConfig.BUF_WORK_YAML) {
        val directories = YAMLUtil.getQualifiedKeyInFile(yamlFile, "directories")?.value as? YAMLSequence
        return BufConfigFile(
            version,
            directories?.items.orEmpty().mapNotNull { (it.value as? YAMLScalar)?.textValue },
        )
    }
    val modules = YAMLUtil.getQualifiedKeyInFile(yamlFile, "modules")?.value as? YAMLSequence
    if (version != "v2" || modules == null || modules.items.isEmpty()) {
        return BufConfigFile(version, listOf("."))
    }
    return BufConfigFile(
        version,
        modules.items.map { item -> item.keysValues.firstOrNull { it.keyText == "path" }?.valueText ?: "." },
    )
}
