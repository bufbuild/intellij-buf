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

package build.buf.intellij.base

import build.buf.intellij.settings.bufSettings
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import org.assertj.core.api.Assertions
import java.io.File
import java.nio.file.Path

abstract class BufTestBase : CodeInsightFixtureTestCase<ModuleFixtureBuilder<*>>() {

    override fun setUp() {
        super.setUp()
        configureCLI()
    }

    private fun configureCLI(cliPath: String = System.getProperty("BUF_CLI", "build/gobin/buf")) {
        val cliFile = File(cliPath)
        if (!cliFile.isFile) {
            throw IllegalStateException("invalid buf CLI: ${cliFile.absolutePath}")
        }
        if (!cliFile.canExecute() && !cliFile.setExecutable(true)) {
            throw IllegalStateException("unable to execute buf CLI: ${cliFile.absolutePath}")
        }
        if (project.bufSettings.state.bufCLIPath != cliPath) {
            project.bufSettings.state = project.bufSettings.state.copy(
                bufCLIPath = cliPath,
            )
        }
    }

    fun configureByFolder(pathWithinTestData: String, vararg filePathsToConfigureFrom: String) {
        val folderPath = findTestDataFolder().resolve(pathWithinTestData)
        addChildrenRecursively(folderPath.toFile(), folderPath.toFile())
        myFixture.configureByFiles(*filePathsToConfigureFrom)
    }

    protected fun findTestDataFolder(): Path {
        val result = Path.of(ClassLoader.getSystemResource("testData").toURI())
            .resolve(basePath)
        Assertions.assertThat(result).isNotNull()
        return result
    }

    private fun addChildrenRecursively(root: File, file: File) {
        if (!file.isDirectory) {
            myFixture.addFileToProject(
                FileUtil.getRelativePath(root, file) ?: return,
                file.readText(),
            )
            return
        }
        for (child in file.listFiles()!!) {
            addChildrenRecursively(root, child)
        }
    }
}
