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

import build.buf.intellij.lsp.BufLspServerSupportProvider
import build.buf.intellij.lsp.BufVersionDetector
import build.buf.intellij.resolve.BufModuleRoots
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.ide.trustedProjects.TrustedProjectsListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspServerManager

class BufTrustedProjectsListener : TrustedProjectsListener {
    override fun onProjectTrusted(project: Project) {
        if (project.isDisposed) return
        BufModuleRoots.updateUserRoots(project, initial = true, forceRootsChange = true)
        ApplicationManager.getApplication().executeOnPooledThread {
            if (project.isDisposed) return@executeOnPooledThread
            BufVersionDetector.getVersionInfo(project, checkIfMissing = true)
            ApplicationManager.getApplication().invokeLater(
                {
                    LspServerManager.getInstance(project)
                        .startServersIfNeeded(BufLspServerSupportProvider::class.java)
                    DaemonCodeAnalyzer.getInstance(project).restart("Buf: project trusted")
                },
                project.disposed,
            )
        }
    }
}
