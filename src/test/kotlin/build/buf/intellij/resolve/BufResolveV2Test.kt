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

package build.buf.intellij.resolve

import build.buf.intellij.base.BufTestBase
import java.nio.file.Path

class BufResolveV2Test : BufTestBase() {

    override fun getEnv(): Map<String, String> {
        return mapOf(Pair("BUF_CACHE_DIR", getCachePath()))
    }

    override fun getBasePath(): String = "resolve"

    private fun getCachePath(): String {
        return Path.of(ClassLoader.getSystemResource("testData")!!.toURI()).resolve("manifest/cache").toFile().absolutePath
    }

    fun testExternalBufModuleFromV2Cache() {
        configureByFolder("external", "order.proto")
        val reference = myFixture.getReferenceAtCaretPositionWithAssertion("order.proto")
        assertNotNull(reference.resolve())
    }
}
