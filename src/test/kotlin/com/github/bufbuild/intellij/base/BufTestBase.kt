package com.github.bufbuild.intellij.base

import com.github.bufbuild.intellij.annotator.BufAnalyzeUtils
import com.github.bufbuild.intellij.index.BufModuleIndex
import com.github.bufbuild.intellij.resolve.BufRootsProvider
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.util.SystemProperties
import junit.framework.TestCase
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path

@RunWith(JUnit38ClassRunner::class) // todo: remove once Gradle will pick up tests without it
abstract class BufTestBase : CodeInsightFixtureTestCase<ModuleFixtureBuilder<*>>() {
    fun configureByFolder(pathWithinTestData: String, vararg filePathsToConfigureFrom: String) {
        val folderPath = findTestDataFolder().resolve(pathWithinTestData)
        addChildrenRecursively(folderPath.toFile(), folderPath.toFile())
        myFixture.configureByFiles(*filePathsToConfigureFrom)
        runReadAction {
            BufAnalyzeUtils.checkLazily(project, project, Path(myFixture.tempDirPath))
        }.value // this will make "buf lint" to execute which will populate cache
        // just to make sure the root is added
        LocalFileSystem.getInstance().apply {
            addRootToWatch(
                BufRootsProvider.bufCacheFolderBase.toString(),
                true
            )
            refresh(false)
        }
        val projectModules = BufModuleIndex.getAllProjectModules(project)
        assertNotEmpty(projectModules)
        val resolvedModuleRoots = projectModules.mapNotNull {
            BufRootsProvider.findModuleCacheFolder(it)
        }
        if (projectModules.size != resolvedModuleRoots.size) {
            val cache = BufRootsProvider.bufCacheFolder
            fail("""
                Failed to resolve ${projectModules.size} modules inside ${cache?.canonicalPath}
                
                Debug info:
                    - Cache base path ${BufRootsProvider.bufCacheFolderBase} modules
                    - Env vars count: ${System.getenv().size}
                    - BUF_CACHE_DIR: ${System.getenv()["BUF_CACHE_DIR"]}
                    - XDG_CACHE_HOME: ${System.getenv()["XDG_CACHE_HOME"]}
                    - HOME: ${System.getenv()["HOME"]}
                    - System Home: ${SystemProperties.getUserHome()}
                    - Found ${resolvedModuleRoots.size} modules
                    - Exists: (${cache?.exists()})
                    - Valid: (${cache?.isValid})
                    - Children: ${cache?.children?.map { it.name }}
            """.trimIndent())
        }
    }

    protected fun findTestDataFolder(): Path {
        val result = Path.of(ClassLoader.getSystemResource("testData").toURI())
            .resolve(basePath)
        TestCase.assertNotNull(result)
        return result
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
