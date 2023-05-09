// Copyright 2022-2023 Buf Technologies, Inc.
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

import build.buf.intellij.annotator.BufAnalyzeUtils
import build.buf.intellij.index.BufModuleIndex
import build.buf.intellij.resolve.BufRootsProvider
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.util.SystemProperties
import org.junit.jupiter.api.Assertions
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path

abstract class BufTestBase : CodeInsightFixtureTestCase<ModuleFixtureBuilder<*>>() {

    protected open fun getEnv() : Map<String, String> = System.getenv()

    fun configureByFolder(pathWithinTestData: String, vararg filePathsToConfigureFrom: String) {
        val folderPath = findTestDataFolder().resolve(pathWithinTestData)
        addChildrenRecursively(folderPath.toFile(), folderPath.toFile())
        myFixture.configureByFiles(*filePathsToConfigureFrom)
        runReadAction {
            BufAnalyzeUtils.checkLazily(project, project, Path(myFixture.tempDirPath))
        }.value // this will make "buf lint" to execute which will populate cache
        val env = getEnv()
        // just to make sure the root is added
        LocalFileSystem.getInstance().apply {
            BufRootsProvider.getBufCacheFolderV1(env)?.let {
                addRootToWatch(it.toString(), true)
            }
            BufRootsProvider.getBufCacheFolderV2(env)?.let {
                addRootToWatch(it.toString(), true)
            }
            refresh(false)
        }
        val projectModules = BufModuleIndex.getAllProjectModules(project)
        assertNotEmpty(projectModules)
        val resolvedModuleRoots = projectModules.mapNotNull {
            BufRootsProvider.findModuleCacheFolderV1(it)
        }
        if (projectModules.size != resolvedModuleRoots.size) {
            val cache = VirtualFileManager.getInstance().findFileByUrl("bufcache:///")
            fail(
                """
                Failed to resolve ${projectModules.size} modules inside ${cache?.canonicalPath}

                Debug info:
                    - Cache base path ${BufRootsProvider.getBufCacheFolderBase(env)} modules
                    - Env vars count: ${env.size}
                    - BUF_CACHE_DIR: ${env["BUF_CACHE_DIR"]}
                    - XDG_CACHE_HOME: ${env["XDG_CACHE_HOME"]}
                    - HOME: ${env["HOME"]}
                    - System Home: ${SystemProperties.getUserHome()}
                    - Found ${resolvedModuleRoots.size} modules
                    - Exists: (${cache?.exists()})
                    - Valid: (${cache?.isValid})
                    - Children: ${cache?.children?.map { it.name }}
            """.trimIndent()
            )
        }
    }

    protected fun findTestDataFolder(): Path {
        val result = Path.of(ClassLoader.getSystemResource("testData").toURI())
            .resolve(basePath)
        Assertions.assertNotNull(result)
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
