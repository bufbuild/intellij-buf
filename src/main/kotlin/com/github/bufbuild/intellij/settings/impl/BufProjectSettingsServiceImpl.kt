package com.github.bufbuild.intellij.settings.impl;

import com.github.bufbuild.intellij.settings.BufProjectSettingsService
import com.github.bufbuild.intellij.settings.BufProjectSettingsService.State
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.configurationStore.serializeObjectInto
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element

private const val serviceName: String = "BufProjectSettings"

@com.intellij.openapi.components.State(
    name = serviceName, storages = [
        Storage(StoragePathMacros.WORKSPACE_FILE),
        Storage("misc.xml", deprecated = true)
    ]
)
class BufProjectSettingsServiceImpl(
    private val project: Project
) : PersistentStateComponent<Element>, BufProjectSettingsService {
    @Volatile
    private var _state: State = State()

    override var state: State
        get() = _state.copy()
        set(newState) {
            if (_state != newState) {
                _state = newState.copy()
                DaemonCodeAnalyzer.getInstance(project).restart()
            }
        }

    override fun getState(): Element {
        val element = Element(serviceName)
        serializeObjectInto(_state, element)
        return element
    }

    override fun loadState(state: Element) {
        val rawState = state.clone()
        XmlSerializer.deserializeInto(_state, rawState)
    }
}
