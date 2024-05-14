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

package build.buf.intellij.index

import build.buf.intellij.module.ModuleKey
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.ID

/**
 * Contains utility methods to retrieve data from `buf.yaml` and `buf.lock` indexes.
 */
object BufIndexes {
    internal val BUF_MODULE_CONFIG_INDEX_ID = ID.create<BufModuleConfig, Void>("BufModuleConfigIndex")
    internal val MODULE_KEY_INDEX_ID = ID.create<ModuleKey, Void>("BufModuleKeyIndex")

    /**
     * Returns all [BufModuleConfig] instances parsed from `buf.yaml` files in the [Project].
     */
    fun getProjectBufModuleConfigs(project: Project): Collection<BufModuleConfig> = getProjectIndexKeys(BUF_MODULE_CONFIG_INDEX_ID, project)

    /**
     * Returns all [ModuleKey] instances parsed from `buf.lock` files in the [Project].
     */
    fun getProjectModuleKeys(project: Project): Collection<ModuleKey> = getProjectIndexKeys(MODULE_KEY_INDEX_ID, project)

    private fun <K : Any> getProjectIndexKeys(indexId: ID<K, Void>, project: Project): Collection<K> {
        val scope = GlobalSearchScope.projectScope(project)
        return FileBasedIndex.getInstance().getAllKeys(indexId, project).filter {
            // NOTE: getAllKeys(..., project) isn't actually filtering out keys that only exist in the project.
            // Filter the results to ensure that we only return keys that were indexed from the project's files.
            FileBasedIndex.getInstance().getContainingFiles(indexId, it, scope).isNotEmpty()
        }
    }
}
