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

package build.buf.intellij.module

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ModuleFullNameTest {
    @Test
    fun testModuleFullName() {
        val fullName = ModuleFullName.parse("buf.build/bufbuild/buf").getOrThrow()
        Assertions.assertEquals("buf.build", fullName.registry)
        Assertions.assertEquals("bufbuild", fullName.owner)
        Assertions.assertEquals("buf", fullName.name)
        Assertions.assertEquals(fullName, ModuleFullName.parse(fullName.toString()).getOrThrow())

        val invalidFullNames = listOf(
            "https://buf.build/bufbuild/buf",
            "username@buf.build/bufbuild/buf",
            "buf.build/bufbuild",
            "buf.build/ /",
            "buf.build// ",
            "buf.build/ / ",
        )
        for (invalidFullName in invalidFullNames) {
            assertThrows<IllegalArgumentException> { ModuleFullName.parse(invalidFullName).getOrThrow() }
        }
    }
}
