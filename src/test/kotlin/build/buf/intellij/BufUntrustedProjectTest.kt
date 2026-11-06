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

package build.buf.intellij

import build.buf.intellij.base.BufTestBase
import build.buf.intellij.inspections.BufNotInstalledInspection
import build.buf.intellij.resolve.BufModuleKeysUserData
import build.buf.intellij.resolve.BufModuleRoots
import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.impl.FileStatusMap
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.PlatformTestUtil
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class BufUntrustedProjectTest : BufTestBase() {
    private lateinit var pluginDir: Path

    override fun setUp() {
        super.setUp()
        pluginDir = Files.createTempDirectory("buf-untrusted-project")
        TrustedProjects.setProjectTrusted(project, false)
    }

    override fun tearDown() {
        try {
            pluginDir.toFile().deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    fun testCheckPluginRunsOnlyAfterProjectIsTrusted() {
        if (SystemInfo.isWindows) return
        val marker = pluginDir.resolve("plugin-ran")
        val plugin = pluginDir.resolve("buf-check-marker")
        Files.writeString(plugin, "#!/bin/sh\ntouch '$marker'\nexit 1\n")
        assertTrue(plugin.toFile().setExecutable(true))
        myFixture.addFileToProject(
            "buf.yaml",
            """
            version: v2
            plugins:
              - plugin: $plugin
            """.trimIndent(),
        )
        myFixture.enableInspections(BufNotInstalledInspection())
        myFixture.configureByText(
            "test.proto",
            """
            syntax = "proto3";
            message Foo {}
            """.trimIndent(),
        )

        val untrustedHighlights = myFixture.doHighlighting()

        assertFalse("buf ran a check plugin in an untrusted project", Files.exists(marker))
        val notInstalled = BufBundle.message("buf.inspection.cli.not.installed")
        assertEmpty(untrustedHighlights.filter { it.description == notInstalled })

        TrustedProjects.setProjectTrusted(project, true)
        // Highlighting before the listener's async restart races with it.
        PlatformTestUtil.waitWithEventsDispatching(
            "highlighting was not restarted after the project was trusted",
            { FileStatusMap.getDirtyTextRange(myFixture.editor, Pass.UPDATE_ALL) != null },
            30,
        )
        myFixture.doHighlighting()

        assertTrue("buf did not run after the project was trusted", Files.exists(marker))
    }

    fun testTrustingProjectRefreshesModuleRoots() {
        myFixture.addFileToProject(
            "buf.yaml",
            """
            version: v1
            deps:
              - buf.build/googleapis/googleapis
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "buf.lock",
            """
            version: v1
            deps:
              - remote: buf.build
                owner: googleapis
                repository: googleapis
                commit: cc916c31859748a68fd229a3c8d7a2e8
                digest: shake256:469b049d0eb04203d5272062636c078decefc96fec69739159c25d85349c50c34c7706918a8b216c5c27f76939df48452148cff8c5c3ae77fa6ba5c25c1b8bf8
            """.trimIndent(),
        )
        BufModuleRoots.updateUserRoots(project, initial = true)
        PlatformTestUtil.waitWithEventsDispatching(
            "module keys were not recorded",
            { project.getUserData(BufModuleKeysUserData.KEY)?.moduleKeys?.isNotEmpty() == true },
            30,
        )
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        val rootsChanges = AtomicInteger()
        project.messageBus.connect(testRootDisposable).subscribe(
            ModuleRootListener.TOPIC,
            object : ModuleRootListener {
                override fun rootsChanged(event: ModuleRootEvent) {
                    rootsChanges.incrementAndGet()
                }
            },
        )

        TrustedProjects.setProjectTrusted(project, true)

        PlatformTestUtil.waitWithEventsDispatching(
            "module roots were not refreshed after the project was trusted",
            { rootsChanges.get() > 0 },
            30,
        )
    }
}
