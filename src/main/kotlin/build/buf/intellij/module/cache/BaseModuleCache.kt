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

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.EnvironmentUtil
import com.intellij.util.SystemProperties

internal abstract class BaseModuleCache(env: Map<String, String> = EnvironmentUtil.getEnvironmentMap()) : ModuleCache {

    protected val bufCacheDir by lazy { getBufCacheDir(env) }

    /**
     * Looks up the location of the `${BUF_CACHE_DIR}`, using the same semantics as the Buf CLI.
     */
    private fun getBufCacheDir(env: Map<String, String>): String {
        val bufCacheDir = env["BUF_CACHE_DIR"]
        if (bufCacheDir != null) {
            return bufCacheDir
        }
        if (SystemInfoRt.isWindows) {
            val localAppData = env["LOCALAPPDATA"]
            if (localAppData != null) {
                return "$localAppData/buf"
            }
        }
        val xdgCacheHome = env["XDG_CACHE_HOME"]
        if (xdgCacheHome != null) {
            return "$xdgCacheHome/buf"
        }
        val home = env["HOME"]
        if (home != null) {
            return "$home/.cache/buf"
        }
        return "${SystemProperties.getUserHome()}/.cache/buf"
    }
}
