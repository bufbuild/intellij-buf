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

    /**
     * Note: These tests use reflection to test private methods since they
     * contain the core version parsing logic that needs to be tested in isolation.
     */

    @Test
    fun testVersionParsingWithV() {
        val result = testIsVersionSupported("v1.40.0")
        assertThat(result).isTrue()
    }

    @Test
    fun testVersionParsingWithoutV() {
        val result = testIsVersionSupported("1.40.0")
        assertThat(result).isTrue()
    }

    @Test
    fun testVersionBelowMinimum() {
        val result = testIsVersionSupported("1.39.0")
        assertThat(result).isFalse()
    }

    @Test
    fun testVersionAboveMinimum() {
        val result = testIsVersionSupported("1.41.0")
        assertThat(result).isTrue()
    }

    @Test
    fun testVersionEqualToMinimum() {
        val result = testIsVersionSupported("1.40.0")
        assertThat(result).isTrue()
    }

    @Test
    fun testMajorVersionAboveMinimum() {
        val result = testIsVersionSupported("2.0.0")
        assertThat(result).isTrue()
    }

    @Test
    fun testMajorVersionBelowMinimum() {
        val result = testIsVersionSupported("0.99.99")
        assertThat(result).isFalse()
    }

    @Test
    fun testMinorVersionEdgeCase() {
        val result = testIsVersionSupported("1.40.1")
        assertThat(result).isTrue()
    }

    @Test
    fun testInvalidVersionFormat() {
        val result = testIsVersionSupported("invalid")
        assertThat(result).isFalse()
    }

    @Test
    fun testVersionWithExtraText() {
        val result = testIsVersionSupported("buf version 1.40.0")
        assertThat(result).isTrue()
    }

    @Test
    fun testVersionParsing() {
        val version = BufVersion.parse("1.40.0")
        assertThat(version).isNotNull()
        assertThat(version!!.major).isEqualTo(1)
        assertThat(version.minor).isEqualTo(40)
        assertThat(version.patch).isEqualTo(0)
    }

    @Test
    fun testVersionParsingWithV() {
        val version = BufVersion.parse("v1.40.0")
        assertThat(version).isNotNull()
        assertThat(version!!.major).isEqualTo(1)
    }

    @Test
    fun testVersionComparison_equal() {
        val v1 = BufVersion.parse("1.40.0")
        val v2 = BufVersion.parse("1.40.0")
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
        val v1 = BufVersion.parse("1.41.0")!!
        val v2 = BufVersion.parse("1.40.99")!!
        assertThat(v1 > v2).isTrue()
    }

    @Test
    fun testVersionComparison_greaterPatch() {
        val v1 = BufVersion.parse("1.40.1")!!
        val v2 = BufVersion.parse("1.40.0")!!
        assertThat(v1 > v2).isTrue()
    }

    @Test
    fun testVersionComparison_lessThan() {
        val v1 = BufVersion.parse("1.39.0")!!
        val v2 = BufVersion.parse("1.40.0")!!
        assertThat(v1 < v2).isTrue()
    }

    /**
     * Helper method to test the private isVersionSupported method using reflection.
     */
    private fun testIsVersionSupported(versionOutput: String): Boolean {
        val method = BufVersionDetector::class.java.getDeclaredMethod(
            "isVersionSupported",
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(BufVersionDetector, versionOutput) as Boolean
    }
}
