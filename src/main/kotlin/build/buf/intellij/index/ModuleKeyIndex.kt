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

package build.buf.intellij.index

import build.buf.intellij.config.BufConfig
import build.buf.intellij.module.ModuleDigest
import build.buf.intellij.module.ModuleFullName
import build.buf.intellij.module.ModuleKey
import build.buf.intellij.module.UUIDUtils
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.indexing.ScalarIndexExtension
import com.intellij.util.io.IOUtil
import com.intellij.util.io.KeyDescriptor
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YAMLSequenceItem
import org.jetbrains.yaml.psi.YamlRecursivePsiElementVisitor
import java.io.DataInput
import java.io.DataOutput

/**
 * Indexes the dependencies found in `buf.lock` files as [ModuleKey] instances.
 */
class ModuleKeyIndex : ScalarIndexExtension<ModuleKey>() {
    override fun getName(): ID<ModuleKey, Void> = BufIndexes.MODULE_KEY_INDEX_ID

    override fun dependsOnFileContent(): Boolean = true

    override fun getVersion(): Int = 4

    override fun getInputFilter(): FileBasedIndex.InputFilter = FileBasedIndex.InputFilter { file ->
        file.name == BufConfig.BUF_LOCK
    }

    override fun getKeyDescriptor(): KeyDescriptor<ModuleKey> = ModuleKeyIndexEntryKeyDescriptor

    override fun getIndexer(): DataIndexer<ModuleKey, Void, FileContent> {
        return DataIndexer { inputData ->
            val yamlFile = inputData.psiFile as? YAMLFile ?: return@DataIndexer emptyMap()
            val lockFileURL = yamlFile.virtualFile?.url ?: return@DataIndexer emptyMap()
            val result = linkedSetOf<ModuleKey>()
            yamlFile.accept(object : YamlRecursivePsiElementVisitor() {
                override fun visitKeyValue(keyValue: YAMLKeyValue) {
                    if (keyValue.key?.textMatches("deps") == true) {
                        val yamlDeps = (keyValue.value as? YAMLSequence)?.items ?: emptyList()
                        result.addAll(yamlDeps.mapNotNull { findModuleKey(lockFileURL, it) })
                    }
                }
            })
            return@DataIndexer result.associateWith { null }
        }
    }

    private fun findModuleKey(localFileURL: String, item: YAMLSequenceItem): ModuleKey? {
        val bufModuleItem = item.keysValues.filter { it.value is YAMLScalar }
            .associate {
                it.keyText to it.valueText
            }
        return try {
            val moduleFullName = bufModuleItem["name"].let { moduleFullName ->
                if (moduleFullName != null) { // v2
                    ModuleFullName.parse(moduleFullName).getOrThrow()
                } else { // v1 or v1beta1
                    val registry = bufModuleItem["remote"] ?: return null
                    val owner = bufModuleItem["owner"] ?: return null
                    val name = bufModuleItem["repository"] ?: return null
                    ModuleFullName(registry, owner, name)
                }
            }
            val commit = bufModuleItem["commit"] ?: return null
            val digest = bufModuleItem["digest"]?.let {
                ModuleDigest.parse(it).getOrNull()
            }
            val commitID = UUIDUtils.fromDashless(commit).getOrThrow()
            ModuleKey(moduleFullName, commitID, digest = digest)
        } catch (e: Exception) {
            LOG.warn("unable to parse dependency $bufModuleItem in $localFileURL: $e")
            return null
        }
    }

    companion object {
        private val LOG = logger<ModuleKeyIndex>()
    }
}

object ModuleKeyIndexEntryKeyDescriptor : KeyDescriptor<ModuleKey> {
    override fun getHashCode(value: ModuleKey): Int = value.hashCode()

    override fun isEqual(a: ModuleKey, b: ModuleKey): Boolean = a == b

    override fun save(out: DataOutput, value: ModuleKey) {
        IOUtil.writeStringList(out, listOf(value.toString(), value.digest?.toString().orEmpty()))
    }

    override fun read(input: DataInput): ModuleKey {
        val (moduleKeyString, digestString) = IOUtil.readStringList(input)
        val digest = if (digestString.isNotEmpty()) digestString?.let { ModuleDigest.parse(it).getOrThrow() } else null
        return ModuleKey.parse(moduleKeyString, digest = digest).getOrThrow()
    }
}
