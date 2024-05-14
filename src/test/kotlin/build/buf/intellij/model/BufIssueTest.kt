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

package build.buf.intellij.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class BufIssueTest {
    @Test
    fun testJSON() {
        val issue = BufIssue.fromJSON(
            """
            {
                "start_line": 1,
                "end_line": 2,
                "start_column": 3,
                "end_column": 4,
                "type": "some type",
                "message": "some message"
            }
            """.trimIndent(),
        ).getOrNull()
        assertNotNull(issue!!, "failed to deserialize issue from JSON")
        assertEquals(1, issue.startLine)
        assertEquals(2, issue.endLine)
        assertEquals(3, issue.startColumn)
        assertEquals(4, issue.endColumn)
        assertEquals("some type", issue.type)
        assertEquals("some message", issue.message)
    }
}
