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

package build.buf.intellij.resolve

import build.buf.intellij.module.ModuleKey
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase

data class BufModuleKeysUserData(val moduleKeys: Set<ModuleKey>) : UserDataHolderBase() {
    companion object {
        val KEY = Key.create<BufModuleKeysUserData>("BUF_ADDITIONAL_ROOTS")
    }
}