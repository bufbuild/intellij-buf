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

package build.buf.intellij.features

import com.intellij.openapi.fileTypes.FileTypeConsumer
import com.intellij.openapi.fileTypes.FileTypeFactory
import com.intellij.protobuf.lang.PbFileType

/**
 * Trick FileTypeFactoryExtractor into suggesting our plugin without a conflict
 *
 * @see <a href="https://github.com/JetBrains/intellij-plugin-verifier/blob/master/intellij-feature-extractor/src/main/java/com/jetbrains/intellij/feature/extractor/extractor/FileTypeFactoryExtractor.kt">FileTypeFactoryExtractor.kt</a>
 *
 */
class FakeFileTypeFactoryForDiscoverability : FileTypeFactory() {
    override fun createFileTypes(consumer: FileTypeConsumer) {
        // make sure it's not affecting production
        if (System.currentTimeMillis() > 0) {
            return
        }
        consumer.consume(PbFileType.INSTANCE, "proto")
    }
}
