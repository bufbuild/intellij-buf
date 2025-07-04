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

package build.buf.intellij.module.cache

import build.buf.intellij.module.ModuleKey
import build.buf.intellij.module.toDashless
import com.intellij.util.EnvironmentUtil
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Cache implementation for the v1 Buf CLI cache layout.
 * Modules are cached to `${BUF_CACHE_DIR}/v1/module/data/${registry}/${owner}/${name}/${commit}`.
 */
internal class ModuleCacheV1(env: Map<String, String> = EnvironmentUtil.getEnvironmentMap()) : BaseModuleCache(env) {
    override fun moduleDataRoot(): Path = Paths.get("$bufCacheDir/v1/module/data")

    override fun moduleDataPathForModuleKey(key: ModuleKey): Path = moduleDataRoot().resolve("${key.moduleFullName}/${key.commitID.toDashless()}")
}
