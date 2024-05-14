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

package build.buf.intellij.fixes

import build.buf.intellij.BufBundle
import build.buf.intellij.config.BufConfig
import build.buf.intellij.vendor.isProtobufFile
import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.yaml.YAMLElementGenerator
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence

class IgnoreBufIssueQuickFix(private val type: String) : BaseIntentionAction() {
    override fun getFamilyName(): String = BufBundle.getMessage("buf.quickfix.family")

    override fun getText(): String = BufBundle.getMessage("buf.quickfix.ignore.issue")

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = file.isProtobufFile()

    override fun startInWriteAction(): Boolean = true

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        checkCommentIgnoresAllowed(project, file)
        addIgnoreComment(project, editor, file)
    }

    private fun checkCommentIgnoresAllowed(project: Project, file: PsiFile) {
        val bufConfigFile = findBufConfigFile(file) ?: return
        val yamlRootMapping = bufConfigFile.children.firstOrNull()?.children?.find {
            it is YAMLMapping
        } as? YAMLMapping ?: return
        val version = yamlRootMapping.getKeyValueByKey("version")?.valueText ?: return
        when (version) {
            "v2" -> enableCommentIgnoresV2(yamlRootMapping)
            "v1", "v1beta1" -> enableCommentIgnoresV1(project, bufConfigFile, yamlRootMapping)
            else -> return
        }
    }

    private fun enableCommentIgnoresV2(yamlRootMapping: YAMLMapping) {
        // Disable on all modules if overridden there.
        val modules = yamlRootMapping.getKeyValueByKey("modules")?.value as? YAMLSequence
        if (modules != null) {
            for (module in modules.items) {
                val moduleMapping = module?.value as? YAMLMapping ?: continue
                enableCommentIgnoresV2(moduleMapping)
            }
        }
        val lintNode = yamlRootMapping.getKeyValueByKey("lint") ?: return
        val lintNodeValue = lintNode.value as YAMLMapping? ?: return
        val disallowCommentIgnoresNode = lintNodeValue.getKeyValueByKey("disallow_comment_ignores") ?: return
        val disallowCommentIgnores = disallowCommentIgnoresNode.value as? YAMLScalar ?: return
        if (disallowCommentIgnores.textValue != "true") {
            return
        }
        disallowCommentIgnoresNode.parentMapping?.deleteKeyValue(disallowCommentIgnoresNode)
        if (lintNodeValue.keyValues.isEmpty()) {
            yamlRootMapping.deleteKeyValue(lintNode)
        }
    }

    private fun enableCommentIgnoresV1(project: Project, bufConfigFile: YAMLFile, yamlRootMapping: YAMLMapping) {
        val codeStyleManager = CodeStyleManager.getInstance(project)
        val lintNode = yamlRootMapping.getKeyValueByKey("lint")
        if (lintNode == null) {
            val generateNode = PsiFileFactory.getInstance(project)
                .createFileFromText(YAMLLanguage.INSTANCE, "\nlint:\n\tallow_comment_ignores: true")
                .children.first()
            yamlRootMapping.add(generateNode.firstChild)
            yamlRootMapping.add(generateNode.lastChild)
            codeStyleManager.reformatText(bufConfigFile, listOf(yamlRootMapping.textRange))
            return
        }
        val lintNodeValue = lintNode.value as? YAMLMapping ?: return
        val allowCommentIgnoresNode = lintNodeValue.getKeyValueByKey("allow_comment_ignores")
        if (allowCommentIgnoresNode?.valueText == "true") {
            return
        }
        val newValue = YAMLElementGenerator.getInstance(project).createYamlKeyValue("allow_comment_ignores", "true")
        lintNodeValue.putKeyValue(newValue)
        codeStyleManager.reformatText(bufConfigFile, listOf(lintNode.textRange))
    }

    private fun findBufConfigFile(file: PsiFile): YAMLFile? {
        var vFile = file.virtualFile?.parent
        while (vFile != null) {
            val bufConfig = vFile.findChild(BufConfig.BUF_YAML)
            if (bufConfig != null) {
                return PsiManager.getInstance(file.project).findFile(bufConfig) as YAMLFile
            }
            vFile = vFile.parent
        }
        return null
    }

    private fun addIgnoreComment(project: Project, editor: Editor, file: PsiFile) {
        val lineNumber = editor.document.getLineNumber(editor.caretModel.offset)
        val startOffset = editor.document.getLineStartOffset(lineNumber)
        val textToInsert = "// buf:lint:ignore $type\n"
        editor.document.insertString(startOffset, textToInsert)
        val codeStyleManager = CodeStyleManager.getInstance(project)
        codeStyleManager.reformatText(file, startOffset, startOffset + textToInsert.length)
    }
}
