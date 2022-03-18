package com.github.bufbuild.intellij.base

import com.github.bufbuild.intellij.annotator.BufLintUtils
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path

@RunWith(JUnit38ClassRunner::class) // todo: remove once Gradle will pick up tests without it
abstract class BufTestBase : CodeInsightFixtureTestCase<ModuleFixtureBuilder<*>>() {
    fun configureByFolder(pathWithinTestData: String, vararg filePathsToConfigureFrom: String) {
        val folderPath = Path.of(ClassLoader.getSystemResource("testData").toURI())
            .resolve(basePath)
            .resolve(pathWithinTestData)
        addChildrenRecursively(folderPath.toFile(), folderPath.toFile())
        myFixture.configureByFiles(*filePathsToConfigureFrom)
        runReadAction {
            BufLintUtils.checkLazily(project, project, Path(myFixture.tempDirPath))
        }.value // this will make "buf lint" to execute which will populate cache
    }

    private fun addChildrenRecursively(root: File, file: File) {
        if (!file.isDirectory) {
            myFixture.addFileToProject(
                FileUtil.getRelativePath(root, file) ?: return,
                file.readText()
            )
            return
        }
        for (child in file.listFiles()!!) {
            addChildrenRecursively(root, child)
        }
    }
}
