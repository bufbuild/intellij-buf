package build.buf.intellij.index

import build.buf.intellij.model.BufModuleCoordinates
import com.intellij.openapi.project.Project
import com.intellij.util.indexing.*
import com.intellij.util.io.IOUtil
import com.intellij.util.io.KeyDescriptor
import org.jetbrains.yaml.psi.*
import java.io.DataInput
import java.io.DataOutput

class BufModuleIndex : ScalarIndexExtension<BufModuleCoordinates>() {
    companion object {
        private val INDEX_ID = ID.create<BufModuleCoordinates, Void>("BufModuleIndex")

        fun getAllProjectModules(project: Project): Collection<BufModuleCoordinates> {
            return FileBasedIndex.getInstance().getAllKeys(INDEX_ID, project)
        }
    }

    override fun getName(): ID<BufModuleCoordinates, Void> = INDEX_ID

    override fun dependsOnFileContent(): Boolean = true

    override fun getVersion(): Int = 1

    override fun getInputFilter(): FileBasedIndex.InputFilter {
        return FileBasedIndex.InputFilter { file ->
            file.name == "buf.lock"
        }
    }

    override fun getKeyDescriptor(): KeyDescriptor<BufModuleCoordinates> = BufModuleIndexEntryKeyDescriptor

    override fun getIndexer(): DataIndexer<BufModuleCoordinates, Void, FileContent> {
        return DataIndexer { inputData ->
            val yamlFile = inputData.psiFile as? YAMLFile ?: return@DataIndexer emptyMap()
            val result = linkedSetOf<BufModuleCoordinates>()
            yamlFile.accept(object : YamlRecursivePsiElementVisitor() {
                override fun visitKeyValue(keyValue: YAMLKeyValue) {
                    if (keyValue.key?.textMatches("deps") == true) {
                        val yamlDeps = (keyValue.value as? YAMLSequence)?.items ?: emptyList()
                        result.addAll(yamlDeps.mapNotNull { findModuleDep(it) })
                    }
                }
            })
            return@DataIndexer result.associateWith { null }
        }
    }

    private fun findModuleDep(item: YAMLSequenceItem): BufModuleCoordinates? {
        val bufModuleItem = item.keysValues.map {
            it.keyText to it.valueText
        }.toMap()
        return BufModuleCoordinates(
            remote = bufModuleItem["remote"] ?: return null,
            owner = bufModuleItem["owner"] ?: return null,
            repository = bufModuleItem["repository"] ?: return null,
            commit = bufModuleItem["commit"] ?: return null,
        )
    }
}

object BufModuleIndexEntryKeyDescriptor : KeyDescriptor<BufModuleCoordinates> {
    override fun getHashCode(value: BufModuleCoordinates): Int = value.hashCode()

    override fun isEqual(a: BufModuleCoordinates, b: BufModuleCoordinates): Boolean = a == b

    override fun save(out: DataOutput, value: BufModuleCoordinates) {
        IOUtil.writeStringList(out, listOf(value.remote, value.owner, value.repository, value.commit))
    }

    override fun read(input: DataInput): BufModuleCoordinates {
        val deserializedList = IOUtil.readStringList(input)
        return BufModuleCoordinates(
            remote = deserializedList[0],
            owner = deserializedList[1],
            repository = deserializedList[2],
            commit = deserializedList[3],
        )
    }
}
