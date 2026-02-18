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

package build.buf.intellij.bundle

import build.buf.intellij.BufBundle
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File
import java.io.InputStreamReader
import java.util.Properties

class BufBundleTest {
    @Test
    fun testBundleIsValidAndComplete() {
        val properties = loadBundleProperties()

        // Bundle should be loadable and non-empty
        assertThat(properties).isNotEmpty

        // All values should be non-empty
        properties.forEach { (key, value) ->
            assertThat(value.toString())
                .`as`("Key '$key' should have a non-empty value")
                .isNotBlank()
        }

        // All keys should follow naming conventions
        properties.keys.forEach { key ->
            val keyStr = key.toString()
            assertThat(keyStr)
                .`as`("Key '$keyStr' should only contain lowercase letters, dots, and underscores")
                .matches("[a-z._]+")
                .doesNotStartWith(".")
                .doesNotEndWith(".")
                .doesNotContain("..")
        }
    }

    @Test
    fun testAllCodeReferencesHaveCorrespondingKeys() {
        val properties = loadBundleProperties()
        val usedKeys = findBundleKeysInCode()

        // Verify all keys referenced in code exist in the properties file
        val missingKeys = usedKeys - properties.keys.map { it.toString() }.toSet()
        assertThat(missingKeys)
            .`as`("The following keys are referenced in code but missing from BufBundle.properties: $missingKeys")
            .isEmpty()
    }

    @Test
    fun testNoUnusedKeys() {
        val properties = loadBundleProperties()
        val definedKeys = properties.keys.map { it.toString() }.toSet()
        val usedKeys = findBundleKeysInCode()

        // Keys also used in plugin.xml
        val pluginXmlKeys = findBundleKeysInPluginXml()

        val allUsedKeys = usedKeys + pluginXmlKeys

        // Find keys that are defined but not used anywhere
        val unusedKeys = definedKeys - allUsedKeys

        assertThat(unusedKeys)
            .`as`("The following keys are defined in BufBundle.properties but not used: $unusedKeys")
            .isEmpty()
    }

    @Test
    fun testBundleMethodsWork() {
        // Test that BufBundle.message() works correctly
        val name = BufBundle.message("name")
        assertThat(name).isEqualTo("Buf")

        // Test parameterized message
        val syntacticText = BufBundle.message("syntactic.library.text", "test-module")
        assertThat(syntacticText).contains("test-module")
    }

    private fun loadBundleProperties(): Properties {
        val properties = Properties()
        val bundleStream = javaClass.classLoader.getResourceAsStream("messages/BufBundle.properties")
            ?: throw IllegalStateException("BufBundle.properties not found")
        InputStreamReader(bundleStream, Charsets.UTF_8).use { reader ->
            properties.load(reader)
        }
        return properties
    }

    private fun findBundleKeysInCode(): Set<String> {
        val srcDir = File("src/main/kotlin")
        if (!srcDir.exists()) {
            throw IllegalStateException("Source directory not found: ${srcDir.absolutePath}")
        }

        val keys = mutableSetOf<String>()

        // Pattern to match BufBundle.message("key")
        val messagePattern = Regex("""BufBundle\.message\("([^"]+)"""")

        srcDir.walk()
            .filter { it.extension == "kt" }
            .forEach { file ->
                val content = file.readText()
                messagePattern.findAll(content).forEach { match ->
                    keys.add(match.groupValues[1])
                }
            }

        return keys
    }

    private fun findBundleKeysInPluginXml(): Set<String> {
        val pluginXml = File("src/main/resources/META-INF/plugin.xml")
        if (!pluginXml.exists()) {
            return emptySet()
        }

        val keys = mutableSetOf<String>()
        val content = pluginXml.readText()

        // Pattern to match bundle="messages.BufBundle" key="some.key"
        val keyPattern = Regex("""bundle="messages\.BufBundle"\s+key="([^"]+)"""")
        keyPattern.findAll(content).forEach { match ->
            keys.add(match.groupValues[1])
        }

        return keys
    }
}
