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

package build.buf.intellij.inspections

import build.buf.intellij.base.BufTestBase
import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.createGlobalContextForTool

class BufAnalyzeInspectionTest : BufTestBase() {
    override fun getBasePath(): String = "inspections"

    fun testUnsavedDocument() {
        myFixture.configureByText(
            "snake_case.proto",
            """
            syntax = "proto3";

            message Foo {
              string bar = 1;
            }
            """.trimIndent(),
        )
        // An unsaved edit forces the analysis to save documents (a write action)
        // before running buf, which previously deadlocked batch inspections.
        val document = myFixture.editor.document
        WriteCommandAction.runWriteCommandAction(project) {
            val offset = document.text.indexOf("bar")
            document.replaceString(offset, offset + "bar".length, "Bar")
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document))

        val toolWrapper = GlobalInspectionToolWrapper(BufAnalyzeInspection())
        val scope = AnalysisScope(myFixture.file)
        val globalContext = createGlobalContextForTool(scope, project, listOf(toolWrapper))
        InspectionTestUtil.runTool(toolWrapper, scope, globalContext)
        InspectionTestUtil.compareToolResults(
            globalContext,
            toolWrapper,
            false,
            findTestDataFolder().resolve("unsavedDocument").toString(),
        )
    }
}
