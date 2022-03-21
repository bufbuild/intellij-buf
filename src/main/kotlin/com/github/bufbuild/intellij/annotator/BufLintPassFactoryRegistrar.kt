package com.github.bufbuild.intellij.annotator

import com.intellij.codeHighlighting.TextEditorHighlightingPassFactoryRegistrar
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.openapi.project.Project

class BufLintPassFactoryRegistrar : TextEditorHighlightingPassFactoryRegistrar {
    override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
        BufLintPassFactory(project, registrar)
    }
}
