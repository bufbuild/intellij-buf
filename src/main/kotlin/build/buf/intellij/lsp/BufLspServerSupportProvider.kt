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

package build.buf.intellij.lsp

import build.buf.intellij.BufBundle
import build.buf.intellij.settings.bufSettings
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerSupportProvider

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
        // Check if LSP is enabled in settings
        if (!project.bufSettings.state.useLspServer) {
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
                log.info("Buf LSP server started for ${file.name}")
            } catch (e: Exception) {
                log.error("Failed to start Buf LSP server", e)
                showLspErrorNotification(project, e.message ?: "Unknown error")
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
                    log.info("Buf LSP server started for ${file.name}")
                } catch (e: Exception) {
                    log.error("Failed to start Buf LSP server", e)
                    showLspErrorNotification(project, e.message ?: "Unknown error")
                }
            }
        }
    }

    private fun showLspUnavailableNotification(project: Project) {
        val versionInfo = BufVersionDetector.getVersionInfo(project)
        val message = if (versionInfo != null) {
            "Buf Language Server requires buf v1.59.0+. Current version: ${versionInfo.version}. " +
                "Falling back to CLI-based diagnostics."
        } else {
            "Buf CLI not found or version could not be detected. " +
                "Falling back to CLI-based diagnostics."
        }

        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup(BufBundle.getMessage("name"))
                    .createNotification(
                        "Buf Language Server Unavailable",
                        message,
                        NotificationType.WARNING,
                    )
                    .addAction(
                        object : com.intellij.notification.NotificationAction("Open Settings") {
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
                    .getNotificationGroup(BufBundle.getMessage("name"))
                    .createNotification(
                        "Buf Language Server Error",
                        "Failed to start Buf Language Server: $errorMessage. " +
                            "Using CLI-based diagnostics instead.",
                        NotificationType.ERROR,
                    )
                    .addAction(
                        object : com.intellij.notification.NotificationAction("Open Settings") {
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
}
