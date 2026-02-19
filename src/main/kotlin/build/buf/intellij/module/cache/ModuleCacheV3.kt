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

package build.buf.intellij.module.cache

import build.buf.intellij.module.ModuleDigestType
import build.buf.intellij.module.ModuleKey
import build.buf.intellij.module.toDashless
import com.intellij.util.EnvironmentUtil
import java.nio.file.Path

/**
 * Cache implementation for the v3 Buf CLI cache layout.
 * Modules are cached to `${BUF_CACHE_DIR}/v3/modules/${digestType}/${registry}/${owner}/${name}/${commit}/files`.
 */
internal class ModuleCacheV3(env: Map<String, String> = EnvironmentUtil.getEnvironmentMap()) : BaseModuleCache(env) {
    override fun moduleDataRoot(): Path = Path.of("$bufCacheDir/v3/modules")

    override fun moduleDataPathForModuleKey(key: ModuleKey): Path {
        val digestType = key.digest?.digestType ?: ModuleDigestType.B5
        return moduleDataRoot().resolve("$digestType/${key.moduleFullName}/${key.commitID.toDashless()}/files")
    }
}
