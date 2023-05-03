package build.buf.intellij.settings

import com.intellij.openapi.project.Project

interface BufProjectSettingsService {
    data class State(
        var backgroundLintingEnabled: Boolean = true,
        var backgroundBreakingEnabled: Boolean = true,
        var breakingArgumentsOverride: List<String> = emptyList(),
        var useBufFormatter: Boolean = true,
    )

    var state: State
}

val Project.bufSettings: BufProjectSettingsService
    get() = getService(BufProjectSettingsService::class.java)
        ?: error("Failed to get RustProjectSettingsService for $this")
