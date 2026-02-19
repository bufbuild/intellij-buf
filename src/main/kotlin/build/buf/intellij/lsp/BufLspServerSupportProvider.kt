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

package build.buf.intellij.lsp

import build.buf.intellij.BufBundle
import build.buf.intellij.configurable.BufConfigurable
import build.buf.intellij.icons.BufIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.lsWidget.LspServerWidgetItem

/**
 * Provides LSP server support for Buf Protocol Buffer files.
 * This class is the entry point for registering and starting the Buf Language Server.
 */
class BufLspServerSupportProvider : LspServerSupportProvider {
    private val log = logger<BufLspServerSupportProvider>()
    private val notifiedProjects = mutableSetOf<Project>()

    override fun fileOpened(
        project: Project,
        file: VirtualFile,
        serverStarter: LspServerSupportProvider.LspServerStarter,
    ) {
        // Skip LSP in test mode to avoid interference with CLI-based tests
        if (System.getProperty("buf.test.disableLsp") == "true") {
            return
        }

        // Only handle .proto files
        if (file.extension != "proto") {
            return
        }

        // First check if version is already cached (fast, no blocking)
        val cachedVersionInfo = BufVersionDetector.getVersionInfo(project, checkIfMissing = false)

        if (cachedVersionInfo != null) {
            // Version is cached, we can check immediately
            if (!cachedVersionInfo.supportsLsp) {
                if (project !in notifiedProjects) {
                    notifiedProjects.add(project)
                    showLspUnavailableNotification(project)
                }
                return
            }

            // Version supports LSP, start the server
            try {
                serverStarter.ensureServerStarted(BufLspServerDescriptor(project))
                log.info("Buf LSP server started for ${file.name} (cached version)")
            } catch (e: Exception) {
                log.error("Failed to start Buf LSP server", e)
                showLspErrorNotification(project, e.message ?: BufBundle.message("lsp.error.unknown"))
            }
        } else {
            // Version not cached yet, need to detect it asynchronously to avoid blocking
            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val versionInfo = BufVersionDetector.getVersionInfo(project, checkIfMissing = true)

                    if (versionInfo == null || !versionInfo.supportsLsp) {
                        if (project !in notifiedProjects) {
                            notifiedProjects.add(project)
                            showLspUnavailableNotification(project)
                        }
                        return@executeOnPooledThread
                    }

                    // Version supports LSP, start the server
                    serverStarter.ensureServerStarted(BufLspServerDescriptor(project))
                    log.info("Buf LSP server started for ${file.name} (after async version detection)")
                } catch (e: Exception) {
                    log.error("Failed to start Buf LSP server", e)
                    showLspErrorNotification(project, e.message ?: BufBundle.message("lsp.error.unknown"))
                }
            }
        }
    }

    private fun showLspUnavailableNotification(project: Project) {
        val versionInfo = BufVersionDetector.getVersionInfo(project)
        val message = if (versionInfo != null) {
            BufBundle.message("lsp.unavailable.version.too.old", versionInfo.version)
        } else {
            BufBundle.message("lsp.unavailable.not.found")
        }

        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup(BufBundle.message("name"))
                    .createNotification(
                        BufBundle.message("lsp.unavailable.title"),
                        message,
                        NotificationType.WARNING,
                    )
                    .addAction(
                        object : com.intellij.notification.NotificationAction(BufBundle.message("action.open.settings")) {
                            override fun actionPerformed(
                                e: com.intellij.openapi.actionSystem.AnActionEvent,
                                notification: com.intellij.notification.Notification,
                            ) {
                                ShowSettingsUtil.getInstance().showSettingsDialog(project, "Buf")
                                notification.expire()
                            }
                        },
                    )
                    .notify(project)
            }
        }
    }

    private fun showLspErrorNotification(project: Project, errorMessage: String) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup(BufBundle.message("name"))
                    .createNotification(
                        BufBundle.message("lsp.error.title"),
                        BufBundle.message("lsp.error.failed.to.start", errorMessage),
                        NotificationType.ERROR,
                    )
                    .addAction(
                        object : com.intellij.notification.NotificationAction(BufBundle.message("action.open.settings")) {
                            override fun actionPerformed(
                                e: com.intellij.openapi.actionSystem.AnActionEvent,
                                notification: com.intellij.notification.Notification,
                            ) {
                                ShowSettingsUtil.getInstance().showSettingsDialog(project, "Buf")
                                notification.expire()
                            }
                        },
                    )
                    .notify(project)
            }
        }
    }

    override fun createLspServerWidgetItem(
        lspServer: LspServer,
        currentFile: VirtualFile?,
    ) = LspServerWidgetItem(
        lspServer,
        currentFile,
        BufIcons.Logo,
        BufConfigurable::class.java,
    )
}
