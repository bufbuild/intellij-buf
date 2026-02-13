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

package build.buf.intellij.settings

import com.intellij.openapi.project.Project

interface BufProjectSettingsService {
    data class State(
        var backgroundLintingEnabled: Boolean = true,
        var backgroundBreakingEnabled: Boolean = true,
        var breakingArgumentsOverride: List<String> = emptyList(),
        var useBufFormatter: Boolean = true,
        var bufCLIPath: String = "",
        var useLspServer: Boolean = true,
        var lspServerDebug: Boolean = false,
        var fallbackToCliDiagnostics: Boolean = true,
    )

    var state: State
}

val Project.bufSettings: BufProjectSettingsService
    get() = getService(BufProjectSettingsService::class.java)
        ?: error("Failed to get BufProjectSettingsService for $this")
