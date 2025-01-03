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

package build.buf.intellij.model

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class BufIssue(
    val path: String,
    @SerializedName("start_line") val startLine: Int,
    @SerializedName("end_line") val endLine: Int,
    @SerializedName("start_column") val startColumn: Int,
    @SerializedName("end_column") val endColumn: Int,
    val type: String,
    val message: String,
) {
    val isCompileError: Boolean
        get() = type == "COMPILE"

    companion object {
        fun fromJSON(text: String): Result<BufIssue> = runCatching {
            Gson().fromJson(text, BufIssue::class.java)
        }
    }
}
