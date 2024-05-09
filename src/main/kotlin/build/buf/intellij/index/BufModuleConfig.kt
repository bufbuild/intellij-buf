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

import build.buf.intellij.module.ModuleFullName

/**
 * Configuration for an individual module loaded from `buf.yaml`.
 * For v1beta1/v1 files, there will be one of these for each config file.
 * For v2 files, there may be multiple modules defined in the same `buf.yaml` file.
 */
data class BufModuleConfig(
    /**
     * The URL to the `buf.yaml` file where the module is defined.
     */
    val bufYamlUrl: String,
    /**
     * The URL to the module sources. This will be relative to the directory where [bufYamlUrl] is found.
     */
    val pathUrl: String,
    /**
     * Optional full name of the module if defined in the `buf.yaml` file.
     */
    val moduleFullName: ModuleFullName? = null,
)
