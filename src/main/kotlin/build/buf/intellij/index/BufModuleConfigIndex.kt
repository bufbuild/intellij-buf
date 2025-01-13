// Copyright 2022-2025 Buf Technologies, Inc.
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

package build.buf.intellij.index

import build.buf.intellij.config.BufConfig
import build.buf.intellij.module.ModuleFullName
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.indexing.ScalarIndexExtension
import com.intellij.util.io.IOUtil
import com.intellij.util.io.KeyDescriptor
import org.jetbrains.yaml.YAMLUtil
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException

/**
 * [BufModuleConfigIndex] indexes [BufModuleConfig] instances loaded from `buf.yaml` files.
 */
class BufModuleConfigIndex : ScalarIndexExtension<BufModuleConfig>() {

    override fun getName(): ID<BufModuleConfig, Void> = BufIndexes.BUF_MODULE_CONFIG_INDEX_ID

    override fun dependsOnFileContent(): Boolean = true

    override fun getVersion(): Int = 1

    override fun getInputFilter(): FileBasedIndex.InputFilter = FileBasedIndex.InputFilter { file ->
        file.name == BufConfig.BUF_YAML
    }

    override fun getKeyDescriptor(): KeyDescriptor<BufModuleConfig> = BufModuleConfigIndexEntryKeyDescriptor

    override fun getIndexer(): DataIndexer<BufModuleConfig, Void, FileContent> {
        return DataIndexer { inputData ->
            val yamlFile = inputData.psiFile as? YAMLFile ?: return@DataIndexer emptyMap()
            val bufYamlFile = yamlFile.virtualFile ?: return@DataIndexer emptyMap()
            val modulePath = bufYamlFile.parent ?: return@DataIndexer emptyMap()
            val version = YAMLUtil.getQualifiedKeyInFile(yamlFile, "version")?.valueText ?: return@DataIndexer emptyMap()
            val name = YAMLUtil.getQualifiedKeyInFile(yamlFile, "name")?.valueText?.let {
                ModuleFullName.parse(it).getOrNull()
            }
            val result = linkedSetOf<BufModuleConfig>()
            when (version) {
                "v1beta1" -> {
                    // NOTE: No support for roots/excludes. Unsupported so far in the plugin and users should move to v1/v2.
                    result.add(BufModuleConfig(bufYamlFile.url, modulePath.url, moduleFullName = name))
                }

                "v1" -> {
                    result.add(BufModuleConfig(bufYamlFile.url, modulePath.url, moduleFullName = name))
                }

                "v2" -> {
                    val modules = YAMLUtil.getQualifiedKeyInFile(yamlFile, "modules")?.value as? YAMLSequence
                    if (modules != null && modules.items.isNotEmpty()) {
                        for (module in modules.items) {
                            val moduleItem = module.keysValues.filter { it.value is YAMLScalar }
                                .associate { it.keyText to it.valueText }
                            val moduleName = moduleItem["name"]?.let { ModuleFullName.parse(it).getOrNull() }
                            val path = moduleItem["path"] ?: return@DataIndexer emptyMap()
                            val v2ModulePath = modulePath.findFileByRelativePath(path) ?: return@DataIndexer emptyMap()
                            result.add(BufModuleConfig(bufYamlFile.url, v2ModulePath.url, moduleFullName = moduleName))
                        }
                    } else {
                        result.add(BufModuleConfig(bufYamlFile.url, modulePath.url, moduleFullName = name))
                    }
                }

                else -> return@DataIndexer emptyMap()
            }
            return@DataIndexer result.associateWith { null }
        }
    }
}

object BufModuleConfigIndexEntryKeyDescriptor : KeyDescriptor<BufModuleConfig> {
    override fun getHashCode(value: BufModuleConfig): Int = value.pathUrl.hashCode()

    override fun isEqual(a: BufModuleConfig, b: BufModuleConfig): Boolean = a.pathUrl == b.pathUrl

    override fun save(out: DataOutput, value: BufModuleConfig) {
        IOUtil.writeStringList(out, listOf(value.bufYamlUrl, value.pathUrl, value.moduleFullName?.toString() ?: ""))
    }

    override fun read(input: DataInput): BufModuleConfig {
        val (bufYAMLPath, path, moduleFullNameStr) = IOUtil.readStringList(input)
        val moduleFullName = try {
            if (moduleFullNameStr.isEmpty()) null else ModuleFullName.parse(moduleFullNameStr).getOrThrow()
        } catch (e: Exception) {
            throw IOException(e)
        }
        return BufModuleConfig(bufYAMLPath, path, moduleFullName = moduleFullName)
    }
}
