package com.github.bufbuild.intellij.fixes

import com.github.bufbuild.intellij.BufBundle
import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.protobuf.lang.psi.PbFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping

class IgnoreBufIssueQuickFix(private val type: String) : BaseIntentionAction() {
    override fun getFamilyName(): String = BufBundle.getMessage("buf.quickfix.family")

    override fun getText(): String = BufBundle.getMessage("buf.quickfix.ignore.issue")

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = file is PbFile

    override fun startInWriteAction(): Boolean = true

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        checkCommentIgnoresAllowed(project, editor, file)
        addIgnoreComment(project, editor, file)
    }

    private fun checkCommentIgnoresAllowed(project: Project, editor: Editor, file: PsiFile) {
        val bufLockFile = findBufConfigFile(file) ?: return
        val yamlRootMapping = bufLockFile.children.firstOrNull()?.children?.find {
            it is YAMLMapping
        } as? YAMLMapping ?: return
        val lintNode = yamlRootMapping.getKeyValueByKey("lint")
        if (lintNode is YAMLMapping && lintNode.getKeyValueByKey("allow_comment_ignores") != null) {
            return
        }
        val codeStyleManager = CodeStyleManager.getInstance(project)
        if (lintNode == null) {
            val generateNode = PsiFileFactory.getInstance(project)
                .createFileFromText(YAMLLanguage.INSTANCE, "\nlint:\n\tallow_comment_ignores: true")
                .children.first()
            yamlRootMapping.add(generateNode.firstChild)
            yamlRootMapping.add(generateNode.lastChild)
            codeStyleManager.reformatText(bufLockFile, listOf(yamlRootMapping.textRange))
        } else {
            val generateNode = PsiFileFactory.getInstance(project)
                .createFileFromText(YAMLLanguage.INSTANCE, "\nallow_comment_ignores: true")
                .children.first()
            lintNode.add(generateNode.firstChild)
            lintNode.add(generateNode.lastChild)
            codeStyleManager.reformatText(bufLockFile, listOf(lintNode.textRange))
        }
    }

    private fun findBufConfigFile(file: PsiFile): YAMLFile? {
        var vFile = file.virtualFile.parent
        while (vFile != null) {
            val bufLock = vFile.findChild("buf.yaml")
            if (bufLock != null) {
                return PsiManager.getInstance(file.project).findFile(bufLock) as YAMLFile
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
