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

package build.buf.intellij.module.cache

import build.buf.intellij.module.ModuleKey
import java.nio.file.Path

/**
 * Abstraction around the Buf CLI module cache.
 * There have been three revisions so far (of the cache):
 *   - v1 (`${BUF_CACHE_DIR}/v1`) for CLI versions < v1.19.0.
 *   - v2 (`${BUF_CACHE_DIR}/v2`) for CLI versions v1.19.0 through v1.31.0.
 *   - v3 (`${BUF_CACHE_DIR}/v3`) for CLI versions after v1.32.0.beta-1.
 *
 *  Both `v1` and `v3` use simple layouts based on the paths of files in the module.
 *  The `v2` cache stores files as a content-addressable store and require extracting to another location for IntelliJ.
 */
interface ModuleCache {
    /**
     * Returns the root path to all modules stored in the module cache.
     * This is used to monitor changes to the underlying module cache.
     */
    fun moduleDataRoot(): Path

    /**
     * Returns the root path to the data for the module with the specified [ModuleKey].
     * This is a path under [moduleDataRoot] where cached files are found.
     */
    fun moduleDataPathForModuleKey(key: ModuleKey): Path
}
