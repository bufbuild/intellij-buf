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

package build.buf.intellij.settings

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.project.Project
import java.io.File

object BufCLIUtils {
    private fun findBufExecutable() = PathEnvironmentVariableUtil.findExecutableInPathOnAnyOS("buf")

    fun isExecutionAllowed(project: Project): Boolean = TrustedProjects.isProjectTrusted(project)

    fun getConfiguredBufExecutable(project: Project): File? {
        if (!isExecutionAllowed(project)) return null
        val bufCLIPath = project.bufSettings.state.bufCLIPath
        return if (bufCLIPath.isNotEmpty()) File(bufCLIPath) else findBufExecutable()
    }
}
