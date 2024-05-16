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

package build.buf.intellij.resolve

import build.buf.intellij.annotator.BufAnalyzeUtils
import build.buf.intellij.config.BufConfig
import build.buf.intellij.index.BufIndexes
import build.buf.intellij.module.ModuleKey
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiManager
import kotlinx.coroutines.runBlocking
import org.jetbrains.yaml.YAMLUtil
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLSequence
import java.util.concurrent.TimeUnit

class RefreshAdditionalBufRoots : StartupActivity {

    override fun runActivity(project: Project) {
        project.messageBus.connect().subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: MutableList<out VFileEvent>) {
                    var bufLockChanged = false
                    for (event in events) {
                        if (event.file?.name == BufConfig.BUF_LOCK) {
                            bufLockChanged = true
                            break
                        }
                    }
                    if (bufLockChanged) {
                        updateUserRoots(project, false)
                    }
                }
            },
        )
        updateUserRoots(project, true)
    }

    private fun updateUserRoots(project: Project, initial: Boolean) {
        ApplicationManager.getApplication().executeOnPooledThread {
            DumbService.getInstance(project).runWhenSmart {
                if (initial) {
                    // Trigger initial 'buf build' on all modules to trigger download of dependencies
                    buildAllModules(project)
                }
                val existingModuleKeys = project.getUserData(BufModuleKeysUserData.KEY)?.moduleKeys ?: emptySet()
                val newModuleKeys = hashSetOf<ModuleKey>()
                // TODO: This still can return stale data after VFS changes.
                // Unclear how to ensure we run this after indexing has completed.
                // There don't appear to be any message bus changes that fire when indexing completes.
                for (moduleKey in BufIndexes.getProjectModuleKeys(project)) {
                    newModuleKeys.add(moduleKey)
                }
                if (existingModuleKeys != newModuleKeys) {
                    project.putUserData(BufModuleKeysUserData.KEY, BufModuleKeysUserData(newModuleKeys))
                    ApplicationManager.getApplication().runWriteAction {
                        ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(
                            EmptyRunnable.getInstance(),
                            RootsChangeRescanningInfo.RESCAN_DEPENDENCIES_IF_NEEDED,
                        )
                    }
                }
            }
        }
    }

    private fun buildAllModules(project: Project) {
        val fs = VirtualFileManager.getInstance()
        for (bufModuleConfig in BufIndexes.getProjectBufModuleConfigs(project)) {
            val bufYaml = fs.findFileByUrl(bufModuleConfig.bufYamlUrl) ?: continue
            val bufDir = bufYaml.parent ?: continue
            // Skip projects with no dependencies
            val bufLock = bufDir.findFileByRelativePath(BufConfig.BUF_LOCK) ?: continue
            val bufLockYamlFile = PsiManager.getInstance(project).findFile(bufLock) as? YAMLFile ?: continue
            val bufLockDeps = YAMLUtil.getQualifiedKeyInFile(bufLockYamlFile, "deps")?.value as? YAMLSequence ?: continue
            if (bufLockDeps.items.isEmpty()) {
                continue
            }
            runBlocking {
                val disposable = Disposer.newDisposable()
                val start = System.nanoTime()
                try {
                    BufAnalyzeUtils.runBufCommand(
                        project,
                        disposable,
                        bufYaml.parent.toNioPath(),
                        listOf("build"),
                        expectedExitCodes = setOf(0, 1),
                    )
                } finally {
                    val elapsed = System.nanoTime() - start
                    LOG.debug("built ${bufYaml.path} in ${TimeUnit.NANOSECONDS.toMillis(elapsed)}ms")
                    disposable.dispose()
                }
            }
        }
    }

    companion object {
        private val LOG = logger<RefreshAdditionalBufRoots>()
    }
}
