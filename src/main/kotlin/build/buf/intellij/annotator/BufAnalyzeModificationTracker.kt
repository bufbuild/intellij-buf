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

package build.buf.intellij.annotator

import build.buf.intellij.config.BufConfig
import build.buf.intellij.settings.BufProjectSettingsService
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.protobuf.lang.PbLanguage
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiModificationTracker

/**
 * Project level service to keep track of all changes to .proto files, Buf configuration files, and the plugin config.
 * This is used to avoid unnecessary calls to BufAnalyzePass (i.e. 'buf breaking' and 'buf lint').
 */
@Service(Service.Level.PROJECT)
class BufAnalyzeModificationTracker(private val project: Project) : ModificationTracker {
    private val protoModificationTracker = PsiModificationTracker.getInstance(project).forLanguage(PbLanguage.INSTANCE)

    override fun getModificationCount(): Long {
        var modificationCount : Long = protoModificationTracker.modificationCount
        runReadAction {
            for (configFile in BufConfig.CONFIG_FILES) {
                FilenameIndex.getVirtualFilesByName(configFile, GlobalSearchScope.projectScope(project)).forEach {
                    modificationCount += it.modificationCount
                }
            }
        }
        // In addition to .proto files and Buf CLI config files, also change the modification count on changes to the
        // plugin's state (which includes settings like arguments to 'buf breaking', whether to enable breaking/lint
        // checks, and the path to the Buf CLI).
        val settings = project.service<BufProjectSettingsService>()
        modificationCount += settings.state.hashCode()
        return modificationCount
    }
}