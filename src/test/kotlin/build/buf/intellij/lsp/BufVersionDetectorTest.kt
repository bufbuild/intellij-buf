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

package build.buf.intellij.lsp

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class BufVersionDetectorTest {

    @Test
    fun testVersionParsing() {
        val version = BufVersion.parse("1.43.0")
        assertThat(version).isNotNull()
        assertThat(version!!.major).isEqualTo(1)
        assertThat(version.minor).isEqualTo(43)
        assertThat(version.patch).isEqualTo(0)
    }

    @Test
    fun testVersionParsingWithV() {
        val version = BufVersion.parse("v1.43.0")
        assertThat(version).isNotNull()
        assertThat(version!!.major).isEqualTo(1)
    }

    @Test
    fun testVersionParsingInvalid() {
        val version = BufVersion.parse("invalid")
        assertThat(version).isNull()
    }

    @Test
    fun testVersionComparison_equal() {
        val v1 = BufVersion.parse("1.43.0")
        val v2 = BufVersion.parse("1.43.0")
        assertThat(v1).isEqualTo(v2)
    }

    @Test
    fun testVersionComparison_greaterMajor() {
        val v1 = BufVersion.parse("2.0.0")!!
        val v2 = BufVersion.parse("1.99.99")!!
        assertThat(v1 > v2).isTrue()
    }

    @Test
    fun testVersionComparison_greaterMinor() {
        val v1 = BufVersion.parse("1.44.0")!!
        val v2 = BufVersion.parse("1.43.99")!!
        assertThat(v1 > v2).isTrue()
    }

    @Test
    fun testVersionComparison_greaterPatch() {
        val v1 = BufVersion.parse("1.43.1")!!
        val v2 = BufVersion.parse("1.43.0")!!
        assertThat(v1 > v2).isTrue()
    }

    @Test
    fun testVersionComparison_lessThan() {
        val v1 = BufVersion.parse("1.42.0")!!
        val v2 = BufVersion.parse("1.43.0")!!
        assertThat(v1 < v2).isTrue()
    }

    @Test
    fun testLspSupport_belowMinimum() {
        val minBeta = BufVersion.parse("1.43.0")!!
        val tooOld = BufVersion.parse("1.42.0")!!
        assertThat(tooOld < minBeta).isTrue()
    }

    @Test
    fun testLspSupport_atMinimum() {
        val minBeta = BufVersion.parse("1.43.0")!!
        val atMinimum = BufVersion.parse("1.43.0")!!
        assertThat(atMinimum >= minBeta).isTrue()
    }

    @Test
    fun testLspSupport_aboveMinimum() {
        val minBeta = BufVersion.parse("1.43.0")!!
        val newer = BufVersion.parse("1.50.0")!!
        assertThat(newer >= minBeta).isTrue()
    }

    @Test
    fun testBetaCommand_at143() {
        // Version 1.43.0 should use beta command
        val version = BufVersion.parse("1.43.0")!!
        val minStable = BufVersion.parse("1.59.0")!!
        assertThat(version < minStable).isTrue()
    }

    @Test
    fun testBetaCommand_at158() {
        // Version 1.58.0 should use beta command
        val version = BufVersion.parse("1.58.0")!!
        val minStable = BufVersion.parse("1.59.0")!!
        assertThat(version < minStable).isTrue()
    }

    @Test
    fun testStableCommand_at159() {
        // Version 1.59.0 should use stable command
        val version = BufVersion.parse("1.59.0")!!
        val minStable = BufVersion.parse("1.59.0")!!
        assertThat(version >= minStable).isTrue()
    }

    @Test
    fun testStableCommand_above159() {
        // Version 2.0.0 should use stable command
        val version = BufVersion.parse("2.0.0")!!
        val minStable = BufVersion.parse("1.59.0")!!
        assertThat(version >= minStable).isTrue()
    }
}
