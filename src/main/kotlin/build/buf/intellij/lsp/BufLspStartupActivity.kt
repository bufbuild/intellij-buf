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

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Project activity that eagerly detects buf CLI version to cache it.
 * This ensures LSP can start quickly when the first .proto file is opened.
 */
class BufLspStartupActivity : ProjectActivity {
    private val log = logger<BufLspStartupActivity>()

    override suspend fun execute(project: Project) {
        try {
            val versionInfo = BufVersionDetector.getVersionInfo(project, checkIfMissing = true)
            if (versionInfo != null) {
                log.info("Cached buf version ${versionInfo.version} on startup, LSP supported: ${versionInfo.supportsLsp}")
            } else {
                log.debug("Buf CLI not found during startup version check")
            }
        } catch (e: Exception) {
            log.warn("Failed to detect buf version on startup", e)
        }
    }
}
