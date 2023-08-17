// Copyright 2022-2023 Buf Technologies, Inc.
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

package build.buf.intellij.config

/**
 * Constants for the Buf CLI configuration files.
 */
object BufConfig {
    /** The lock file for a module's dependencies */
    val BUF_LOCK = "buf.lock"
    /** The buf.yaml configuration file for a Buf module */
    val BUF_YAML = "buf.yaml"
    /** The workspace configuration file */
    val BUF_WORK_YAML = "buf.work.yaml"

    /** All configuration files used by the Buf CLI */
    val CONFIG_FILES = setOf(BUF_LOCK, BUF_YAML, BUF_WORK_YAML)
}
